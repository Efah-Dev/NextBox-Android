package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.PolicyView
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.Rule
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The steps of the route path, in the order traffic meets them (see nbcore route.go). */
object RouteSteps {
    const val ADS = "ads"
    const val LAN = "lan"
    const val BLOCK = "block"
    const val DIRECT = "direct"
    const val POLICY = "policy"
    const val PROXY = "proxy"
    const val CN = "cn"
    const val FINAL = "final"
    val all = listOf(ADS, LAN, BLOCK, DIRECT, POLICY, PROXY, CN, FINAL)

    fun title(step: String): String = when (step) {
        ADS -> T("屏蔽广告")
        LAN -> T("局域网和内网")
        BLOCK -> T("屏蔽规则")
        DIRECT -> T("直连规则")
        POLICY -> T("策略组规则")
        PROXY -> T("代理规则")
        CN -> T("绕过中国大陆")
        else -> T("其余所有连接")
    }

    /** Which rules belong to a rule step. */
    fun matches(step: String, target: String): Boolean = when (step) {
        BLOCK -> target == "block"
        DIRECT -> target == "direct"
        POLICY -> target.startsWith("policy:")
        PROXY -> target == "proxy"
        else -> false
    }

    /** Whether a step takes part with the current settings. */
    fun active(step: String, directMode: Boolean, blockAds: Boolean, bypassCn: Boolean): Boolean = when (step) {
        ADS -> blockAds
        LAN, FINAL -> true
        CN -> bypassCn && !directMode
        else -> !directMode
    }
}

/** Colours of a step's exit: block, direct, policy group or the node. */
private fun stepTarget(step: String): String = when (step) {
    RouteSteps.ADS, RouteSteps.BLOCK -> "block"
    RouteSteps.LAN, RouteSteps.DIRECT, RouteSteps.CN -> "direct"
    RouteSteps.POLICY -> "policy:0"
    else -> "proxy"
}

/**
 * The rules page: the route path every connection takes, from top to bottom.
 * The home page's scope, ad blocking and "bypass mainland China" switch here
 * too; a rule step opens the list of its rules.
 */
