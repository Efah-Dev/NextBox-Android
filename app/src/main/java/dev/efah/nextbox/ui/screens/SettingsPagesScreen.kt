package dev.efah.nextbox.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.bg.Notifier
import dev.efah.nextbox.BuildConfig
import dev.efah.nextbox.data.BackupInfo
import dev.efah.nextbox.data.BackupParts
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.Updater
import dev.efah.nextbox.libbox.Libbox
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NavRow
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbField
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.RowDivider
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.SettingsGroup
import dev.efah.nextbox.ui.SwitchRow
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.TopBar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One settings sub-page (see SettingsPages). */
@Composable
fun SettingsPageScreen(page: String, onClose: () -> Unit) {
    val title = when (page) {
        SettingsPages.AUTO_SELECT -> T("自动选择节点")
        SettingsPages.TEST -> T("测速")
        SettingsPages.NOTIFY -> T("通知栏")
        SettingsPages.ADVANCED -> T("高级")
        SettingsPages.ABOUT -> T("关于 NextBox")
        SettingsPages.BACKUP -> T("备份与恢复")
        else -> ""
    }
    GlassScreen(header = { TopBar(title, leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = LocalBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                SettingsPages.AUTO_SELECT -> AutoSelectPage()
                SettingsPages.TEST -> TestPage()
                SettingsPages.NOTIFY -> NotifyPage()
                SettingsPages.ADVANCED -> AdvancedPage()
                SettingsPages.ABOUT -> AboutPage()
                SettingsPages.BACKUP -> BackupPage()
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = NbType.small, color = Nb.c.muted, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun patcher(reloadDefault: Boolean = true): (Boolean, kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    return { reload, block ->
        scope.launch { Repo.updateSettings(buildJsonObject(block), reload && reloadDefault).onFailure { toast.error(it.message ?: T("保存失败")) } }
    }
}

// ---------------------------------------------------------------- auto select

@Composable
private fun ColumnScope.AutoSelectPage() {
    val c = Nb.c
    val on by Prefs.autoSelect.collectAsState()
    val all by Prefs.autoSelectAll.collectAsState()
    val timeout by Prefs.autoSelectTimeout.collectAsState()
    val watch by Prefs.autoSelectWatch.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    NbCard(padding = PaddingValues(0.dp)) {
        SwitchRow(T("自动选择节点"), T("当前节点不通时，换成最快的可用节点"), on, { Prefs.setAutoSelect(it) }, info = T("点连接时如果当前节点连不上，就从下面选的范围里测出最快的可用节点并换过去。默认关闭。"))
        RowDivider()
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(T("从哪些节点里选"), style = NbType.body, color = if (on) c.text else c.muted, modifier = Modifier.padding(start = 4.dp))
            Segmented(listOf(T("当前节点所在分组"), T("所有节点")), if (all) 1 else 0, onSelect = { Prefs.setAutoSelectAll(it == 1) })
        }
        RowDivider()
        NavRow(T("判定超时"), T("超过这个时间算连不上"), T("%d 秒", timeout / 1000)) { dialog = true }
        RowDivider()
        SwitchRow(T("连接中节点失效也切换"), T("亮屏时每 5 分钟检查一次"), watch, { Prefs.setAutoSelectWatch(it) }, enabled = on, info = T("连接后，亮屏时每 5 分钟测一次正在用的节点；连续两次不通才换，一次网络波动不会切换。"))
    }
    NbCard {
        Text(T("点连接后"), style = NbType.section, color = c.muted)
        listOf(
            T("先确认手机能上网，再测当前节点"),
            T("不通就同时测同组其他节点"),
            T("连到最快的可用节点；一个都不通就不连接，并说明原因"),
        ).forEachIndexed { i, s ->
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = c.accentInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(s, style = NbType.body.copy(fontSize = 14.sp), color = c.text)
            }
        }
    }
    Note(T("测试使用「测速」里的测试地址，结果会更新到节点列表。"))
    if (dialog) {
        ChoiceDialog(T("判定超时"), listOf(2000, 3000, 5000, 8000).map { it to T("%d 秒", it / 1000) }, timeout, { dialog = false }) {
            dialog = false
            Prefs.setAutoSelectTimeout(it)
        }
    }
}

// ---------------------------------------------------------------- latency test

private val testUrls = listOf(
    "Google" to "https://www.google.com/generate_204",
    "Cloudflare" to "https://cp.cloudflare.com/generate_204",
)

private fun testUrlLabel(url: String): String = testUrls.firstOrNull { it.second == url }?.first ?: url.removePrefix("https://").removePrefix("http://")

@Composable
private fun ColumnScope.TestPage() {
    val c = Nb.c
    val toast = LocalToast.current
    val state by Repo.state.collectAsState()
    val s = state.settings
    val mode by Prefs.latencyMode.collectAsState()
    val patch = patcher(reloadDefault = false)
    var edit by remember { mutableStateOf<String?>(null) }
    NbCard(padding = PaddingValues(0.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Segmented(listOf(T("URL 测试"), T("TCP 测试")), if (mode == Prefs.TEST_TCP) 1 else 0, onSelect = { i ->
                Prefs.setLatencyMode(if (i == 1) Prefs.TEST_TCP else Prefs.TEST_URL)
            })
            Text(
                if (mode == Prefs.TEST_TCP) {
                    T("TCP 测试只测到服务器的握手时间，更快但不代表节点可用。Hysteria2、TUIC 等 UDP 协议和链式代理仍使用 URL 测试。")
                } else {
                    T("通过节点实际访问测试地址，结果最接近真实体验。")
                },
                style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
        RowDivider()
        NavRow(T("测试地址"), T("URL 测试访问的地址"), testUrlLabel(s.testUrl)) { edit = "url" }
        RowDivider()
        NavRow(T("超时时间"), T("超过这个时间算作失败"), T("%d 毫秒", s.testTimeout)) { edit = "timeout" }
        RowDivider()
        NavRow(T("同时测试数"), T("一次并行测试的节点数"), s.testConcurrency.toString()) { edit = "concurrency" }
        RowDivider()
        val sorts = sortChoices()
        NavRow(T("测速后"), T("测试整个分组之后节点怎么排"), sorts.firstOrNull { it.first == s.sortAfterTest }?.second ?: T("每次询问")) { edit = "sort" }
    }
    when (edit) {
        "sort" -> dev.efah.nextbox.ui.ChoiceSheet(
            T("测速后"),
            listOf(dev.efah.nextbox.ui.ChoiceOption("", T("每次询问"), T("测试整个分组前问一次"))) +
                sortChoices().map { (v, t, d) -> dev.efah.nextbox.ui.ChoiceOption(v, t, d) },
            s.sortAfterTest, { edit = null },
        ) { v -> edit = null; patch(false) { put("sortAfterTest", v) } }
        "url" -> TestUrlDialog(s.testUrl, { edit = null }) { v -> edit = null; patch(false) { put("testUrl", v) } }
        "timeout" -> TextDialog(T("超时时间（毫秒）"), s.testTimeout.toString(), "1000 - 30000", { edit = null }, number = true) { v ->
            edit = null
            val ms = v.toIntOrNull()
            if (ms == null || ms !in 1000..30000) toast.error(T("请输入 1000 到 30000")) else patch(false) { put("testTimeout", ms) }
        }
        "concurrency" -> TextDialog(T("同时测试数"), s.testConcurrency.toString(), "1 - 64", { edit = null }, number = true) { v ->
            edit = null
            val n = v.toIntOrNull()
            if (n == null || n !in 1..64) toast.error(T("请输入 1 到 64")) else patch(false) { put("testConcurrency", n) }
        }
    }
}

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
                (testUrls + ("custom" to "")).forEach { (name, url) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { choice = name }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == name, onClick = { choice = name })
                        Column {
                            Text(if (name == "custom") T("自定义") else name, style = NbType.body, color = c.text)
                            if (url.isNotEmpty()) Text(url, style = NbType.small, color = c.muted)
                        }
                    }
                }
                if (choice == "custom") {
                    NbField(custom, { custom = it }, singleLine = true, placeholder = { Text("https://") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            NbButton(T("保存"), {
                val url = testUrls.firstOrNull { it.first == choice }?.second ?: custom.trim()
                if (url.startsWith("http://") || url.startsWith("https://")) onConfirm(url) else onDismiss()
            })
        },
        dismissButton = { NbButton(T("取消"), onDismiss, kind = BtnKind.Secondary) },
    )
}

// ---------------------------------------------------------------- notification

@Composable
private fun ColumnScope.NotifyPage() {
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val speed by Prefs.notifySpeed.collectAsState()
    val stats by Prefs.notifyStats.collectAsState()
    val billingDay by Prefs.billingDay.collectAsState()
    var edit by remember { mutableStateOf(false) }
    NbCard(padding = PaddingValues(0.dp)) {
        SwitchRow(T("常驻实时网速"), T("通知里显示上传 / 下载速度"), speed, { Prefs.setNotifySpeed(it) })
        RowDivider()
        SwitchRow(T("显示流量统计"), T("本次、今日和本月用量"), stats, { Prefs.setNotifyStats(it) })
        RowDivider()
        NavRow(T("每月结算日"), T("「本月」用量从这一天开始计算"), if (billingDay == 1) T("每月 1 日") else T("每月 %d 日", billingDay)) { edit = true }
        if (!Notifier.permitted()) {
            RowDivider()
            NavRow(T("通知未开启"), T("开启后才能在通知栏看到网速和流量"), T("去开启")) { openNotificationSettings(ctx) }
        }
    }
    if (edit) {
        TextDialog(T("每月结算日"), billingDay.toString(), "1 - 28", { edit = false }, number = true) { v ->
            edit = false
            val d = v.toIntOrNull()
            if (d == null || d !in 1..28) toast.error(T("请输入 1 到 28")) else Prefs.setBillingDay(d)
        }
    }
}

// ---------------------------------------------------------------- advanced

@Composable
private fun ColumnScope.AdvancedPage() {
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val s = state.settings
    val fakeIp by Prefs.fakeIp.collectAsState()
    val showApps by Prefs.showApps.collectAsState()
    val patch = patcher()
    var edit by remember { mutableStateOf<String?>(null) }
    SettingsGroup(T("DNS")) {
        NavRow(T("远程 DNS"), T("通过代理查询"), s.remoteDns) { edit = "remoteDns" }
        RowDivider()
        NavRow(T("直连 DNS"), T("国内域名和直连流量使用"), s.directDns) { edit = "directDns" }
        RowDivider()
        DnsOverridesRow(s.dnsOverrides)
        RowDivider()
        SubscriptionDnsRow(state.groups)
        RowDivider()
        SwitchRow("FakeIP", T("打开网页更快，省一次 DNS 查询"), fakeIp, { on ->
            Prefs.setFakeIp(on)
            dev.efah.nextbox.bg.Box.reloadIfRunning()
        }, info = T("走代理的域名立即得到一个虚拟地址，真实地址由代理那边解析，省去一次经代理的 DNS 查询。"))
    }
    SettingsGroup(T("网络")) {
        SwitchRow("IPv6", T("为 VPN 分配 IPv6 地址并解析 IPv6"), s.enableIpv6, { on -> patch(true) { put("enableIpv6", on) } })
        RowDivider()
        NavRow(T("本地代理端口"), T("HTTP + SOCKS5 混合端口"), s.mixedPort.toString(), info = T("仅代理模式，或打开「允许局域网连接」时开启。VPN 模式下手机上的应用不需要它。")) { edit = "mixedPort" }
        RowDivider()
        SwitchRow(T("允许局域网连接"), T("其他设备可以用这台手机的代理端口"), s.allowLan, { on -> patch(true) { put("allowLan", on) } })
        RowDivider()
        SwitchRow(T("显示连接所属应用"), T("在「活动」页显示，略增耗电"), showApps, { on ->
            Prefs.setShowApps(on)
            dev.efah.nextbox.bg.Box.reloadIfRunning()
        }, info = T("「活动」页显示每个连接是哪个应用发起的。每个连接都要向系统多查询一次，所以会略增耗电。"))
        RowDivider()
        NavRow(T("更新规则集"), T("重新下载当前需要的规则集")) {
            scope.launch { Repo.updateRuleSets().onSuccess { toast.ok(it) }.onFailure { toast.error(it.message ?: T("更新失败")) } }
        }
    }
    when (edit) {
        "remoteDns" -> TextDialog(T("远程 DNS"), s.remoteDns, T("例如 https://1.1.1.1/dns-query"), { edit = null }) { v -> edit = null; patch(true) { put("remoteDns", v) } }
        "directDns" -> TextDialog(T("直连 DNS"), s.directDns, T("例如 223.5.5.5"), { edit = null }) { v -> edit = null; patch(true) { put("directDns", v) } }
        "mixedPort" -> TextDialog(T("本地代理端口"), s.mixedPort.toString(), "1024 - 65535", { edit = null }, number = true) { v ->
            edit = null
            val port = v.toIntOrNull()
            if (port == null || port !in 1024..65535) toast.error(T("端口无效")) else patch(true) { put("mixedPort", port) }
        }
    }
}

// ---------------------------------------------------------------- about

@Composable
private fun ColumnScope.AboutPage() {
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val autoUpdate by Prefs.autoUpdate.collectAsState()
    val newRelease by Updater.latest.collectAsState()
    var checking by remember { mutableStateOf(false) }
    fun open(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }
    NbCard(padding = PaddingValues(0.dp)) {
        NavRow("NextBox", T("基于 sing-box 内核的代理客户端"), BuildConfig.VERSION_NAME) {}
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
                    .onSuccess { if (it == null) toast.ok(T("已是最新版本")) }
                    .onFailure { toast.error(T("检查更新失败：") + (it.message ?: it.javaClass.simpleName)) }
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
        NavRow(T("sing-box 内核"), "with_gvisor · with_quic · with_utls · with_naive_outbound", runCatching { Libbox.version() }.getOrDefault("")) {}
    }
}

// ---------------------------------------------------------------- backup

/**
 * Backups move between NextBox for Android and for Windows, with the same
 * three parts as the desktop app (nodes, routing rules, other settings),
 * sealed with a password. An import shows what the file holds and takes the
 * parts the user ticks.
 */
@Composable
private fun ColumnScope.BackupPage() {
    val c = Nb.c
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    var nodes by remember { mutableStateOf(true) }
    var rules by remember { mutableStateOf(true) }
    var look by remember { mutableStateOf(true) }
    var other by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // import: the file, its password once given, and what it holds
    var pending by remember { mutableStateOf<String?>(null) }
    var pendingPassword by remember { mutableStateOf("") }
    var askPassword by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<BackupInfo?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            Repo.exportBackup(BackupParts(nodes = nodes, rules = rules, settings = other, appearance = look, password = password)).onSuccess { out ->
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(out.toByteArray()) } }
                    .onSuccess { toast.ok(T("已导出备份")); password = ""; again = "" }.onFailure { toast.error(it.message ?: T("导出失败")) }
            }.onFailure { toast.error(it.message ?: T("导出失败")) }
            busy = false
        }
    }

    fun inspect(text: String, pw: String) {
        scope.launch {
            Repo.inspectBackup(text, pw).onSuccess { i ->
                pending = text
                if (i.needPassword) {
                    askPassword = true
                } else {
                    pendingPassword = pw
                    info = i
                }
            }.onFailure { toast.error(it.message ?: T("不是有效的备份文件")) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        if (text == null) toast.error(T("读取文件失败")) else inspect(text, "")
    }

    val nothing = !nodes && !rules && !look && !other
    Note(T("备份文件用密码加密。手机版和 NextBox for Windows 导出的备份可以互相导入。"))
    NbCard {
        Text(T("导出备份"), style = NbType.bodyBold, color = c.text)
        Spacer(Modifier.height(4.dp))
        PartRow(nodes, T("节点与订阅"), T("含链式代理和分组代理")) { nodes = it }
        PartRow(rules, T("分流规则"), T("%d 条规则", RuleUi.count(state.settings.rules) { true })) { rules = it }
        PartRow(look, T("外观"), T("主题、主题色、语言")) { look = it }
        PartRow(other, T("其他设置"), T("DNS、代理范围、通知等")) { other = it }
        val pw = androidx.compose.ui.text.input.PasswordVisualTransformation()
        NbField(
            password, { password = it }, singleLine = true, label = { Text(T("密码")) },
            visualTransformation = pw, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        NbField(
            again, { again = it }, singleLine = true, label = { Text(T("再输一次")) },
            isError = again.isNotEmpty() && again != password,
            supportingText = { Text(if (again.isNotEmpty() && again != password) T("两次输入的密码不一样") else T("忘记密码就没法恢复，请记好。")) },
            visualTransformation = pw, modifier = Modifier.fillMaxWidth(),
        )
        NbButton(when {
                    busy -> T("正在导出…")
                    nothing -> T("请至少选择一项内容")
                    else -> T("保存备份文件…")
                }, { exportLauncher.launch("NextBox-" + T("备份") + "-" + java.time.LocalDate.now() + ".json") }, Modifier.fillMaxWidth().padding(top = 6.dp).height(48.dp), enabled = !busy && !nothing && password.isNotEmpty() && password == again)
    }
    NbCard {
        Text(T("导入备份"), style = NbType.bodyBold, color = c.text)
        Text(T("手机版或 Windows 版导出的文件，也支持 profiles.json"), style = NbType.small, color = c.muted)
        NbButton(T("选择文件…"), { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp))
    }

    if (askPassword) {
        PasswordDialog(T("输入备份密码"), T("这个备份文件是加密的。"), T("确定"), { askPassword = false; pending = null }) { pw ->
            askPassword = false
            pending?.let { inspect(it, pw) }
        }
    }

    info?.let { i ->
        ImportSheet(i, onDismiss = { info = null; pending = null }) { parts ->
            val text = pending ?: return@ImportSheet
            info = null
            pending = null
            scope.launch {
                Repo.importBackup(text, parts.copy(password = pendingPassword))
                    .onSuccess { toast.ok(it) }.onFailure { toast.error(it.message ?: T("恢复失败")) }
            }
        }
    }
}

/** A part of a backup to tick: a title, what it holds, a checkbox. */
@Composable
private fun PartRow(checked: Boolean, title: String, sub: String, onChange: (Boolean) -> Unit) {
    val c = Nb.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = c.accentStrong))
        Text(title, style = NbType.body, color = c.text, modifier = Modifier.weight(1f))
        Text(sub, style = NbType.small.copy(fontSize = 12.5.sp), color = c.muted, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
    }
}

