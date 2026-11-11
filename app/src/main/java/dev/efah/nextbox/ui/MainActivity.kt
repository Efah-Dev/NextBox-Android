package dev.efah.nextbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.efah.nextbox.App
import dev.efah.nextbox.bg.Box as Core
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.BuildConfig
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.Updater
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import dev.efah.nextbox.ui.screens.ActivityScreen
import dev.efah.nextbox.ui.screens.HomeScreen
import dev.efah.nextbox.ui.screens.NodeEditorScreen
import dev.efah.nextbox.ui.screens.NodesScreen
import dev.efah.nextbox.ui.screens.PerAppScreen
import dev.efah.nextbox.ui.screens.RulesScreen
import dev.efah.nextbox.ui.screens.ScanScreen
import dev.efah.nextbox.ui.screens.SettingsScreen
import dev.efah.nextbox.ui.screens.UsageScreen
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NextBoxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("首页", NbIcons.Home), // i18n:skip (translated where shown)
    Nodes("节点", NbIcons.Globe), // i18n:skip (translated where shown)
    Usage("用量", NbIcons.Chart), // i18n:skip (translated where shown)
    Activity("活动", NbIcons.Activity), // i18n:skip (translated where shown)
    Settings("设置", NbIcons.Settings), // i18n:skip (translated where shown)
}

private sealed interface Overlay {
    data class Editor(val id: Long) : Overlay
    data object PerApp : Overlay
    data object Rules : Overlay
    data object Scan : Overlay
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
    val activity = LocalContext.current as ComponentActivity
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var notifyGuide by remember { mutableStateOf(false) }
    var externalImport by remember { mutableStateOf<String?>(null) }
    val toast: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    // Glass effects: the setting, and off while the system saves power.
    val glassPref by Prefs.glass.collectAsState()
    var powerSave by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        powerSave = activity.getSystemService(PowerManager::class.java).isPowerSaveMode
        onPauseOrDispose {}
    }
    val glassOn = glassPref && !powerSave && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) Core.start(activity) else toast(T("需要允许 VPN 连接才能开启 VPN 模式"))
    }

    fun connectNow() {
        val st = Repo.state.value
        if (st.selectedId == 0L && st.settings.routeMode != "direct") {
            toast(T("请先选择一个节点"))
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

    fun import(text: String) {
        scope.launch {
            Repo.importText(text).onSuccess { toast(it); tab = Tab.Nodes }.onFailure { toast(it.message ?: T("导入失败")) }
        }
    }

    fun importClipboard() {
        val text = App.clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
        if (text.isBlank()) toast(T("剪贴板是空的")) else import(text)
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
    LaunchedEffect(error) { error?.let { toast(T("连接失败：") + it) } }

    BackHandler(enabled = overlay != null || tab != Tab.Home) {
        if (overlay != null) overlay = null else tab = Tab.Home
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barSpace = navInset + 64.dp + 22.dp

    CompositionLocalProvider(LocalToast provides toast, LocalGlass provides glassOn) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
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
                if (ov != null) {
                    Box(Modifier.fillMaxSize()) {
                        GlowBackground(strength = 0.6f)
                        CompositionLocalProvider(LocalBottomInset provides navInset) {
                            when (ov) {
                                is Overlay.Editor -> NodeEditorScreen(ov.id) { overlay = null }
                                Overlay.PerApp -> PerAppScreen { overlay = null }
                                Overlay.Rules -> RulesScreen { overlay = null }
                                Overlay.Scan -> ScanScreen(
                                    onResult = { text -> overlay = null; import(text) },
                                    onClose = { overlay = null },
                                )
                            }
                        }
                    }
                } else {
                    val backdrop = rememberBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                            GlowBackground()
                            CompositionLocalProvider(LocalBottomInset provides barSpace) {
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
                                        Tab.Nodes -> NodesScreen(
                                            onEdit = { overlay = Overlay.Editor(it) },
                                            onImportClipboard = ::importClipboard,
                                            onScan = { overlay = Overlay.Scan },
                                        )
                                        Tab.Usage -> UsageScreen()
                                        Tab.Activity -> ActivityScreen()
                                        Tab.Settings -> SettingsScreen(
                                            onPerApp = { overlay = Overlay.PerApp },
                                            onRules = { overlay = Overlay.Rules },
                                        )
                                    }
                                }
                            }
                        }
                        GlassNavBar(
                            tab, backdrop, onSelect = { tab = it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp, bottom = navInset + 10.dp),
                        )
                    }
                }
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).padding(bottom = if (overlay == null) barSpace else navInset),
            )
        }

        externalImport?.takeIf { it.isNotBlank() }?.let { text ->
            val state by Repo.state.collectAsState()
            val group = state.groups.firstOrNull { it.id == state.currentGroupId }?.displayName.orEmpty()
            NbAlertDialog(
                onDismissRequest = { externalImport = null },
                title = { Text(T("导入分享链接")) },
                text = {
                    Text(T("要把收到的内容导入到分组「%s」吗？", group) + "\n\n" + text.take(120) + if (text.length > 120) "…" else "")
                },
                confirmButton = {
                    TextButton(onClick = {
                        externalImport = null
                        import(text)
                    }) { Text(T("导入")) }
                },
                dismissButton = { TextButton(onClick = { externalImport = null }) { Text(T("取消")) } },
            )
        }

        val update by Updater.prompt.collectAsState()
        update?.let { r ->
            NbAlertDialog(
                onDismissRequest = { Updater.close(later = false) },
                title = { Text(T("发现新版本 %s", r.version)) },
                text = {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        Text(T("当前版本 %s", BuildConfig.VERSION_NAME), color = c.muted)
                        if (r.notes.isNotEmpty()) Text(r.notes, modifier = Modifier.padding(top = 10.dp))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        Updater.close(later = false)
                        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(r.apk))) }
                            .onFailure { toast(T("没有可以打开链接的应用")) }
                    }) { Text(T("下载")) }
                },
                dismissButton = { TextButton(onClick = { Updater.close(later = true) }) { Text(T("以后再说")) } },
            )
        }

        if (notifyGuide) {
            NbAlertDialog(
                onDismissRequest = {},
                title = { Text(T("在通知栏查看网速")) },
                text = { Text(T("允许 NextBox 发送通知后，连接时通知栏会常驻显示实时网速和今日、本月的流量，还可以在通知里直接断开或切换节点。")) },
                confirmButton = {
                    TextButton(onClick = {
                        notifyGuide = false
                        Prefs.markShown("notify_guide")
                        notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text(T("允许通知")) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        notifyGuide = false
                        Prefs.markShown("notify_guide")
                        connectNow()
                    }) { Text(T("以后再说")) }
                },
            )
        }
    }
}

/** The floating glass navigation bar with a highlight that slides to the selected tab. */
@Composable
private fun GlassNavBar(tab: Tab, backdrop: Backdrop, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Nb.c
    val shape = RoundedCornerShape(32.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .glass(backdrop, radius = 32.dp),
    ) {
        val w = maxWidth / Tab.entries.size
        val x by animateDpAsState(w * tab.ordinal, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "nav")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .padding(5.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(c.accentSoft.copy(alpha = if (c.dark) 0.7f else 0.8f)),
        )
        Row(Modifier.fillMaxSize()) {
            Tab.entries.forEach { t ->
                val sel = t == tab
                val color by animateColorAsState(if (sel) c.accentInk else c.muted, label = "navc")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(27.dp))
                        .selectable(
                            selected = sel, role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, null, tint = color, modifier = Modifier.size(22.dp))
                    Text(
                        T(t.label), color = if (sel) c.text else c.muted, fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
