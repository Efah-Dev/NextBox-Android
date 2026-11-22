package dev.efah.nextbox.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.Lang
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbField
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.SectionTitle
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.TopBar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Settings sub-pages, opened with SettingsScreen's onOpen. */
object SettingsPages {
    const val AUTO_SELECT = "autoselect"
    const val TEST = "test"
    const val NOTIFY = "notify"
    const val ADVANCED = "advanced"
    const val ABOUT = "about"
    const val BACKUP = "backup"
}

/**
 * The settings, grouped by task: connection, nodes, notification and
 * appearance, data and the rest. Rarely changed things live on sub-pages.
 */
@Composable
fun SettingsScreen(onPerApp: () -> Unit, onRules: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val state by Repo.state.collectAsState()
    val s = state.settings
    val routeScope by Prefs.routeScope.collectAsState()
    val perApp by Prefs.perAppList.collectAsState()
    val appMode by Prefs.perAppMode.collectAsState()
    val autoConnect by Prefs.autoConnect.collectAsState()
    val autoSelect by Prefs.autoSelect.collectAsState()
    val subAuto by Prefs.subAutoHours.collectAsState()
    val testMode by Prefs.latencyMode.collectAsState()
    val notifySpeed by Prefs.notifySpeed.collectAsState()
    val notifyStats by Prefs.notifyStats.collectAsState()
    val glass by Prefs.glass.collectAsState()
    val language by Prefs.language.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }

    fun patch(reload: Boolean = true, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        scope.launch { Repo.updateSettings(buildJsonObject(block), reload).onFailure { toast.error(it.message ?: T("保存失败")) } }
    }

    GlassScreen(header = { TopBar(T("设置")) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconGroup(T("连接")) {
                IconNav(NbIcons.Shield, T("连接方式"), null, if (s.tunEnabled) T("VPN 模式") else T("仅代理")) { dialog = "mode" }
                IconSep()
                IconSwitch(NbIcons.Power, T("打开 App 时自动连接"), T("只在你自己打开 App 时"), autoConnect, { Prefs.setAutoConnect(it) })
                IconSep()
                IconNav(NbIcons.Swap, T("自动选择节点"), T("当前节点连不上时换一个能用的"), if (autoSelect) T("已开启") else T("关闭")) { onOpen(SettingsPages.AUTO_SELECT) }
                IconSep()
                val ruleCount = RuleUi.count(s.rules) { true }
                IconNav(
                    NbIcons.Route, T("分流规则"), T("路由模式、路由路径、规则集、分组代理"),
                    listOfNotNull(
                        when (s.routeMode) { "rule" -> T("规则分流"); "direct" -> T("全部直连"); else -> T("全局代理") },
                        if (s.routeMode == "rule" && ruleCount > 0) T("%d 条", ruleCount) else null,
                    ).joinToString(" · "),
                    onClick = onRules,
                )
                IconSep()
                IconNav(
                    NbIcons.Apps, T("代理范围"), T("哪些应用使用 VPN"),
                    when {
                        routeScope != Prefs.SCOPE_APPS -> T("所有应用")
                        appMode == Prefs.APPS_EXCLUDE -> T("排除 %d 个", perApp.size)
                        else -> T("代理 %d 个", perApp.size)
                    },
                    onClick = onPerApp,
                )
            }

            IconGroup(T("节点与订阅")) {
                IconNav(NbIcons.Refresh, T("自动更新订阅"), null, autoLabel(subAuto)) { dialog = "subAuto" }
                IconSep()
                IconNav(
                    NbIcons.Bolt, T("测速"), T("方式、测试地址、超时、并发"),
                    (if (testMode == Prefs.TEST_TCP) "TCP" else "URL") + " · " + T("%d 秒", s.testTimeout / 1000),
                ) { onOpen(SettingsPages.TEST) }
            }

            IconGroup(T("通知与外观")) {
                IconNav(
                    NbIcons.Bell, T("通知栏"), T("实时网速、流量统计、结算日"),
                    if (notifySpeed || notifyStats) T("已开启") else T("关闭"),
                ) { onOpen(SettingsPages.NOTIFY) }
                IconSep()
                IconNav(NbIcons.Palette, T("主题"), null, themeLabel(s.theme)) { dialog = "theme" }
                IconSep()
                IconSwitch(
                    NbIcons.Sparkle, T("玻璃效果"), T("省电模式下自动关闭"), glass, { Prefs.setGlass(it) },
                    enabled = android.os.Build.VERSION.SDK_INT >= 31,
                )
                IconSep()
                IconNav(NbIcons.Globe, T("语言"), null, languages.firstOrNull { it.first == language }?.second?.let { if (language == "auto") T(it) else it }.orEmpty()) { dialog = "language" }
            }

            IconGroup(T("数据与其他")) {
                IconNav(NbIcons.Archive, T("备份与恢复"), T("可以和 Windows 版互相导入")) { onOpen(SettingsPages.BACKUP) }
                IconSep()
                IconNav(NbIcons.Settings, T("高级"), T("DNS、端口、局域网、IPv6、FakeIP")) { onOpen(SettingsPages.ADVANCED) }
                IconSep()
                IconNav(NbIcons.Info, T("关于 NextBox"), T("更新、官网、GitHub"), dev.efah.nextbox.BuildConfig.VERSION_NAME) { onOpen(SettingsPages.ABOUT) }
            }
        }
    }

    when (dialog) {
        "mode" -> ChoiceDialog(
            T("连接方式"), listOf(true to T("VPN 模式"), false to T("仅代理")), s.tunEnabled, { dialog = null },
            descs = listOf(T("所有应用经过 NextBox，按规则分流"), T("只开本地代理端口，由应用自己设置")),
        ) { v ->
            dialog = null
            patch { put("tunEnabled", v) }
            if (!v) toast(T("只开启本地代理端口 %d，需要应用自己设置代理", s.mixedPort))
        }
        "subAuto" -> ChoiceDialog(T("自动更新订阅"), autoChoices.map { it to autoLabel(it) }, subAuto, { dialog = null }) { v ->
            dialog = null
            Prefs.setSubAutoHours(v)
        }
        "language" -> ChoiceDialog(T("语言"), languages.map { (k, v) -> k to if (k == "auto") T(v) else v }, language, { dialog = null }) { v ->
            dialog = null
            Prefs.setLanguage(v)
            Lang.apply(v)
        }
        "theme" -> ThemeSheet(s.theme, s.accentColor, s.recentColors, { dialog = null }) { theme, accent, recent ->
            patch(reload = false) {
                put("theme", theme)
                put("accentColor", accent)
                put("recentColors", kotlinx.serialization.json.JsonArray(recent.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
        }
    }
}

// ---------------------------------------------------------------- rows

@Composable
fun IconGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title)
        NbCard(padding = PaddingValues(0.dp)) { content() }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    val c = Nb.c
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.accentInk, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun IconNav(icon: ImageVector, label: String, sub: String? = null, value: String = "", onClick: () -> Unit) {
    val c = Nb.c
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowIcon(icon)
            Column(Modifier.weight(1f)) {
                Text(label, style = NbType.body, color = c.text)
                if (!sub.isNullOrEmpty()) Text(sub, style = NbType.small, color = c.muted)
            }
            if (value.isNotEmpty()) {
                Text(value, style = NbType.small.copy(fontSize = 13.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
            }
            Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun IconSwitch(icon: ImageVector, label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Nb.c
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowIcon(icon)
            Column(Modifier.weight(1f)) {
                Text(label, style = NbType.body, color = if (enabled) c.text else c.muted)
                if (!sub.isNullOrEmpty()) Text(sub, style = NbType.small, color = c.muted)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = c.accentStrong))
        }
    }
}

/** The hairline between two rows of an IconGroup. */
@Composable
fun IconSep() {
    HorizontalDivider(color = Nb.c.line.copy(alpha = 0.7f), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
}

// ---------------------------------------------------------------- dialogs

private fun themeLabel(theme: String) = when (theme) {
    "light" -> T("浅色")
    "dark" -> T("深色")
    else -> T("跟随系统")
}

@Composable
fun TextDialog(
    title: String,
    initial: String,
    hint: String,
    onDismiss: () -> Unit,
    number: Boolean = false,
    onConfirm: (String) -> Unit,
) {
    var v by remember { mutableStateOf(initial) }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NbField(
                v, { v = it }, singleLine = true, placeholder = { Text(hint) },
                keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { NbButton(T("保存"), { onConfirm(v.trim()) }) },
        dismissButton = { NbButton(T("取消"), onDismiss, kind = BtnKind.Secondary) },
    )
}

internal val autoChoices = listOf(0, 6, 12, 24, 72)

internal fun autoLabel(hours: Int): String = when {
    hours <= 0 -> T("关闭")
    hours < 24 -> T("每 %d 小时", hours)
    else -> T("每 %d 天", hours / 24)
}

internal val languages = listOf("auto" to "跟随系统", "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English") // i18n:skip

/** One of [options] (value to label), in a sheet; [descs] are optional lines under the labels. */
@Composable
fun <V> ChoiceDialog(title: String, options: List<Pair<V, String>>, current: V, onDismiss: () -> Unit, descs: List<String> = emptyList(), onPick: (V) -> Unit) =
    dev.efah.nextbox.ui.ChoiceSheet(
        title, options.mapIndexed { i, (v, label) -> dev.efah.nextbox.ui.ChoiceOption(v, label, descs.getOrElse(i) { "" }) },
        current, onDismiss, onPick = onPick,
    )

@Composable
fun PasswordDialog(title: String, note: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(note, style = NbType.small, color = Nb.c.muted)
                NbField(
                    v, { v = it }, singleLine = true, placeholder = { Text(T("密码")) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { NbButton(confirm, { onConfirm(v) }) },
        dismissButton = { NbButton(T("取消"), onDismiss, kind = BtnKind.Secondary) },
    )
}

/** Opens the system notification settings of this app. */
internal fun openNotificationSettings(ctx: android.content.Context) {
    ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
}