/** What a backup holds, the parts to take, and whether its nodes are added or replace the list. */
@Composable
private fun ImportSheet(i: BackupInfo, onDismiss: () -> Unit, onImport: (BackupParts) -> Unit) {
    val c = Nb.c
    var nodes by remember { mutableStateOf(i.hasNodes) }
    var rules by remember { mutableStateOf(i.hasRules) }
    // as in the desktop app, the look is taken by default, the other
    // settings only when ticked
    var look by remember { mutableStateOf(i.hasAppearance) }
    var other by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf(false) }
    val fromOther = i.platform != "android"
    val any = (nodes && i.hasNodes) || (rules && i.hasRules) || (look && i.hasAppearance) || (other && i.hasSettings)
    NbSheet(onDismiss) {
        Text(T("导入这份备份？"), style = NbType.big, color = c.text)
        Text(
            when (i.platform) {
                "windows" -> T("来自 NextBox for Windows")
                "android" -> T("来自 NextBox for Android")
                else -> T("来源未知")
            } + if (i.encrypted) " · " + T("已解密") else "",
            color = c.accentInk, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp).clip(RoundedCornerShape(8.dp)).background(c.accentSoft).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        NbCard(padding = PaddingValues(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
            if (i.hasNodes) {
                val s = i.summary
                PartRow(
                    nodes, T("节点与订阅"),
                    T("%d 个节点", s.nodes + s.chains) + (if (s.subscriptions > 0) T("，%d 个订阅", s.subscriptions) else "") +
                        (if (s.policies > 0) T("，%d 个分组代理", s.policies) else ""),
                ) { nodes = it }
            }
            if (i.hasRules) PartRow(rules, T("分流规则"), T("%d 条，会替换现有规则", i.rules)) { rules = it }
            if (i.hasAppearance) PartRow(look, T("外观"), T("主题、主题色、语言")) { look = it }
            if (i.hasSettings) PartRow(other, T("其他设置"), if (fromOther) T("DNS、测速") else T("全部，包括代理范围")) { other = it }
        }
        val notes = buildList {
            if (rules && i.desktopRules > 0) add(T("其中 %d 条按电脑程序分流，只在 Windows 版生效，会原样保留，再导出给电脑时不会丢。", i.desktopRules))
            if (rules && i.scenes.isNotEmpty()) add(T("电脑上打开的「%s」会变成同名规则集，放在原来的位置。", i.scenes.joinToString(T("、")) { sceneName(it) }))
            if (fromOther && ((rules && i.hasRules) || (other && i.hasSettings))) add(T("全部直连的选择、端口、TUN 等这台手机的设置不会被覆盖。"))
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            NbCard(color = c.line.copy(alpha = 0.35f), border = androidx.compose.ui.graphics.Color.Transparent) {
                notes.forEachIndexed { n, t ->
                    Text(t, style = NbType.small, color = c.muted, modifier = Modifier.padding(top = if (n > 0) 6.dp else 0.dp))
                }
            }
        }
        if (i.hasNodes && nodes) {
            Spacer(Modifier.height(14.dp))
            Text(T("节点怎么导入"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
            Spacer(Modifier.height(8.dp))
            Segmented(listOf(T("合并到现有节点"), T("替换现有节点")), if (replace) 1 else 0, onSelect = { replace = it == 1 }, height = 40.dp)
            Text(
                if (replace) T("现有的分组、节点和分组代理会被替换。") else T("已有的订阅不会重复添加。"),
                style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NbButton(T("取消"), onDismiss, Modifier.weight(1f).height(48.dp), kind = BtnKind.Secondary)
            NbButton(T("导入"), { onImport(BackupParts(nodes = nodes && i.hasNodes, rules = rules && i.hasRules, settings = other && i.hasSettings, appearance = look && i.hasAppearance, replace = replace)) }, Modifier.weight(2f).height(48.dp), enabled = any)
        }
    }
}

/** The names of the desktop app's scenes that become rule sets here (see foldScenes in the core). */
private fun sceneName(id: String): String = when (id) {
    "ai" -> T("AI 服务")
    "media" -> T("流媒体")
    else -> id
}