@Composable
fun RulesScreen(onClose: () -> Unit, onOpenList: (String) -> Unit, onTest: () -> Unit, onPerApp: () -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    val routeScope by Prefs.routeScope.collectAsState()
    val appMode by Prefs.perAppMode.collectAsState()
    val apps by Prefs.perAppList.collectAsState()
    val s = state.settings
    val direct = routeScope == Prefs.SCOPE_DIRECT
    val node = remember(state.selectedId) { Repo.selectedNodeName() }.ifEmpty { T("首页选中的节点") }
    val labels by produceState(emptyMap<String, String>()) { value = InstalledApps.load(ctx).associate { it.pkg to it.label } }
    LaunchedEffect(Unit) { Repo.loadPolicies() }
    val scopes = listOf(Prefs.SCOPE_GLOBAL to T("全局代理"), Prefs.SCOPE_APPS to T("指定应用"), Prefs.SCOPE_DIRECT to T("全部直连"))

    GlassScreen(header = {
        TopBar(T("分流规则"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) {
            Row(
                Modifier.padding(end = 8.dp).height(38.dp).clip(RoundedCornerShape(19.dp)).background(c.accentSoft)
                    .clickable(onClick = onTest).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(NbIcons.Search, null, tint = c.accentInk, modifier = Modifier.size(17.dp))
                Text(T("试一试"), color = c.accentInk, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        Segmented(
            scopes.map { it.second }, scopes.indexOfFirst { it.first == routeScope },
            onSelect = { i ->
                val target = scopes[i].first
                scope.launch { Repo.setRoute(scope = target) }
                if (target == Prefs.SCOPE_APPS && apps.isEmpty()) onPerApp()
            },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), height = 38.dp,
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 6.dp, bottom = LocalBottomInset.current + 24.dp),
        ) {
            // where connections come from
            val start = when {
                routeScope != Prefs.SCOPE_APPS -> T("手机上所有应用")
                appMode == Prefs.APPS_EXCLUDE -> T("除了排除的 %d 个应用", apps.size)
                else -> T("选中的 %d 个应用", apps.size)
            }
            Row(Modifier.height(IntrinsicSize.Min)) {
                Rail(dot = { StartDot() }, last = false)
                Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(start, style = NbType.bodyBold, color = c.text)
                    if (routeScope == Prefs.SCOPE_APPS) {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp))
                                .clickable(onClick = onPerApp).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (appMode == Prefs.APPS_EXCLUDE) T("排除的应用不进 VPN，直接用手机网络") else T("其他应用不进 VPN，直接用手机网络"),
                                style = NbType.small, color = c.muted,
                            )
                            Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                        }
                    }
                }
            }

            RouteSteps.all.forEachIndexed { i, step ->
                val on = RouteSteps.active(step, direct, s.blockAds, s.bypassCn)
                val rules = s.rules.filter { RouteSteps.matches(step, it.target) }
                val sub = when {
                    !on && step == RouteSteps.ADS -> T("没开，跳过这一步")
                    !on && step == RouteSteps.CN && !direct -> T("没开，跳过这一步")
                    !on -> T("全部直连时不生效")
                    step == RouteSteps.ADS -> T("常见的广告和跟踪域名")
                    step == RouteSteps.LAN -> T("192.168.x.x、10.x.x.x 等")
                    step == RouteSteps.CN -> T("国内网站和国内 IP")
                    step == RouteSteps.FINAL -> if (direct) T("全部直连模式") else T("上面都没匹配到的")
                    rules.isEmpty() -> T("还没有规则，点这里添加")
                    step == RouteSteps.POLICY -> rules.groupBy { it.target }.entries.joinToString("，") { (t, rs) ->
                        val (a, b, d) = RuleUi.items(rs, t) { labels[it] ?: it }
                        (a + b + d).take(2).joinToString("、") { it.label } + " → " + RuleUi.targetLabel(t, policies)
                    }
                    else -> {
                        val (a, b, d) = RuleUi.items(rules, rules.first().target) { labels[it] ?: it }
                        (a + b + d).take(3).joinToString("、") { it.label } + if (a.size + b.size + d.size > 3) "…" else ""
                    }
                }
                val title = when (step) {
                    RouteSteps.BLOCK, RouteSteps.DIRECT, RouteSteps.POLICY, RouteSteps.PROXY ->
                        RouteSteps.title(step) + " · " + T("%d 条", RuleUi.count(s.rules) { RouteSteps.matches(step, it) })
                    else -> RouteSteps.title(step)
                }
                val exit = when (step) {
                    RouteSteps.ADS, RouteSteps.BLOCK -> T("屏蔽")
                    RouteSteps.LAN, RouteSteps.DIRECT, RouteSteps.CN -> T("直连")
                    RouteSteps.POLICY -> T("策略组")
                    RouteSteps.PROXY -> node
                    else -> if (direct) T("直连") else node
                }
                val toggle: ((Boolean) -> Unit)? = when (step) {
                    RouteSteps.ADS -> { v -> scope.launch { Repo.updateSettings(buildJsonObject { put("blockAds", v) }) } }
                    RouteSteps.CN -> if (direct) null else ({ v -> scope.launch { Repo.setRoute(bypass = v) } })
                    else -> null
                }
                val page = when (step) {
                    RouteSteps.BLOCK -> "block"
                    RouteSteps.DIRECT -> "direct"
                    RouteSteps.POLICY, RouteSteps.PROXY -> "proxy"
                    else -> null
                }
                StepRow(
                    number = i + 1, on = on, last = i == RouteSteps.all.lastIndex,
                    title = title, sub = sub,
                    exit = exit, exitTarget = if (step == RouteSteps.FINAL && direct) "direct" else stepTarget(step),
                    checked = if (step == RouteSteps.ADS) s.blockAds else s.bypassCn, onToggle = toggle,
                    onClick = page?.let { p -> { onOpenList(p) } },
                )
            }
            Text(
                T("从上往下依次判断，在哪一步匹配到就从那一步出去。同一个网站同时写在几处时，更具体的域名优先。"),
                style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
    }
}

