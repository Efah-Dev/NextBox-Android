package dev.efah.nextbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.efah.nextbox.App
import dev.efah.nextbox.bg.Box as Core
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.screens.ActivityScreen
import dev.efah.nextbox.ui.screens.HomeScreen
import dev.efah.nextbox.ui.screens.NodeEditorScreen
import dev.efah.nextbox.ui.screens.NodesScreen
import dev.efah.nextbox.ui.screens.PerAppScreen
import dev.efah.nextbox.ui.screens.SettingsScreen
import dev.efah.nextbox.ui.screens.UsageScreen
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NextBoxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("首页", NbIcons.Home),
    Nodes("节点", NbIcons.Globe),
    Usage("用量", NbIcons.Chart),
    Activity("活动", NbIcons.Activity),
    Settings("设置", NbIcons.Settings),
}

private sealed interface Overlay {
    data class Editor(val id: Long) : Overlay
    data object PerApp : Overlay
}

class MainActivity : ComponentActivity() {
    /** Intents that arrive while the UI is running (share links, notification buttons). */
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingIntent.value = intent
        setContent {
            val state by Repo.state.collectAsState()
            NextBoxTheme(state.settings.theme, state.settings.accentColor) {
                NextBoxApp(pendingIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_CONNECT = "connect"
    }
}

@Composable
private fun NextBoxApp(intents: MutableStateFlow<Intent?>) {
    val c = Nb.c
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var notifyGuide by remember { mutableStateOf(false) }
    var externalImport by remember { mutableStateOf<String?>(null) }
    val toast: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) Core.start(activity) else toast("需要允许 VPN 连接才能开启 VPN 模式")
    }

    fun connectNow() {
        val st = Repo.state.value
        if (st.selectedId == 0L && st.settings.routeMode != "direct") {
            toast("请先选择一个节点")
            tab = Tab.Nodes
            return
        }
        if (st.settings.tunEnabled) {
            val prepare = VpnService.prepare(activity)
            if (prepare != null) {
                vpnLauncher.launch(prepare)
                return
            }
        }
        Core.start(activity)
    }

    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> connectNow() }

    fun connect() {
        if (Core.status.value != dev.efah.nextbox.bg.Status.Stopped) {
            Core.stop()
            return
        }
        // first connection on Android 13+: explain why the notification matters
        val needsNotify = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotify && !Prefs.shown("notify_guide")) {
            notifyGuide = true
            return
        }
        connectNow()
    }

    fun importClipboard() {
        val text = App.clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
        if (text.isBlank()) {
            toast("剪贴板是空的")
            return
        }
        scope.launch {
            Repo.importText(text).onSuccess { toast(it); tab = Tab.Nodes }.onFailure { toast(it.message ?: "导入失败") }
        }
    }

    LaunchedEffect(Unit) {
        intents.collect { intent ->
            if (intent == null) return@collect
            intents.value = null
            // links from other apps or web pages are only imported after the user confirms
            when {
                intent.action == Intent.ACTION_VIEW && intent.data != null -> externalImport = intent.data.toString()
                intent.action == Intent.ACTION_SEND -> externalImport = intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            when (intent.getStringExtra(MainActivity.EXTRA_TAB)) {
                "nodes" -> { overlay = null; tab = Tab.Nodes }
            }
            if (intent.getBooleanExtra(MainActivity.EXTRA_CONNECT, false)) connect()
        }
    }

    val error by Core.error.collectAsState()
    LaunchedEffect(error) { error?.let { toast("连接失败：$it") } }

    BackHandler(enabled = overlay != null || tab != Tab.Home) {
        if (overlay != null) overlay = null else tab = Tab.Home
    }

    CompositionLocalProvider(LocalToast provides toast) {
        AnimatedContent(
            targetState = overlay,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith fadeOut(tween(150))
                } else {
                    fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "overlay",
        ) { ov ->
            when (ov) {
                is Overlay.Editor -> Scaffold(containerColor = c.bg, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
                    Box(Modifier.padding(bottom = pad.calculateBottomPadding())) { NodeEditorScreen(ov.id) { overlay = null } }
                }
                Overlay.PerApp -> Scaffold(containerColor = c.bg, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
                    Box(Modifier.padding(bottom = pad.calculateBottomPadding())) { PerAppScreen { overlay = null } }
                }
                null -> Scaffold(
                    containerColor = c.bg,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar(containerColor = c.card, tonalElevation = 0.dp) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, null, modifier = Modifier.size(22.dp)) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = c.accentInk,
                                        selectedTextColor = c.text,
                                        indicatorColor = c.accentSoft,
                                        unselectedIconColor = c.muted,
                                        unselectedTextColor = c.muted,
                                    ),
                                )
                            }
                        }
                    },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { dir * it / 12 }) togetherWith fadeOut(tween(120))
                            },
                            label = "tab",
                        ) { t ->
                            when (t) {
                                Tab.Home -> HomeScreen(
                                    onConnect = ::connect, onOpenNodes = { tab = Tab.Nodes },
                                    onImport = ::importClipboard, onPickApps = { overlay = Overlay.PerApp },
                                )
                                Tab.Nodes -> NodesScreen(onEdit = { overlay = Overlay.Editor(it) }, onImportClipboard = ::importClipboard)
                                Tab.Usage -> UsageScreen()
                                Tab.Activity -> ActivityScreen()
                                Tab.Settings -> SettingsScreen(onPerApp = { overlay = Overlay.PerApp })
                            }
                        }
                    }
                }
            }
        }
    }

    externalImport?.takeIf { it.isNotBlank() }?.let { text ->
        val state by Repo.state.collectAsState()
        val group = state.groups.firstOrNull { it.id == state.currentGroupId }?.displayName.orEmpty()
        AlertDialog(
            onDismissRequest = { externalImport = null },
            title = { Text("导入分享链接") },
            text = {
                Text("要把收到的内容导入到分组「$group」吗？\n\n" + text.take(120) + if (text.length > 120) "…" else "")
            },
            confirmButton = {
                TextButton(onClick = {
                    externalImport = null
                    scope.launch {
                        Repo.importText(text).onSuccess { toast(it); tab = Tab.Nodes }.onFailure { toast(it.message ?: "导入失败") }
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { externalImport = null }) { Text("取消") } },
        )
    }

    if (notifyGuide) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("在通知栏查看网速") },
            text = { Text("允许 NextBox 发送通知后，连接时通知栏会常驻显示实时网速和今日、本月的流量，还可以在通知里直接断开或切换节点。") },
            confirmButton = {
                TextButton(onClick = {
                    notifyGuide = false
                    Prefs.markShown("notify_guide")
                    notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("允许通知") }
            },
            dismissButton = {
                TextButton(onClick = {
                    notifyGuide = false
                    Prefs.markShown("notify_guide")
                    connectNow()
                }) { Text("以后再说") }
            },
        )
    }
}
