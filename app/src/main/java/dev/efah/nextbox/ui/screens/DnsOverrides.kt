package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.efah.nextbox.data.DnsOverride
import dev.efah.nextbox.data.NbJson
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.NavRow
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject

/**
 * DNS 复写 (Settings.dnsOverrides): some domains get their addresses from a
 * DNS server of their own (an airport's DNS that unlocks streaming sites) or
 * fixed addresses (like a hosts file), before the remote and direct DNS.
 * Shared with NextBox for Windows through backups.
 */
@Composable
fun DnsOverridesRow(list: List<DnsOverride>) {
    var open by remember { mutableStateOf(false) }
    NavRow(T("DNS 复写"), T("指定的域名改用别的 DNS 服务器解析，或者直接指定 IP"), if (list.isEmpty()) T("未设置") else T("%d 条", list.size)) { open = true }
    if (open) DnsOverridesDialog(list) { open = false }
}

private fun split(s: String) = s.split(Regex("[\\s,，、;；]+")).map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun DnsOverridesDialog(initial: List<DnsOverride>, onDismiss: () -> Unit) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    var list by remember { mutableStateOf(initial) }
    var editing by remember { mutableIntStateOf(-2) } // -2 none, -1 new, else index
    fun save(next: List<DnsOverride>) {
        list = next
        scope.launch {
            val js = NbJson.encodeToJsonElement(ListSerializer(DnsOverride.serializer()), next)
            Repo.updateSettings(buildJsonObject { put("dnsOverrides", js) }).onFailure { toast(it.message ?: T("保存失败")) }
        }
    }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T("DNS 复写")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(T("优先于远程 / 直连 DNS。机场提供的解锁 DNS 一般需要「经代理查询」。"), style = NbType.small, color = c.muted)
                if (list.isEmpty()) {
                    Text(T("还没有 DNS 复写"), style = NbType.body, color = c.muted, modifier = Modifier.padding(vertical = 16.dp))
                }
                list.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable { editing = i }.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(o.domains.joinToString(", "), style = NbType.bodyBold, color = if (o.disabled) c.muted else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val to = if (o.ips.isNotEmpty()) "IP · " + o.ips.joinToString(", ") else "DNS · " + o.server + if (o.viaProxy) T(" · 经代理") else ""
                            Text(to + if (o.disabled) T(" · 已停用") else "", style = NbType.small, color = c.muted, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { save(list.filterIndexed { j, _ -> j != i }) }) {
                            Icon(NbIcons.Trash, T("删除"), tint = c.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T("完成")) } },
        dismissButton = { TextButton(onClick = { editing = -1 }) { Text(T("添加")) } },
    )
    if (editing != -2) {
        DnsOverrideEditor(list.getOrNull(editing), { editing = -2 }) { o ->
            save(if (editing < 0) list + o else list.mapIndexed { j, x -> if (j == editing) o else x })
            editing = -2
        }
    }
}

