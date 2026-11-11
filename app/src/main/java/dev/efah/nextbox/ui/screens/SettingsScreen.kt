package dev.efah.nextbox.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import dev.efah.nextbox.ui.NbAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.efah.nextbox.BuildConfig
import dev.efah.nextbox.bg.Notifier
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.Updater
import dev.efah.nextbox.libbox.Libbox
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NavRow
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.RowDivider
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.SettingsGroup
import dev.efah.nextbox.ui.SwitchRow
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.theme.parseHex
import dev.efah.nextbox.data.BackupCrypto
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.Lang
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val accents = listOf("#F472B6", "#60A5FA", "#34D399", "#A78BFA", "#FB923C")

@Composable
fun SettingsScreen(onPerApp: () -> Unit, onRules: () -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val s = state.settings
    val bypass by Prefs.bypassCn.collectAsState()
    val perAppList by Prefs.perAppList.collectAsState()
    val testMode by Prefs.latencyMode.collectAsState()
    val fakeIp by Prefs.fakeIp.collectAsState()
    val showApps by Prefs.showApps.collectAsState()
    val notifySpeed by Prefs.notifySpeed.collectAsState()
    val notifyStats by Prefs.notifyStats.collectAsState()
    val billingDay by Prefs.billingDay.collectAsState()
    val glass by Prefs.glass.collectAsState()
    val language by Prefs.language.collectAsState()
    val subAuto by Prefs.subAutoHours.collectAsState()
    val autoUpdate by Prefs.autoUpdate.collectAsState()
    val newRelease by Updater.latest.collectAsState()
    var checking by remember { mutableStateOf(false) }
    // backup password dialogs: the password typed for an export, and an encrypted file waiting for one
    var exportPassword by remember { mutableStateOf("") }
    var encryptedBackup by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<String?>(null) } // remoteDns | directDns | mixedPort | billingDay

    fun patch(reload: Boolean = true, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        scope.launch { Repo.updateSettings(buildJsonObject(block), reload).onFailure { toast(it.message ?: T("保存失败")) } }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            Repo.backup().onSuccess { json ->
                val out = if (exportPassword.isEmpty()) json else withContext(Dispatchers.Default) { BackupCrypto.encrypt(json, exportPassword) }
                exportPassword = ""
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(out.toByteArray()) } }
                    .onSuccess { toast(T("已导出备份")) }.onFailure { toast(it.message ?: T("导出失败")) }
            }.onFailure { toast(it.message ?: T("导出失败")) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text == null) {
                toast(T("读取文件失败"))
                return@launch
            }
            if (BackupCrypto.isEncrypted(text)) {
                encryptedBackup = text
                return@launch
            }
            Repo.restore(text).onSuccess { toast(T("已从备份恢复")) }.onFailure { toast(it.message ?: T("恢复失败")) }
        }
    }

    GlassScreen(header = { TopBar(T("设置")) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(T("连接")) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Segmented(listOf(T("VPN 模式"), T("仅代理")), if (s.tunEnabled) 0 else 1, onSelect = { i ->
                        patch { put("tunEnabled", i == 0) }
                    })
                    Text(
                        if (s.tunEnabled) T("接管全部应用（或下方选中的应用）") else T("只开启本地代理端口 %d，需要应用自己设置代理", s.mixedPort),
                        style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                RowDivider()
                SwitchRow(T("绕过中国大陆"), T("全局代理和指定应用都遵守：国内网站和 IP 直连"), bypass, { on ->
                    scope.launch { Repo.setRoute(bypass = on) }
                })
                RowDivider()
                NavRow(T("指定应用"), T("路由模式为「指定应用」时，只有这些应用走 VPN"), T("已选 %d 个", perAppList.size), onClick = onPerApp)
                RowDivider()
                NavRow(T("自定义规则"), T("指定哪些网站或应用直连、走代理或屏蔽"), T("%d 条", ruleCount(s.directRules) + ruleCount(s.proxyRules) + ruleCount(s.blockRules)), onClick = onRules)
                RowDivider()
                SwitchRow(T("屏蔽广告"), T("拦截 geosite-category-ads-all 广告域名"), s.blockAds, { on -> patch { put("blockAds", on) } })
                RowDivider()
                SwitchRow("IPv6", T("为 VPN 分配 IPv6 地址并解析 IPv6"), s.enableIpv6, { on -> patch { put("enableIpv6", on) } })
            }

            SettingsGroup(T("订阅")) {
                NavRow(T("自动更新订阅"), T("打开 App 时和连接期间检查，超过这个时间就更新"), autoLabel(subAuto)) { edit = "subAuto" }
            }

            SettingsGroup(T("通知栏")) {
                SwitchRow(T("常驻实时网速"), T("通知里显示上传 / 下载速度"), notifySpeed, { Prefs.setNotifySpeed(it) })
                RowDivider()
                SwitchRow(T("显示流量统计"), T("本次、今日和本月用量"), notifyStats, { Prefs.setNotifyStats(it) })
                RowDivider()
                NavRow(T("每月结算日"), T("「本月」用量从这一天开始计算"), if (billingDay == 1) T("每月 1 日") else T("每月 %d 日", billingDay)) { edit = "billingDay" }
                if (!Notifier.permitted()) {
                    RowDivider()
                    NavRow(T("通知未开启"), T("开启后才能在通知栏看到网速和流量"), T("去开启")) {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName),
                        )
                    }
                }
            }

            SettingsGroup(T("测速")) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Segmented(listOf(T("URL 测试"), T("TCP 测试")), if (testMode == Prefs.TEST_TCP) 1 else 0, onSelect = { i ->
                        Prefs.setLatencyMode(if (i == 1) Prefs.TEST_TCP else Prefs.TEST_URL)
                    })
                    Text(
                        if (testMode == Prefs.TEST_TCP) {
                            T("TCP 测试只测到服务器的握手时间，更快但不代表节点可用。Hysteria2、TUIC 等 UDP 协议仍使用 URL 测试。")
                        } else {
                            T("通过节点实际访问测试地址，结果最接近真实体验。")
                        },
                        style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                RowDivider()
                NavRow(T("测试地址"), T("URL 测试访问的地址"), testUrlLabel(s.testUrl)) { edit = "testUrl" }
                RowDivider()
                NavRow(T("超时时间"), T("超过这个时间算作失败"), T("%d 毫秒", s.testTimeout)) { edit = "testTimeout" }
                RowDivider()
                NavRow(T("同时测试数"), T("一次并行测试的节点数"), s.testConcurrency.toString()) { edit = "testConcurrency" }
            }

            SettingsGroup(T("性能")) {
                SwitchRow("FakeIP", T("代理的域名立即返回虚拟地址，省去一次经代理的 DNS 查询，打开网页更快"), fakeIp, { on ->
                    Prefs.setFakeIp(on)
                    dev.efah.nextbox.bg.Box.reloadIfRunning()
                })
                RowDivider()
                SwitchRow(T("显示连接所属应用"), T("「活动」页显示每个连接是哪个应用发起的，每个连接都要多查询一次，略增耗电"), showApps, { on ->
                    Prefs.setShowApps(on)
                    dev.efah.nextbox.bg.Box.reloadIfRunning()
                })
            }

            SettingsGroup(T("网络")) {
                NavRow(T("远程 DNS"), T("通过代理查询"), s.remoteDns) { edit = "remoteDns" }
                RowDivider()
                NavRow(T("直连 DNS"), T("国内域名和直连流量使用"), s.directDns) { edit = "directDns" }
                RowDivider()
                NavRow(T("本地代理端口"), T("HTTP + SOCKS5 混合端口，仅代理模式或允许局域网连接时开启"), s.mixedPort.toString()) { edit = "mixedPort" }
                RowDivider()
                SwitchRow(T("允许局域网连接"), T("局域网内其他设备可以使用这台手机的代理端口"), s.allowLan, { on -> patch { put("allowLan", on) } })
                RowDivider()
                NavRow(T("更新规则集"), T("重新下载当前需要的规则集")) {
                    scope.launch { Repo.updateRuleSets().onSuccess { toast(it) }.onFailure { toast(it.message ?: T("更新失败")) } }
                }
            }

            SettingsGroup(T("外观")) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    val themes = listOf("auto", "light", "dark")
                    Segmented(listOf(T("跟随系统"), T("浅色"), T("深色")), themes.indexOf(s.theme).coerceAtLeast(0), onSelect = { i ->
                        patch(reload = false) { put("theme", themes[i]) }
                    })
                }
                RowDivider()
                SwitchRow(T("玻璃效果"), T("导航栏、按钮和弹窗的模糊与折射；省电模式下自动关闭"), glass, { Prefs.setGlass(it) }, enabled = android.os.Build.VERSION.SDK_INT >= 31)
                RowDivider()
                NavRow(T("语言"), null, languages.firstOrNull { it.first == language }?.second.orEmpty()) { edit = "language" }
                RowDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(T("主题色"), style = NbType.body, color = c.text, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        accents.forEach { hex ->
                            val sel = hex.equals(s.accentColor, ignoreCase = true)
                            val ring by animateColorAsState(if (sel) c.text else Color.Transparent, label = "ring")
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .border(2.dp, ring, CircleShape)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(parseHex(hex))
                                    .clickable { patch(reload = false) { put("accentColor", hex) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (sel) Icon(NbIcons.Check, T("已选中"), tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            SettingsGroup(T("数据")) {
                NavRow(T("导出备份"), T("节点、订阅和设置，可以设置密码加密")) { edit = "exportPw" }
                RowDivider()
                NavRow(T("从备份恢复"), T("也可以直接选择 Windows 版的 profiles.json")) {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }

            SettingsGroup(T("关于")) {
                fun open(url: String) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                }
                NavRow("NextBox", T("基于 NekoBox / sing-box 的代理客户端"), BuildConfig.VERSION_NAME) {}
                RowDivider()
                NavRow(
                    T("检查更新"),
                    newRelease?.let { T("有新版本 %s", it.version) } ?: T("与 GitHub Releases 上的最新版本比对"),
                    if (checking) T("检查中…") else "",
                ) {
                    if (checking) return@NavRow
                    checking = true
                    scope.launch {
                        Updater.check(manual = true)
                            .onSuccess { if (it == null) toast(T("已是最新版本")) }
                            .onFailure { toast(T("检查更新失败：") + (it.message ?: it.javaClass.simpleName)) }
                        checking = false
                    }
                }
                RowDivider()
                SwitchRow(T("自动检查更新"), T("打开 App 时检查，有新版本会提醒"), autoUpdate, { Prefs.setAutoUpdate(it) })
                RowDivider()
                NavRow(T("官网"), "nextbox.efah.dev") { open(Updater.WEBSITE) }
                RowDivider()
                NavRow("GitHub", "github.com/" + Updater.REPO) { open(Updater.GITHUB) }
                RowDivider()
                NavRow(T("sing-box 内核"), "with_gvisor · with_quic · with_utls", runCatching { Libbox.version() }.getOrDefault("")) {}
            }
        }
    }

    encryptedBackup?.let { text ->
        PasswordDialog(T("输入备份密码"), T("这个备份文件是加密的。"), T("恢复"), { encryptedBackup = null }) { pw ->
            encryptedBackup = null
            scope.launch {
                val json = withContext(Dispatchers.Default) { runCatching { BackupCrypto.decrypt(text, pw) }.getOrNull() }
                if (json == null) {
                    toast(T("密码不对，或者文件已损坏"))
                } else {
                    Repo.restore(json).onSuccess { toast(T("已从备份恢复")) }.onFailure { toast(it.message ?: T("恢复失败")) }
                }
            }
        }
    }

    when (edit) {
        "subAuto" -> ChoiceDialog(T("自动更新订阅"), autoChoices.map { it to autoLabel(it) }, subAuto, { edit = null }) { v ->
            edit = null
            Prefs.setSubAutoHours(v)
        }
        "language" -> ChoiceDialog(T("语言"), languages, language, { edit = null }) { v ->
            edit = null
            Prefs.setLanguage(v)
            Lang.apply(v)
        }
        "exportPw" -> PasswordDialog(T("导出备份"), T("备份里有节点密码和订阅地址。设置密码后文件会被加密，恢复时需要输入；留空则不加密。"), T("导出"), { edit = null }) { pw ->
            edit = null
            exportPassword = pw
            exportLauncher.launch("NextBox-backup.json")
        }
        "remoteDns" -> TextDialog(T("远程 DNS"), s.remoteDns, T("例如 https://1.1.1.1/dns-query"), { edit = null }) { v ->
            edit = null
            patch { put("remoteDns", v) }
        }
        "directDns" -> TextDialog(T("直连 DNS"), s.directDns, T("例如 223.5.5.5"), { edit = null }) { v ->
            edit = null
            patch { put("directDns", v) }
        }
        "mixedPort" -> TextDialog(T("本地代理端口"), s.mixedPort.toString(), "1024 - 65535", { edit = null }, number = true) { v ->
            edit = null
            val port = v.toIntOrNull()
            if (port == null || port !in 1024..65535) toast(T("端口无效")) else patch { put("mixedPort", port) }
        }
        "testUrl" -> TestUrlDialog(s.testUrl, { edit = null }) { v ->
            edit = null
            patch(reload = false) { put("testUrl", v) }
        }
        "testTimeout" -> TextDialog(T("超时时间（毫秒）"), s.testTimeout.toString(), "1000 - 30000", { edit = null }, number = true) { v ->
            edit = null
            val ms = v.toIntOrNull()
            if (ms == null || ms !in 1000..30000) toast(T("请输入 1000 到 30000")) else patch(reload = false) { put("testTimeout", ms) }
        }
        "testConcurrency" -> TextDialog(T("同时测试数"), s.testConcurrency.toString(), "1 - 64", { edit = null }, number = true) { v ->
            edit = null
            val n = v.toIntOrNull()
            if (n == null || n !in 1..64) toast(T("请输入 1 到 64")) else patch(reload = false) { put("testConcurrency", n) }
        }
        "billingDay" -> TextDialog(T("每月结算日"), billingDay.toString(), "1 - 28", { edit = null }, number = true) { v ->
            edit = null
            val d = v.toIntOrNull()
            if (d == null || d !in 1..28) toast(T("请输入 1 到 28")) else Prefs.setBillingDay(d)
        }
    }
}

private val testUrls = listOf(
    "Google" to "https://www.google.com/generate_204",
    "Cloudflare" to "https://cp.cloudflare.com/generate_204",
)

private fun testUrlLabel(url: String): String = testUrls.firstOrNull { it.second == url }?.first ?: url.removePrefix("https://").removePrefix("http://")

@Composable
private fun TestUrlDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = Nb.c
    val preset = testUrls.firstOrNull { it.second == current }
    var choice by remember { mutableStateOf(preset?.first ?: "custom") }
    var custom by remember { mutableStateOf(if (preset == null) current else "") }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T("测试地址")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (testUrls.map { it.first to it.second } + ("custom" to "")).forEach { (name, url) ->
                    Row(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable { choice = name }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = choice == name, onClick = { choice = name })
                        Column {
                            Text(if (name == "custom") T("自定义") else name, style = NbType.body, color = c.text)
                            if (url.isNotEmpty()) Text(url, style = NbType.small, color = c.muted)
                        }
                    }
                }
                if (choice == "custom") {
                    OutlinedTextField(custom, { custom = it }, singleLine = true, placeholder = { Text("https://") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = testUrls.firstOrNull { it.first == choice }?.second ?: custom.trim()
                if (url.startsWith("http://") || url.startsWith("https://")) onConfirm(url) else onDismiss()
            }) { Text(T("保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T("取消")) } },
    )
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
            OutlinedTextField(
                v, { v = it }, singleLine = true, placeholder = { Text(hint) },
                keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(v.trim()) }) { Text(T("保存")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T("取消")) } },
    )
}

private fun ruleCount(text: String) = text.lines().count { it.isNotBlank() && !it.trim().startsWith("#") }

private val autoChoices = listOf(0, 6, 12, 24, 72)

private fun autoLabel(hours: Int): String = when {
    hours <= 0 -> T("关闭")
    hours < 24 -> T("每 %d 小时", hours)
    else -> T("每 %d 天", hours / 24)
}

private val languages = listOf("auto" to "跟随系统", "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English") // i18n:skip

@Composable
private fun <V> ChoiceDialog(title: String, options: List<Pair<V, String>>, current: V, onDismiss: () -> Unit, onPick: (V) -> Unit) {
    val c = Nb.c
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable { onPick(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = value == current, onClick = { onPick(value) })
                        Text(if (value == "auto") T(label) else label, style = NbType.body, color = c.text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T("取消")) } },
    )
}

@Composable
private fun PasswordDialog(title: String, note: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(note, style = NbType.small, color = Nb.c.muted)
                OutlinedTextField(
                    v, { v = it }, singleLine = true, placeholder = { Text(T("密码")) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(v) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T("取消")) } },
    )
}
