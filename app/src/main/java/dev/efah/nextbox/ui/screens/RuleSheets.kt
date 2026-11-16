package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.Rule
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.LocalGlass
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.WindowBlurBehind
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch

/** A bottom sheet with the app's surface and, with glass effects on, a blurred background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NbSheet(onDismiss: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = Nb.c
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (LocalGlass.current) c.card.copy(alpha = if (c.dark) 0.9f else 0.94f) else c.card,
        tonalElevation = 0.dp,
    ) {
        WindowBlurBehind()
        Column(
            Modifier.fillMaxWidth().imePadding()
                .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            content = content,
        )
    }
}

/**
 * The height of a list inside a sheet: a share of the screen, fixed. A list
 * that takes "the space left" makes the sheet's height depend on where the
 * sheet is, and the two chase each other when the sheet is pulled (it
 * shakes, or keeps moving); the sheet scrolls instead when it is too tall.
 */
@Composable
fun sheetListHeight(share: Float, max: Dp): Dp =
    (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * share).dp.coerceIn(160.dp, max)

/** What the add sheet starts with: which tab, prefilled text or apps, and the target. */
data class RuleDraft(val tab: Int = 0, val text: String = "", val apps: Set<String> = emptySet(), val target: String = "direct")

private val packageName = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
private val ipOrCidr = Regex("^[0-9a-fA-F:.]+(/\\d{1,3})?$")

/** "https://www.example.com/a?b" → "www.example.com". */
fun hostOf(text: String): String {
    var h = text.trim().lowercase()
    h = h.substringAfter("://", h)
    h = h.substringBefore('/').substringBefore('?').substringBefore('#')
    h = h.substringAfter('@')
    if (h.startsWith("[")) return h.substringAfter('[').substringBefore(']')
    if (h.count { it == ':' } == 1) h = h.substringBefore(':')
    return h.trim('.')
}

private val secondLevel = setOf("com", "net", "org", "gov", "edu", "co", "ac", "or", "ne", "go")

/** The registrable domain of a host: "rr3.sn.googlevideo.com" → "googlevideo.com", "www.bbc.co.uk" → "bbc.co.uk". */
fun mainDomain(host: String): String {
    val parts = host.split('.')
    if (parts.size <= 2 || host.all { it.isDigit() || it == '.' || it == ':' }) return host
    val keep = if (parts[parts.size - 2] in secondLevel && parts.last().length == 2) 3 else 2
    return parts.takeLast(keep).joinToString(".")
}