@Composable
private fun DnsOverrideEditor(initial: DnsOverride?, onDismiss: () -> Unit, onSave: (DnsOverride) -> Unit) {
    val c = Nb.c
    var kind by remember { mutableIntStateOf(if (initial?.ips?.isNotEmpty() == true) 1 else 0) }
    var domains by remember { mutableStateOf(initial?.domains?.joinToString("\n").orEmpty()) }
    var server by remember { mutableStateOf(initial?.server.orEmpty()) }
    var viaProxy by remember { mutableStateOf(initial?.viaProxy ?: true) }
    var ips by remember { mutableStateOf(initial?.ips?.joinToString("\n").orEmpty()) }
    val problem = when {
        split(domains).isEmpty() -> T("至少填一个域名")
        kind == 0 && server.isBlank() -> T("填写 DNS 服务器地址")
        kind == 1 && split(ips).isEmpty() -> T("至少填一个 IP")
        else -> ""
    }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) T("添加 DNS 复写") else T("编辑 DNS 复写")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(listOf(T("用指定的 DNS 服务器"), T("固定 IP")), kind, onSelect = { kind = it })
                OutlinedTextField(
                    domains, { domains = it }, minLines = 2,
                    label = { Text(if (kind == 0) T("域名（含子域名）") else T("完整域名")) },
                    placeholder = { Text(if (kind == 0) "netflix.com" else "nas.home.arpa") },
                    supportingText = { Text(T("每行一个，也可以用逗号分开")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == 0) {
                    OutlinedTextField(server, { server = it }, singleLine = true, label = { Text(T("DNS 服务器")) }, placeholder = { Text("10.8.0.53") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(T("经代理查询"), style = NbType.body, color = c.text)
                            Text(T("机场提供的解锁 DNS 一般需要打开"), style = NbType.small, color = c.muted)
                        }
                        Switch(
                            viaProxy, { viaProxy = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accentStrong, checkedThumbColor = Color.White),
                        )
                    }
                } else {
                    OutlinedTextField(ips, { ips = it }, minLines = 1, label = { Text("IP") }, placeholder = { Text("192.168.1.20") }, modifier = Modifier.fillMaxWidth())
                }
                if (problem.isNotEmpty()) Text(problem, style = NbType.small, color = c.muted)
            }
        },
        confirmButton = {
            TextButton(enabled = problem.isEmpty(), onClick = {
                val base = initial ?: DnsOverride()
                onSave(
                    if (kind == 0) base.copy(domains = split(domains), server = server.trim(), viaProxy = viaProxy, ips = emptyList())
                    else base.copy(domains = split(domains), ips = split(ips), server = "", viaProxy = false),
                )
            }) { Text(T("保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T("取消")) } },
    )
}

/**
 * 订阅专属 DNS: the DNS a subscription names (found when it is added or
 * updated) is used for its own nodes. A switch per subscription; 编辑 for a
 * DNS of one's own, which few people need.
 */
@Composable
fun SubscriptionDnsRow(groups: List<dev.efah.nextbox.data.Group>) {
    var open by remember { mutableStateOf(false) }
    val subs = groups.filter { it.isSubscription }
    val inUse = subs.count { it.dns?.isSet == true }
    NavRow(T("订阅专属 DNS"), T("按订阅里的配置，只用于它的节点"), if (inUse > 0) T("%d 个在用", inUse) else T("未使用")) { open = true }
    if (open) SubscriptionDnsDialog(subs) { open = false }
}

@Composable
private fun SubscriptionDnsDialog(subs: List<dev.efah.nextbox.data.Group>, onDismiss: () -> Unit) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    var editing by remember { mutableStateOf<dev.efah.nextbox.data.Group?>(null) }
    fun addrs(d: dev.efah.nextbox.data.GroupDns) = listOfNotNull(
        d.remote.takeIf { it.isNotEmpty() }?.let { T("远程 %s", it) },
        d.resolver.takeIf { it.isNotEmpty() }?.let { T("节点域名 %s", it) },
    )
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T("订阅专属 DNS")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(T("添加或更新订阅时会自动识别。只用于这个订阅的节点，不影响其他订阅。"), style = NbType.small, color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
                if (subs.isEmpty()) Text(T("还没有添加订阅"), style = NbType.body, color = c.muted, modifier = Modifier.padding(vertical = 12.dp))
                subs.forEach { g ->
                    val using = g.dns?.isSet == true
                    val offered = g.suggestedDns?.isSet == true
                    val (state, lines) = when {
                        using -> (if (g.clash?.subDns == true) T("使用订阅自带的 DNS") else T("手动设置")) to addrs(g.dns!!)
                        offered -> T("订阅提供了 DNS，未使用") to addrs(g.suggestedDns!!)
                        else -> T("订阅没有提供，使用全局 DNS") to emptyList()
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.displayName, style = NbType.bodyBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(state, style = NbType.small, color = if (using) c.accentInk else c.muted)
                            lines.forEach { Text(it, style = NbType.small, color = c.muted, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        TextButton(onClick = { editing = g }) { Text(T("编辑")) }
                        if (using || offered) {
                            Switch(
                                using, { on ->
                                    if (on && !offered) editing = g
                                    else scope.launch { Repo.useSubscriptionDns(g.id, on).onFailure { toast(it.message ?: T("保存失败")) } }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accentStrong, checkedThumbColor = Color.White),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T("完成")) } },
    )
    editing?.let { g ->
        TwoFieldDialog(
            title = T("订阅专属 DNS：%s", g.displayName), first = T("专属远程 DNS（留空用全局的远程 DNS）"), second = T("节点域名解析 DNS（留空用直连 DNS）"),
            initialFirst = g.dns?.remote.orEmpty(), initialSecond = g.dns?.resolver.orEmpty(), confirm = T("保存"), onDismiss = { editing = null },
            note = T("只用于这个订阅的节点：连接它的节点时代替远程 DNS，并用来解析它的节点地址。手动设置后，更新订阅不会再覆盖。"),
        ) { remote, resolver ->
            editing = null
            scope.launch { Repo.setGroupDns(g.id, remote, resolver).onFailure { toast(it.message ?: T("保存失败")) } }
        }
    }
}
