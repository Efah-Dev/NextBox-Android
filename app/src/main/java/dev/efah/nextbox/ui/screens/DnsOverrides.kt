package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.efah.nextbox.data.DnsOverride
import dev.efah.nextbox.data.Group
import dev.efah.nextbox.data.GroupDns
import dev.efah.nextbox.data.NbJson
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NavRow
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.NbLabeledField
import dev.efah.nextbox.ui.NbSwitch
import dev.efah.nextbox.ui.NbTextButton
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
    NavRow(T("DNS 复写"), T("指定域名改用别的 DNS 或固定 IP"), if (list.isEmpty()) T("未设置") else T("%d 条", list.size)) { open = true }
    if (open) DnsOverridesSheet(list) { open = false }
}

private fun split(s: String) = s.split(Regex("[\\s,，、;；]+")).map { it.trim() }.filter { it.isNotEmpty() }

/** Title and a line below it, the top of every sheet here. */
@Composable
private fun SheetHead(title: String, note: String) {
    val c = Nb.c
    Text(title, style = NbType.big, color = c.text)
    Text(note, style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
}

/** The two buttons at the bottom of a sheet, side by side. */
@Composable
private fun SheetButtons(secondary: String, onSecondary: () -> Unit, primary: String, onPrimary: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NbButton(secondary, onSecondary, Modifier.weight(1f), kind = BtnKind.Secondary)
        NbButton(primary, onPrimary, Modifier.weight(1f))
    }
}

/** The list, and in its place the editor of one entry (index -1 for a new one). */
@Composable
private fun DnsOverridesSheet(initial: List<DnsOverride>, onDismiss: () -> Unit) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    var list by remember { mutableStateOf(initial) }
    var editing by remember { mutableIntStateOf(-2) } // -2 the list, -1 a new entry, else its index
    fun save(next: List<DnsOverride>) {
        list = next
        scope.launch {
            val js = NbJson.encodeToJsonElement(ListSerializer(DnsOverride.serializer()), next)
            Repo.updateSettings(buildJsonObject { put("dnsOverrides", js) }).onFailure { toast.error(it.message ?: T("保存失败")) }
        }
    }
    NbSheet(onDismiss) {
        if (editing != -2) {
            DnsOverrideEditor(list.getOrNull(editing), { editing = -2 }) { o ->
                save(if (editing < 0) list + o else list.mapIndexed { j, x -> if (j == editing) o else x })
                editing = -2
            }
            return@NbSheet
        }
        SheetHead(T("DNS 复写"), T("优先于远程 / 直连 DNS。机场提供的解锁 DNS 一般需要「经代理查询」。"))
        if (list.isEmpty()) {
            Text(T("还没有 DNS 复写"), style = NbType.body, color = c.muted, modifier = Modifier.padding(vertical = 16.dp))
        }
        list.forEachIndexed { i, o ->
            if (i > 0) HorizontalDivider(color = c.line, thickness = 0.5.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).clickable { editing = i }.padding(start = 4.dp),
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
        SheetButtons(T("添加"), { editing = -1 }, T("完成"), onDismiss)
    }
}

@Composable
private fun ColumnScope.DnsOverrideEditor(initial: DnsOverride?, onCancel: () -> Unit, onSave: (DnsOverride) -> Unit) {
    var kind by remember { mutableIntStateOf(if (initial?.ips?.isNotEmpty() == true) 1 else 0) }
    var domains by remember { mutableStateOf(initial?.domains?.joinToString("\n").orEmpty()) }
    var server by remember { mutableStateOf(initial?.server.orEmpty()) }
    var viaProxy by remember { mutableStateOf(initial?.viaProxy ?: true) }
    var ips by remember { mutableStateOf(initial?.ips?.joinToString("\n").orEmpty()) }
    // problems show once 保存 was tapped, under the field they are about
    var tried by remember { mutableStateOf(false) }
    val domainError = if (tried && split(domains).isEmpty()) T("至少填一个域名") else ""
    val serverError = if (tried && kind == 0 && server.isBlank()) T("填写 DNS 服务器地址") else ""
    val ipError = if (tried && kind == 1 && split(ips).isEmpty()) T("至少填一个 IP") else ""

    Text(if (initial == null) T("添加 DNS 复写") else T("编辑 DNS 复写"), style = NbType.big, color = Nb.c.text, modifier = Modifier.padding(bottom = 14.dp))
    Segmented(listOf(T("DNS 服务器"), T("固定 IP")), kind, onSelect = { kind = it }, height = 40.dp)
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NbLabeledField(
            if (kind == 0) T("域名（含子域名）") else T("完整域名"), domains, { domains = it },
            placeholder = if (kind == 0) "netflix.com" else "nas.home.arpa", hint = T("每行一个，也可以用逗号分开"),
            error = domainError, singleLine = false, minLines = 2,
        )
        if (kind == 0) {
            NbLabeledField(T("DNS 服务器"), server, { server = it }, placeholder = "10.8.0.53", error = serverError, mono = true)
        } else {
            NbLabeledField("IP", ips, { ips = it }, placeholder = "192.168.1.20", hint = T("每行一个，也可以用逗号分开"), error = ipError, singleLine = false, mono = true)
        }
    }
    if (kind == 0) {
        // lined up with the fields (a SwitchRow is indented for cards)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable { viaProxy = !viaProxy }.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(T("经代理查询"), style = NbType.body, color = Nb.c.text)
                Text(T("机场提供的解锁 DNS 一般需要打开"), style = NbType.small, color = Nb.c.muted)
            }
            NbSwitch(viaProxy, { viaProxy = it })
        }
    }
    SheetButtons(T("取消"), onCancel, T("保存")) {
        tried = true
        val ok = split(domains).isNotEmpty() && if (kind == 0) server.isNotBlank() else split(ips).isNotEmpty()
        if (!ok) return@SheetButtons
        val base = initial ?: DnsOverride()
        onSave(
            if (kind == 0) base.copy(domains = split(domains), server = server.trim(), viaProxy = viaProxy, ips = emptyList())
            else base.copy(domains = split(domains), ips = split(ips), server = "", viaProxy = false),
        )
    }
}

