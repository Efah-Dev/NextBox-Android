package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.RouteMatch
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.flagged
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.delay

/**
 * "试一试": type a website or IP (and optionally pick the app making the
 * connection) and the route path shows the step that takes it, and why.
 */
@Composable
fun RouteTestScreen(initialHost: String, initialPkg: String, onClose: () -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    var input by remember { mutableStateOf(initialHost) }
    var pkg by remember { mutableStateOf(initialPkg) }
    var result by remember { mutableStateOf<RouteMatch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickApp by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<RuleDraft?>(null) }
    val apps by produceState<List<AppItem>>(emptyList()) { value = InstalledApps.load(ctx) }
    val appLabel = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
    val s = state.settings
    val direct = s.routeMode == "direct"
    val appName = { p: String -> apps.firstOrNull { it.pkg == p }?.label ?: p }
    val node = remember(state.selectedId) { Repo.selectedNodeLabel() }.ifEmpty { T("首页选中的节点") }
    LaunchedEffect(Unit) { Repo.loadPolicies() }

    // test as the user types, and again when the rules change
    LaunchedEffect(input, pkg, s) {
        if (input.isBlank()) {
            result = null
            error = null
            return@LaunchedEffect
        }
        delay(250)
        Repo.testRoute(input, pkg).onSuccess { result = it; error = null }.onFailure { result = null; error = it.message }
    }

    GlassScreen(header = {
        TopBar(T("试一试"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) })
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, singleLine = true,
                placeholder = { Text(T("输入网址或 IP")) },
                leadingIcon = { Icon(NbIcons.Search, null, tint = c.muted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.height(52.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, c.line, RoundedCornerShape(22.dp))
                    .clickable { pickApp = true }.padding(start = 6.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (pkg.isEmpty()) {
                    Icon(NbIcons.Apps, null, tint = c.muted, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                    Text(T("应用"), style = NbType.small, color = c.muted)
                } else {
                    AppIcon(pkg, apps, 30.dp)
                    Text(appLabel, style = NbType.small, color = c.text, maxLines = 1, modifier = Modifier.widthIn(max = 84.dp))
                }
            }
        }
    }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = LocalBottomInset.current + 24.dp),
        ) {
            val r = result
            if (r == null) {
                Text(
                    error ?: T("输入一个网址（比如 chatgpt.com）或 IP，看看它会在哪一步被接住、从哪里出去。也可以选上发起连接的应用。"),
                    style = NbType.small, color = if (error != null) c.bad else c.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            // the route as the rules page shows it: LAN, the steps in order, everything else
            data class Step(val title: String, val on: Boolean)
            val steps = buildList {
                add(Step(T("局域网和本机地址"), true))
                s.rules.forEach { rule ->
                    val title = when (rule.kind) {
                        "ads" -> T("屏蔽广告")
                        "scene" -> Route.sceneTitle(rule.values.firstOrNull().orEmpty())
                        else -> Route.title(rule, appName)
                    }
                    add(Step(title, Route.active(s, rule)))
                }
                add(Step(T("其余流量"), true))
            }
            val last = steps.lastIndex
            val hit = when (r?.step) {
                null -> -1
                "lan" -> 0
                "rule" -> r.rule + 1
                "ads" -> s.rules.indexOfFirst { it.kind == "ads" } + 1
                "scene" -> s.rules.indexOfFirst { it.kind == "scene" && it.values.firstOrNull() == r.scene } + 1
                else -> last
            }
            steps.forEachIndexed { i, st ->
                val on = st.on
                val step = if (i == 0) "lan" else ""
                val target = if (r != null && i == hit) r.target else ""
                val tone = when {
                    i != hit -> c.accent
                    target == "final" -> if (direct) RuleUi.targetColors("direct", c).second else c.accentInk
                    step == "lan" -> RuleUi.targetColors("direct", c).second
                    else -> RuleUi.targetColors(target, c).second
                }
                val toneBg = when {
                    i != hit -> c.card
                    target == "final" -> if (direct) RuleUi.targetColors("direct", c).first else c.accentSoft
                    step == "lan" -> RuleUi.targetColors("direct", c).first
                    else -> RuleUi.targetColors(target, c).first
                }
                val passed = r != null && i < hit
                val state2 = when {
                    r == null -> ""
                    i == hit -> T("在这一步接住")
                    !on && i < hit -> T("没开，跳过")
                    passed -> T("没匹配，继续")
                    else -> T("不再经过")
                }
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Column(Modifier.width(26.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val lineOn = c.accentStrong.copy(alpha = 0.7f)
                        val lineOff = c.line
                        Box(Modifier.width(2.dp).height(if (i == hit) 12.dp else 16.dp).background(if (i == 0) Color.Transparent else if (r != null && i <= hit) lineOn else lineOff))
                        Box(
                            Modifier.size(if (i == hit) 22.dp else 14.dp).clip(CircleShape)
                                .background(if (i == hit) tone else if (passed) c.card else c.line.copy(alpha = 0.4f))
                                .border(2.dp, if (i == hit) tone else if (passed) c.accentStrong else c.line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (i == hit) Icon(NbIcons.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                        Box(Modifier.width(2.dp).weight(1f).background(if (i == last) Color.Transparent else if (r != null && i < hit) lineOn else lineOff))
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier.weight(1f).padding(vertical = 2.dp).heightIn(min = 42.dp)
                            .alpha(if (r == null || i <= hit) 1f else 0.55f)
                            .clip(RoundedCornerShape(14.dp)).background(if (i == hit) toneBg else c.card.copy(alpha = if (passed || r == null) 1f else 0.5f))
                            .border(1.dp, if (i == hit) tone else c.cardBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            st.title, style = NbType.body.copy(fontSize = 13.5.sp, fontWeight = if (i == hit) FontWeight.Bold else FontWeight.Normal),
                            color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text(state2, style = NbType.small.copy(fontSize = 11.5.sp, fontWeight = if (i == hit) FontWeight.Bold else FontWeight.Normal), color = if (i == hit) tone else c.muted)
                    }
                }
            }
            if (r != null) {
                Spacer(Modifier.height(14.dp))
                val exitTarget = when {
                    r.target == "final" -> if (direct) "direct" else "proxy"
                    else -> r.target
                }
                val exit = when {
                    exitTarget == "proxy" -> node
                    exitTarget.startsWith("policy:") -> {
                        val p = policies.firstOrNull { it.id == RuleUi.policyId(exitTarget) }
                        val chosen = p?.nodes?.firstOrNull { it.id == p.selected } ?: p?.nodes?.minByOrNull { if (it.latency > 0) it.latency else Int.MAX_VALUE }
                        T("分组代理 %s", p?.name ?: "") + (chosen?.let { " · " + flagged(it.name, it.region) } ?: "")
                    }
                    else -> RuleUi.targetLabel(exitTarget, policies)
                }
                val what = when {
                    r.kind == "package" -> T("应用「%s」", apps.firstOrNull { it.pkg == r.value }?.label ?: r.value)
                    r.kind == "geosite" -> T("「%s」", RuleUi.services.firstOrNull { r.value in it.geosite }?.let { T(it.label) } ?: r.value)
                    else -> T("「%s」", r.value)
                }
                val reason = when (r.step) {
                    "ads" -> T("在广告域名列表里，被「屏蔽广告」拦下。")
                    "lan" -> T("这是局域网地址（比如家里的路由器），总是直连。")
                    "scene" -> T("被常用场景「%s」接住。", Route.sceneTitle(r.scene))
                    "final" -> when (s.routeMode) {
                        "direct" -> T("现在是全部直连。")
                        "global" -> T("现在是全局代理，规则不生效，走首页选中的节点。")
                        else -> T("前面每一步都没匹配到，走首页选中的节点。")
                    }
                    else -> T("命中「%s」：%s", s.rules.getOrNull(r.rule)?.let { Route.title(it, appName) } ?: "", what) +
                        if (r.kind == "package") "\n" + T("这个应用的所有连接都按这条走，不看网址。") else ""
                } + (if (r.resolved.isNotEmpty()) "\n" + T("按 IP 判断的步骤用直连 DNS 解析出的 %s", r.resolved) else "") +
                    if (r.note.isNotEmpty()) "\n" + r.note else ""
                NbCard(padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(T("结果"), style = NbType.bodyBold.copy(fontSize = 13.sp), color = c.muted, modifier = Modifier.weight(1f))
                        TargetPill(exitTarget, policies, label = exit)
                    }
                    Text(reason, style = NbType.body.copy(fontSize = 13.5.sp), color = c.text, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = {
                        adding = if (pkg.isNotEmpty() && input.isBlank()) RuleDraft(tab = 1, apps = setOf(pkg), target = "direct")
                        else RuleDraft(tab = 0, text = hostOf(input), target = if (exitTarget == "direct") "proxy" else "direct")
                    }) { Text(T("想换个走法？加一条规则"), color = c.accentInk, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (pickApp) {
        NbAlertDialog(
            onDismissRequest = { pickApp = false },
            title = { Text(T("发起连接的应用")) },
            text = {
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    AppChooser(
                        selected = setOfNotNull(pkg.ifEmpty { null }),
                        onChange = { sel -> pkg = (sel - pkg).firstOrNull() ?: ""; pickApp = false },
                        showBulkActions = false, padding = PaddingValues(0.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { pkg = ""; pickApp = false }) { Text(T("不指定应用")) } },
        )
    }
    adding?.let { d -> AddRuleSheet(d, onDismiss = { adding = null }) }
}