/**
 * Adds rules: websites (common services, or domains typed or pasted one per
 * line), apps (searchable list or a package name) or IP addresses, and where
 * they go. Values already sent elsewhere are moved after asking.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRuleSheet(draft: RuleDraft, onDismiss: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    var tab by remember { mutableIntStateOf(draft.tab) }
    var services by remember { mutableStateOf(emptySet<String>()) }
    var siteText by remember { mutableStateOf(if (draft.tab == 0) draft.text else "") }
    var siteKind by remember { mutableStateOf("suffix") }
    var apps by remember { mutableStateOf(draft.apps) }
    var ipText by remember { mutableStateOf(if (draft.tab == 2) draft.text else "") }
    var countries by remember { mutableStateOf(emptySet<String>()) }
    var target by remember { mutableStateOf(draft.target) }
    var manual by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<List<Pair<String, List<String>>>, Map<String, String>>?>(null) }
    LaunchedEffect(Unit) { Repo.loadPolicies() }

    fun lines(text: String) = text.split('\n', ',', '，', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }

    /** What would be added, as (kind, values). */
    fun changes(): List<Pair<String, List<String>>> = when (tab) {
        0 -> buildList {
            val chosen = RuleUi.services.filter { it.id in services }
            chosen.flatMap { it.geosite }.takeIf { it.isNotEmpty() }?.let { add("geosite" to it) }
            chosen.flatMap { it.cidrs }.takeIf { it.isNotEmpty() }?.let { add("cidr" to it) }
            val typed = lines(siteText).map { if (siteKind == "regex") it else hostOf(it) }.filter { it.isNotEmpty() }.distinct()
            if (typed.isNotEmpty()) add(siteKind to typed)
        }
        1 -> if (apps.isEmpty()) emptyList() else listOf("package" to apps.toList())
        else -> buildList {
            val typed = lines(ipText).filter { ipOrCidr.matches(it) }
            if (typed.isNotEmpty()) add("cidr" to typed)
            if (countries.isNotEmpty()) add("geoip" to countries.toList())
        }
    }
    val pending = changes()
    val total = when (tab) {
        0 -> services.size + lines(siteText).size
        1 -> apps.size
        else -> lines(ipText).count { ipOrCidr.matches(it) } + countries.size
    }

    fun apply(list: List<Pair<String, List<String>>>) {
        var next: List<Rule> = state.settings.rules
        list.forEach { (kind, values) -> next = RuleUi.place(next, kind, values, target) }
        onDismiss()
        scope.launch {
            Repo.saveRules(next)
                .onSuccess { toast(T("已添加，走「%s」", RuleUi.targetLabel(target, policies))) }
                .onFailure { toast(it.message ?: T("保存失败")) }
        }
    }

    fun submit() {
        if (pending.isEmpty()) {
            toast(
                when (tab) {
                    0 -> T("请选择常用服务，或者输入域名")
                    1 -> T("请选择应用")
                    else -> T("请输入 IP 地址或 IP 段，例如 10.0.0.0/8")
                },
            )
            return
        }
        val clash = pending.fold(emptyMap<String, String>()) { acc, (kind, values) -> acc + RuleUi.conflicts(state.settings.rules, kind, values, target) }
        if (clash.isEmpty()) apply(pending) else confirm = pending to clash
    }

    NbSheet(onDismiss) {
        Text(T("添加规则"), style = NbType.big, color = c.text, modifier = Modifier.padding(bottom = 12.dp))
        Segmented(listOf(T("网站"), T("应用"), T("IP")), tab, { tab = it }, height = 38.dp)
        Spacer(Modifier.height(14.dp))
        when (tab) {
            0 -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(T("常用服务"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
                    Text(T("点一下加入整组域名"), style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleUi.services.forEach { s ->
                        Chip(T(s.label), s.id in services, onClick = { services = if (s.id in services) services - s.id else services + s.id })
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    siteText, { siteText = it }, minLines = 3, maxLines = 6,
                    label = { Text(T("或者输入域名，一行一个")) },
                    placeholder = { Text("example.com\nnews.example.org") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(T("匹配方式"), style = NbType.body, color = c.text, modifier = Modifier.weight(1f))
                    Box {
                        var open by remember { mutableStateOf(false) }
                        TextButton(onClick = { open = true }) { Text(RuleUi.siteKindLabel(siteKind), color = c.accentInk) }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            RuleUi.siteKinds.forEach { k -> DropdownMenuItem(text = { Text(RuleUi.siteKindLabel(k)) }, onClick = { open = false; siteKind = k }) }
                        }
                    }
                }
                Text(
                    when (siteKind) {
                        "suffix" -> T("example.com 也会匹配 www.example.com、api.example.com")
                        "domain" -> T("只匹配完全相同的域名")
                        "keyword" -> T("域名里包含这段文字就匹配")
                        else -> T("用正则表达式匹配域名")
                    },
                    style = NbType.small, color = c.muted,
                )
            }
            1 -> {
                // a fixed height (see sheetListHeight): when many targets below
                // make the sheet too tall, the sheet scrolls to the buttons
                Box(Modifier.fillMaxWidth().height(sheetListHeight(0.42f, 380.dp))) {
                    AppChooser(selected = apps, onChange = { apps = it }, showBulkActions = false, padding = PaddingValues(0.dp))
                }
                TextButton(onClick = { manual = true }) { Text(T("找不到？手动输入包名"), color = c.accentInk) }
            }
            else -> {
                OutlinedTextField(
                    ipText, { ipText = it }, minLines = 3, maxLines = 6,
                    label = { Text(T("IP 地址或 IP 段，一行一个")) },
                    placeholder = { Text("10.0.0.0/8\n1.1.1.1") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(T("按 IP 所在地区"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleUi.geoips.forEach { (code, name) ->
                        Chip(T(name), code in countries, onClick = { countries = if (code in countries) countries - code else countries + code })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(T("走哪里"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
        Spacer(Modifier.height(8.dp))
        TargetChips(target, policies) { target = it }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text(T("取消")) }
            Button(
                onClick = ::submit,
                colors = ButtonDefaults.buttonColors(containerColor = c.accentStrong),
                modifier = Modifier.weight(2f).height(48.dp),
            ) { Text(if (total > 0) T("添加 %d 项", total) else T("添加"), fontWeight = FontWeight.Bold) }
        }
    }

    if (manual) {
        var pkg by remember { mutableStateOf("") }
        NbAlertDialog(
            onDismissRequest = { manual = false },
            title = { Text(T("手动输入包名")) },
            text = {
                OutlinedTextField(
                    pkg, { pkg = it }, singleLine = true, placeholder = { Text("com.example.app") },
                    supportingText = { Text(T("没安装的应用也可以先加上")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {}),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = pkg.trim()
                    if (!packageName.matches(v)) {
                        toast(T("包名格式不对，例如 com.example.app"))
                    } else {
                        apps = apps + v
                        manual = false
                    }
                }) { Text(T("添加")) }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text(T("取消")) } },
        )
    }

    confirm?.let { (list, clash) ->
        NbAlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(T("移到「%s」？", RuleUi.targetLabel(target, policies))) },
            text = {
                Text(
                    T("这些已经有规则了：") + "\n" + clash.entries.take(6).joinToString("\n") { (v, t) ->
                        "· " + (RuleUi.services.firstOrNull { v in it.geosite }?.let { T(it.label) } ?: v) + " → " + RuleUi.targetLabel(t, policies)
                    } + if (clash.size > 6) "\n…" else "",
                )
            },
            confirmButton = { TextButton(onClick = { confirm = null; apply(list) }) { Text(T("移过来")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(T("取消")) } },
        )
    }
}

/**
 * Quick rules for one connection of the activity page: send its website or
 * its app somewhere, or see which step of the route path it takes.
 */
@Composable
fun ConnectionRuleSheet(host: String, pkg: String, appLabel: String, proxied: Boolean, onShowPath: () -> Unit, onDisconnect: () -> Unit, onDismiss: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    val isIp = host.all { it.isDigit() || it == '.' || it == ':' }
    val main = mainDomain(host)
    var byMain by remember { mutableStateOf(!isIp && main != host) }
    var pickPolicy by remember { mutableStateOf<String?>(null) } // "site" or "app"
    LaunchedEffect(Unit) { Repo.loadPolicies() }

    fun add(kind: String, value: String, target: String, what: String) {
        onDismiss()
        scope.launch {
            Repo.saveRules(RuleUi.place(state.settings.rules, kind, listOf(value), target))
                .onSuccess { toast(T("已添加：%s 走「%s」", what, RuleUi.targetLabel(target, policies))) }
                .onFailure { toast(it.message ?: T("保存失败")) }
        }
    }
    val siteKind = if (isIp) "cidr" else if (byMain) "suffix" else "domain"
    val siteValue = if (byMain) main else host

    NbSheet(onDismiss) {
        Text(host, style = NbType.big.copy(fontSize = 18.sp), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            (if (pkg.isNotEmpty()) T("来自 %s", appLabel) + " · " else "") + if (proxied) T("现在走代理") else T("现在直连"),
            style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        if (!isIp && main != host) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { byMain = !byMain }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(T("按主域名添加"), style = NbType.body, color = c.text)
                    Text(T("%s 及所有子域名", main), style = NbType.small, color = c.muted)
                }
                Checkbox(checked = byMain, onCheckedChange = { byMain = it }, colors = CheckboxDefaults.colors(checkedColor = c.accentStrong))
            }
        }
        Spacer(Modifier.height(8.dp))
        ActionCard {
            if (proxied) Action(T("这个网站走直连"), RuleUi.targetColors("direct", c).second) { add(siteKind, siteValue, "direct", siteValue) }
            else Action(T("这个网站走代理"), RuleUi.targetColors("proxy", c).second) { add(siteKind, siteValue, "proxy", siteValue) }
            if (policies.isNotEmpty()) Action(T("这个网站走策略组…"), RuleUi.targetColors("policy:0", c).second, divider = true) { pickPolicy = "site" }
            Action(T("屏蔽这个网站"), c.bad, divider = true, color = c.bad) { add(siteKind, siteValue, "block", siteValue) }
        }
        if (pkg.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ActionCard {
                if (proxied) Action(T("%s 整个应用走直连", appLabel), RuleUi.targetColors("direct", c).second) { add("package", pkg, "direct", appLabel) }
                else Action(T("%s 整个应用走代理", appLabel), RuleUi.targetColors("proxy", c).second) { add("package", pkg, "proxy", appLabel) }
                if (policies.isNotEmpty()) Action(T("%s 整个应用走策略组…", appLabel), RuleUi.targetColors("policy:0", c).second, divider = true) { pickPolicy = "app" }
            }
        }
        Spacer(Modifier.height(10.dp))
        ActionCard {
            Action(T("查看它走哪条路"), c.accentInk) { onDismiss(); onShowPath() }
            Action(T("断开这个连接"), c.muted, divider = true) { onDismiss(); onDisconnect() }
        }
    }

    pickPolicy?.let { what ->
        NbAlertDialog(
            onDismissRequest = { pickPolicy = null },
            title = { Text(T("选择策略组")) },
            text = {
                Column {
                    policies.forEach { p ->
                        Text(
                            p.name, style = NbType.body, color = c.text,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                pickPolicy = null
                                if (what == "app") add("package", pkg, "policy:${p.id}", appLabel) else add(siteKind, siteValue, "policy:${p.id}", siteValue)
                            }.padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickPolicy = null }) { Text(T("取消")) } },
        )
    }
}

@Composable
private fun ActionCard(content: @Composable ColumnScope.() -> Unit) {
    dev.efah.nextbox.ui.NbCard(padding = PaddingValues(0.dp), radius = 18.dp, content = content)
}

@Composable
private fun Action(label: String, dot: androidx.compose.ui.graphics.Color, divider: Boolean = false, color: androidx.compose.ui.graphics.Color = Nb.c.text, onClick: () -> Unit) {
    if (divider) HorizontalDivider(color = Nb.c.line, thickness = 1.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(dot))
        Text(label, style = NbType.body, color = color, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