/**
 * 订阅专属 DNS: the DNS a subscription names (found when it is added or
 * updated) is used for its own nodes. A switch per subscription; 编辑 for a
 * DNS of one's own, which few people need.
 */
@Composable
fun SubscriptionDnsRow(groups: List<Group>) {
    var open by remember { mutableStateOf(false) }
    val subs = groups.filter { it.isSubscription }
    val inUse = subs.count { it.dns?.isSet == true }
    NavRow(T("订阅专属 DNS"), T("按订阅里的配置，只用于它的节点"), if (inUse > 0) T("%d 个在用", inUse) else T("未使用")) { open = true }
    if (open) SubscriptionDnsSheet(subs) { open = false }
}

@Composable
private fun SubscriptionDnsSheet(subs: List<Group>, onDismiss: () -> Unit) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    var editing by remember { mutableStateOf<Group?>(null) }
    fun addrs(d: GroupDns) = listOfNotNull(
        d.remote.takeIf { it.isNotEmpty() }?.let { T("远程 %s", it) },
        d.resolver.takeIf { it.isNotEmpty() }?.let { T("节点域名 %s", it) },
    )
    NbSheet(onDismiss) {
        editing?.let { g ->
            SubscriptionDnsEditor(g, { editing = null }) { remote, resolver ->
                editing = null
                scope.launch { Repo.setGroupDns(g.id, remote, resolver).onFailure { toast.error(it.message ?: T("保存失败")) } }
            }
            return@NbSheet
        }
        SheetHead(T("订阅专属 DNS"), T("添加或更新订阅时会自动识别。只用于这个订阅的节点，不影响其他订阅。"))
        if (subs.isEmpty()) Text(T("还没有添加订阅"), style = NbType.body, color = c.muted, modifier = Modifier.padding(vertical = 12.dp))
        subs.forEachIndexed { i, g ->
            if (i > 0) HorizontalDivider(color = c.line, thickness = 0.5.dp)
            val using = g.dns?.isSet == true
            val offered = g.suggestedDns?.isSet == true
            val (state, lines) = when {
                using -> (if (g.clash?.subDns == true) T("使用订阅自带的 DNS") else T("手动设置")) to addrs(g.dns)
                offered -> T("订阅提供了 DNS，未使用") to addrs(g.suggestedDns)
                else -> T("订阅没有提供，使用全局 DNS") to emptyList()
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.displayName, style = NbType.bodyBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state, style = NbType.small, color = if (using) c.accentInk else c.muted)
                    lines.forEach { Text(it, style = NbType.small, color = c.muted, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                NbTextButton(T("编辑"), { editing = g })
                if (using || offered) {
                    NbSwitch(using, { on ->
                        if (on && !offered) editing = g
                        else scope.launch { Repo.useSubscriptionDns(g.id, on).onFailure { toast.error(it.message ?: T("保存失败")) } }
                    })
                }
            }
        }
        NbButton(T("完成"), onDismiss, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

@Composable
private fun SubscriptionDnsEditor(g: Group, onCancel: () -> Unit, onSave: (remote: String, resolver: String) -> Unit) {
    var remote by remember { mutableStateOf(g.dns?.remote.orEmpty()) }
    var resolver by remember { mutableStateOf(g.dns?.resolver.orEmpty()) }
    SheetHead(T("订阅专属 DNS：%s", g.displayName), T("只用于这个订阅的节点：连接它的节点时代替远程 DNS，并用来解析它的节点地址。手动设置后，更新订阅不会再覆盖。"))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NbLabeledField(T("专属远程 DNS"), remote, { remote = it }, placeholder = "https://1.1.1.1/dns-query", hint = T("留空用全局的远程 DNS"), mono = true)
        NbLabeledField(T("节点域名解析 DNS"), resolver, { resolver = it }, placeholder = "223.5.5.5", hint = T("留空用直连 DNS"), mono = true)
    }
    SheetButtons(T("取消"), onCancel, T("保存")) { onSave(remote.trim(), resolver.trim()) }
}