@Composable
private fun StartDot() {
    Box(Modifier.size(30.dp).clip(CircleShape).background(Nb.c.text), contentAlignment = Alignment.Center) {
        Icon(NbIcons.Phone, null, tint = Nb.c.bg, modifier = Modifier.size(16.dp))
    }
}

/** The left column of the path: a dot and the line down to the next step. */
@Composable
private fun Rail(dot: @Composable () -> Unit, last: Boolean, top: Dp = 0.dp, lineColor: Color = Nb.c.accent.copy(alpha = 0.35f)) {
    Column(Modifier.width(30.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (top > 0.dp) Box(Modifier.width(2.dp).height(top).background(lineColor))
        dot()
        Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else lineColor))
    }
    Spacer(Modifier.width(10.dp))
}

@Composable
private fun StepRow(
    number: Int,
    on: Boolean,
    last: Boolean,
    title: String,
    sub: String,
    exit: String,
    exitTarget: String,
    checked: Boolean,
    onToggle: ((Boolean) -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val c = Nb.c
    val (bg, fg) = RuleUi.targetColors(exitTarget, c)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Rail(
            dot = {
                Box(
                    Modifier.padding(top = 12.dp).size(26.dp).clip(CircleShape)
                        .background(if (on) c.card else c.line.copy(alpha = 0.4f))
                        .border(2.dp, if (on) fg else c.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("$number", color = if (on) fg else c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            },
            last = last,
        )
        NbCard(
            Modifier.weight(1f).padding(bottom = 6.dp).alpha(if (on) 1f else 0.7f),
            onClick = onClick,
            padding = PaddingValues(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            radius = 16.dp,
            color = if (on) c.card else c.card.copy(alpha = 0.5f),
        ) {
            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = NbType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = c.text)
                    Text(sub, style = NbType.small.copy(fontSize = 11.5.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    exit, color = if (on) fg else c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp).widthIn(max = 110.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (on) bg else c.line.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
                when {
                    onToggle != null -> Switch(
                        checked = checked, onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accentStrong, checkedThumbColor = Color.White),
                        modifier = Modifier.padding(start = 6.dp).graphicsLayer { scaleX = 0.8f; scaleY = 0.8f },
                    )
                    onClick != null -> Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                    else -> Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- lists

/**
 * The rules of one page: direct, proxy (with the policy groups) or block.
 * Tapping an entry moves or deletes it; "添加" opens the add sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleListScreen(page: String, onClose: () -> Unit, onEditPolicy: (Long) -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    val rules = state.settings.rules
    val pages = listOf("direct", "proxy", "block")
    var tab by remember { mutableIntStateOf(pages.indexOf(page).coerceAtLeast(0)) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RuleItem?>(null) }
    val apps by produceState<List<AppItem>>(emptyList()) { value = InstalledApps.load(ctx) }
    val label: (String) -> String = { pkg -> apps.firstOrNull { it.pkg == pkg }?.label ?: pkg }
    val node = remember(state.selectedId) { Repo.selectedNodeName() }
    LaunchedEffect(Unit) { Repo.loadPolicies() }

    val counts = pages.map { p -> RuleUi.count(rules) { RuleUi.page(it) == p } }
    GlassScreen(
        header = {
            TopBar(T("分流规则"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) })
            Segmented(
                listOf(T("直连 %d", counts[0]), T("代理 %d", counts[1]), T("屏蔽 %d", counts[2])), tab, { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), height = 40.dp,
            )
        },
        floating = { AddButton(Modifier.align(Alignment.BottomEnd)) { adding = true } },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = LocalBottomInset.current + 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (pages[tab]) {
                "direct" -> {
                    Hint(T("这些不经过节点，直接连接。点一条可以修改、移到别的页或删除。"))
                    TargetLists(rules, "direct", policies, label, apps) { editing = it }
                }
                "block" -> {
                    Hint(T("这些连接会被拦下。点一条可以修改、移到别的页或删除。"))
                    TargetLists(rules, "block", policies, label, apps) { editing = it }
                }
                else -> {
                    Hint(T("开着「绕过中国大陆」或只代理部分应用时，这些也一定走代理；可以指定用哪个策略组。"))
                    DestinationCard(T("首页选中的节点"), node.ifEmpty { T("还没有选择节点") }, "proxy", null) {
                        TargetLists(rules, "proxy", policies, label, apps, card = false) { editing = it }
                    }
                    policies.forEach { p ->
                        val target = "policy:${p.id}"
                        val sub = when {
                            p.nodes.isEmpty() -> T("没有节点")
                            p.mode == "select" -> T("手动选择 · 用 %s", p.nodes.firstOrNull { it.id == p.selected }?.name ?: p.nodes.first().name)
                            else -> T("自动选最快 · %d 个节点", p.nodes.size)
                        }
                        DestinationCard(T("策略组 · %s", p.name), sub, target, { onEditPolicy(p.id) }) {
                            TargetLists(rules, target, policies, label, apps, card = false) { editing = it }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(18.dp))
                            .border(1.5.dp, c.line, RoundedCornerShape(18.dp)).clickable { onEditPolicy(0) },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(NbIcons.Plus, null, tint = c.accentInk, modifier = Modifier.size(18.dp))
                        Text(T("新建策略组"), color = c.accentInk, modifier = Modifier.padding(start = 8.dp))
                    }
                    Hint(T("策略组是一组节点（比如所有日本节点），可以自动选最快的，也可以手动选一个。"))
                }
            }
        }
    }

    if (adding) {
        val target = when (pages[tab]) {
            "direct" -> "direct"
            "block" -> "block"
            else -> "proxy"
        }
        AddRuleSheet(RuleDraft(target = target), onDismiss = { adding = false })
    }
    editing?.let { item -> RuleItemSheet(item, onDismiss = { editing = null }) }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = NbType.small, color = Nb.c.muted, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun AddButton(modifier: Modifier, onClick: () -> Unit) {
    val c = Nb.c
    Row(
        modifier
            .padding(end = 18.dp, bottom = LocalBottomInset.current + 18.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(c.accentStrong)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(NbIcons.Plus, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(T("添加"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** A card for one destination of the proxy page: its header and its rules. */
@Composable
private fun DestinationCard(title: String, sub: String, target: String, onHeader: (() -> Unit)?, content: @Composable () -> Unit) {
    val c = Nb.c
    val (bg, fg) = RuleUi.targetColors(target, c)
    NbCard(padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().background(bg.copy(alpha = 0.55f))
                .let { if (onHeader != null) it.clickable(onClick = onHeader) else it }
                .heightIn(min = 50.dp).padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
                Text(sub, style = NbType.small, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onHeader != null) Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
        content()
    }
}

/** The apps, websites and IP ranges of one target. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetLists(
    rules: List<Rule>,
    target: String,
    policies: List<PolicyView>,
    label: (String) -> String,
    apps: List<AppItem>,
    card: Boolean = true,
    onEdit: (RuleItem) -> Unit,
) {
    val c = Nb.c
    val (appItems, sites, ips) = RuleUi.items(rules, target, label)
    if (appItems.isEmpty() && sites.isEmpty() && ips.isEmpty()) {
        if (card) {
            NbCard { Text(T("还没有规则。点右下角「添加」，可以加网站、应用或 IP。"), style = NbType.small, color = c.muted) }
        } else {
            Text(T("还没有规则"), style = NbType.small, color = c.muted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
        }
        return
    }
    if (card) {
        if (appItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(T("应用 · %d", appItems.size))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    appItems.forEach { a -> AppChip(a, apps) { onEdit(a) } }
                }
            }
        }
        if (sites.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(T("网站 · %d", sites.size))
                NbCard(padding = PaddingValues(0.dp)) { ItemRows(sites, apps, onEdit) }
            }
        }
        if (ips.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(T("IP · %d", ips.size))
                NbCard(padding = PaddingValues(0.dp)) { ItemRows(ips, apps, onEdit) }
            }
        }
    } else {
        ItemRows(appItems + sites + ips, apps, onEdit, dividerFirst = true)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = NbType.section.copy(fontWeight = FontWeight.Bold), color = Nb.c.text, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun ItemRows(items: List<RuleItem>, apps: List<AppItem>, onEdit: (RuleItem) -> Unit, dividerFirst: Boolean = false) {
    val c = Nb.c
    items.forEachIndexed { i, it ->
        if (i > 0 || dividerFirst) HorizontalDivider(color = c.line, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { onEdit(it) }.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (it.kind == "package") AppIcon(it.values.first(), apps, 26.dp)
            Text(
                it.label, style = NbType.body.copy(fontSize = 14.5.sp), color = if (it.disabled) c.muted else c.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                if (it.disabled) T("已停用") else it.tag.ifEmpty { T("应用") }, style = NbType.small.copy(fontSize = 11.5.sp), color = c.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.line.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun AppChip(item: RuleItem, apps: List<AppItem>, onClick: () -> Unit) {
    val c = Nb.c
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(start = 5.dp, end = 12.dp).alpha(if (item.disabled) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(item.values.first(), apps, 30.dp)
        Text(item.label, style = NbType.body.copy(fontSize = 13.5.sp), color = c.text, maxLines = 1)
    }
}

/** An installed app's icon, or its first letter. */
@Composable
fun AppIcon(pkg: String, apps: List<AppItem>, size: Dp) {
    val c = Nb.c
    val a = apps.firstOrNull { it.pkg == pkg }
    val icon = a?.icon
    if (icon != null) {
        Image(icon.asImageBitmap(), null, Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Text((a?.label ?: pkg.substringAfterLast('.')).take(1).uppercase(), color = c.accentInk, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Where a rule entry goes, and whether to drop it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleItemSheet(item: RuleItem, onDismiss: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    val rules = state.settings.rules
    fun save(next: List<Rule>, msg: String) {
        onDismiss()
        scope.launch { Repo.saveRules(next).onSuccess { toast(msg) }.onFailure { toast(it.message ?: T("保存失败")) } }
    }
    NbSheet(onDismiss) {
        Text(item.label, style = NbType.big, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (item.kind == "package") item.values.first() else item.tag,
            style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
        )
        SectionLabel(T("走哪里"))
        Spacer(Modifier.height(8.dp))
        TargetChips(item.target, policies) { t ->
            if (t != item.target) save(RuleUi.place(rules, item.kind, item.values, t, item.disabled), T("已改为：%s", RuleUi.targetLabel(t, policies)))
        }
        Spacer(Modifier.height(14.dp))
        NbCard(padding = PaddingValues(0.dp)) {
            dev.efah.nextbox.ui.SwitchRow(T("暂时停用"), T("保留在列表里，但不生效"), item.disabled, { off ->
                save(RuleUi.place(rules, item.kind, item.values, item.target, off), if (off) T("已停用") else T("已启用"))
            })
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.material3.TextButton(
            onClick = { save(RuleUi.remove(rules, item.kind, item.values), T("已删除")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(T("删除这条规则"), color = c.bad) }
    }
}

/** Direct, proxy, block and the policy groups as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetChips(selected: String, policies: List<PolicyView>, withBlock: Boolean = true, onPick: (String) -> Unit) {
    val c = Nb.c
    val options = buildList {
        add("direct")
        add("proxy")
        if (withBlock) add("block")
        policies.forEach { add("policy:${it.id}") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { t ->
            val on = t == selected
            val (bg, fg) = RuleUi.targetColors(t, c)
            val label = if (t.startsWith("policy:")) T("策略组 · %s", RuleUi.targetLabel(t, policies)) else RuleUi.targetLabel(t, policies)
            Box(
                Modifier.height(38.dp).clip(RoundedCornerShape(19.dp))
                    .background(if (on) bg else Color.Transparent)
                    .border(1.dp, if (on) fg.copy(alpha = 0.5f) else c.line, RoundedCornerShape(19.dp))
                    .clickable { onPick(t) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) fg else c.muted, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}
