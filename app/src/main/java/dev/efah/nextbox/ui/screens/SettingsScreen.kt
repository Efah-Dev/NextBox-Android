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
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val accents = listOf("#F472B6", "#60A5FA", "#34D399", "#A78BFA", "#FB923C")

@Composable
fun SettingsScreen(onPerApp: () -> Unit) {
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
    var edit by remember { mutableStateOf<String?>(null) } // remoteDns | directDns | mixedPort | billingDay

    fun patch(reload: Boolean = true, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        scope.launch { Repo.updateSettings(buildJsonObject(block), reload).onFailure { toast(it.message ?: "保存失败") } }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            Repo.backup().onSuccess { json ->
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                    .onSuccess { toast("已导出备份") }.onFailure { toast(it.message ?: "导出失败") }
            }.onFailure { toast(it.message ?: "导出失败") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text == null) {
                toast("读取文件失败")
                return@launch
            }
            Repo.restore(text).onSuccess { toast("已从备份恢复") }.onFailure { toast(it.message ?: "恢复失败") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("设置")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup("连接") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Segmented(listOf("VPN 模式", "仅代理"), if (s.tunEnabled) 0 else 1, onSelect = { i ->
                        patch { put("tunEnabled", i == 0) }
                    })
                    Text(
                        if (s.tunEnabled) "接管全部应用（或下方选中的应用）" else "只开启本地代理端口 ${s.mixedPort}，需要应用自己设置代理",
                        style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                RowDivider()
                SwitchRow("绕过中国大陆", "全局代理和指定应用都遵守：国内网站和 IP 直连", bypass, { on ->
                    scope.launch { Repo.setRoute(bypass = on) }
                })
                RowDivider()
                NavRow("指定应用", "路由模式为「指定应用」时，只有这些应用走 VPN", "已选 ${perAppList.size} 个", onClick = onPerApp)
                RowDivider()
                SwitchRow("屏蔽广告", "拦截 geosite-category-ads-all 广告域名", s.blockAds, { on -> patch { put("blockAds", on) } })
                RowDivider()
                SwitchRow("IPv6", "为 VPN 分配 IPv6 地址并解析 IPv6", s.enableIpv6, { on -> patch { put("enableIpv6", on) } })
            }

            SettingsGroup("通知栏") {
                SwitchRow("常驻实时网速", "通知里显示上传 / 下载速度", notifySpeed, { Prefs.setNotifySpeed(it) })
                RowDivider()
                SwitchRow("显示流量统计", "本次、今日和本月用量", notifyStats, { Prefs.setNotifyStats(it) })
                RowDivider()
                NavRow("每月结算日", "「本月」用量从这一天开始计算", if (billingDay == 1) "每月 1 日" else "每月 $billingDay 日") { edit = "billingDay" }
                if (!Notifier.permitted()) {
                    RowDivider()
                    NavRow("通知未开启", "开启后才能在通知栏看到网速和流量", "去开启") {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName),
                        )
                    }
                }
            }

            SettingsGroup("测速") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Segmented(listOf("URL 测试", "TCP 测试"), if (testMode == Prefs.TEST_TCP) 1 else 0, onSelect = { i ->
                        Prefs.setLatencyMode(if (i == 1) Prefs.TEST_TCP else Prefs.TEST_URL)
                    })
                    Text(
                        if (testMode == Prefs.TEST_TCP) {
                            "TCP 测试只测到服务器的握手时间，更快但不代表节点可用。Hysteria2、TUIC 等 UDP 协议仍使用 URL 测试。"
                        } else {
                            "通过节点实际访问测试地址，结果最接近真实体验。"
                        },
                        style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                RowDivider()
                NavRow("测试地址", "URL 测试访问的地址", testUrlLabel(s.testUrl)) { edit = "testUrl" }
                RowDivider()
                NavRow("超时时间", "超过这个时间算作失败", "${s.testTimeout} 毫秒") { edit = "testTimeout" }
                RowDivider()
                NavRow("同时测试数", "一次并行测试的节点数", s.testConcurrency.toString()) { edit = "testConcurrency" }
            }

            SettingsGroup("性能") {
                SwitchRow("FakeIP", "代理的域名立即返回虚拟地址，省去一次经代理的 DNS 查询，打开网页更快", fakeIp, { on ->
                    Prefs.setFakeIp(on)
                    dev.efah.nextbox.bg.Box.reloadIfRunning()
                })
                RowDivider()
                SwitchRow("显示连接所属应用", "「活动」页显示每个连接是哪个应用发起的，每个连接都要多查询一次，略增耗电", showApps, { on ->
                    Prefs.setShowApps(on)
                    dev.efah.nextbox.bg.Box.reloadIfRunning()
                })
            }

            SettingsGroup("网络") {
                NavRow("远程 DNS", "通过代理查询", s.remoteDns) { edit = "remoteDns" }
                RowDivider()
                NavRow("直连 DNS", "国内域名和直连流量使用", s.directDns) { edit = "directDns" }
                RowDivider()
                NavRow("本地代理端口", "HTTP + SOCKS5 混合端口，仅代理模式或允许局域网连接时开启", s.mixedPort.toString()) { edit = "mixedPort" }
                RowDivider()
                SwitchRow("允许局域网连接", "局域网内其他设备可以使用这台手机的代理端口", s.allowLan, { on -> patch { put("allowLan", on) } })
                RowDivider()
                NavRow("更新规则集", "重新下载当前需要的规则集") {
                    scope.launch { Repo.updateRuleSets().onSuccess { toast(it) }.onFailure { toast(it.message ?: "更新失败") } }
                }
            }

            SettingsGroup("外观") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    val themes = listOf("auto", "light", "dark")
                    Segmented(listOf("跟随系统", "浅色", "深色"), themes.indexOf(s.theme).coerceAtLeast(0), onSelect = { i ->
                        patch(reload = false) { put("theme", themes[i]) }
                    })
                }
                RowDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("主题色", style = NbType.body, color = c.text, modifier = Modifier.weight(1f))
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
                                if (sel) Icon(NbIcons.Check, "已选中", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            SettingsGroup("数据") {
                NavRow("导出备份", "节点、订阅和设置，与 Windows 版通用") { exportLauncher.launch("NextBox-backup.json") }
                RowDivider()
                NavRow("从备份恢复", "也可以直接选择 Windows 版的 profiles.json") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }

            SettingsGroup("关于") {
                NavRow("NextBox", "基于 NekoBox / sing-box 的代理客户端", BuildConfig.VERSION_NAME) {}
                RowDivider()
                NavRow("sing-box 内核", "with_gvisor · with_quic · with_utls", runCatching { Libbox.version() }.getOrDefault("")) {}
                RowDivider()
                NavRow("源代码", "GPL-3.0 开源，欢迎反馈问题", "GitHub") {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Efah-Dev/NextBox-Android")))
                    }
                }
            }
        }
    }

    when (edit) {
        "remoteDns" -> TextDialog("远程 DNS", s.remoteDns, "例如 https://1.1.1.1/dns-query", { edit = null }) { v ->
            edit = null
            patch { put("remoteDns", v) }
        }
        "directDns" -> TextDialog("直连 DNS", s.directDns, "例如 223.5.5.5", { edit = null }) { v ->
            edit = null
            patch { put("directDns", v) }
        }
        "mixedPort" -> TextDialog("本地代理端口", s.mixedPort.toString(), "1024 - 65535", { edit = null }, number = true) { v ->
            edit = null
            val port = v.toIntOrNull()
            if (port == null || port !in 1024..65535) toast("端口无效") else patch { put("mixedPort", port) }
        }
        "testUrl" -> TestUrlDialog(s.testUrl, { edit = null }) { v ->
            edit = null
            patch(reload = false) { put("testUrl", v) }
        }
        "testTimeout" -> TextDialog("超时时间（毫秒）", s.testTimeout.toString(), "1000 - 30000", { edit = null }, number = true) { v ->
            edit = null
            val ms = v.toIntOrNull()
            if (ms == null || ms !in 1000..30000) toast("请输入 1000 到 30000") else patch(reload = false) { put("testTimeout", ms) }
        }
        "testConcurrency" -> TextDialog("同时测试数", s.testConcurrency.toString(), "1 - 64", { edit = null }, number = true) { v ->
            edit = null
            val n = v.toIntOrNull()
            if (n == null || n !in 1..64) toast("请输入 1 到 64") else patch(reload = false) { put("testConcurrency", n) }
        }
        "billingDay" -> TextDialog("每月结算日", billingDay.toString(), "1 - 28", { edit = null }, number = true) { v ->
            edit = null
            val d = v.toIntOrNull()
            if (d == null || d !in 1..28) toast("请输入 1 到 28") else Prefs.setBillingDay(d)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("测试地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (testUrls.map { it.first to it.second } + ("custom" to "")).forEach { (name, url) ->
                    Row(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable { choice = name }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = choice == name, onClick = { choice = name })
                        Column {
                            Text(if (name == "custom") "自定义" else name, style = NbType.body, color = c.text)
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
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                v, { v = it }, singleLine = true, placeholder = { Text(hint) },
                keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(v.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
