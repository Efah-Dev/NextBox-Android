package dev.efah.nextbox.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.PolicyView
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.Rule
import dev.efah.nextbox.data.Settings
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.dragAbove
import dev.efah.nextbox.ui.dragHandle
import dev.efah.nextbox.ui.dragRow
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbField
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.rememberDragState
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.TopBar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val modeOptions = listOf("global" to "全局代理", "rule" to "规则分流", "direct" to "全部直连") // i18n:skip

/**
 * 分流规则: the routing mode and, in rule mode, the route every connection
 * takes from top to bottom (as on NextBox for Windows). LAN addresses come
 * first and "everything else" last; in between, ad blocking, the scenes and
 * the user's rules and rule sets are steps that can be dragged into any
 * order. A step leaves at the first match. With the text mode switch on, the
 * same rules are edited as text instead.
 */
@Composable
fun RulesScreen(onClose: () -> Unit, onTest: () -> Unit, onPerApp: () -> Unit, onEditSet: (Int) -> Unit, onEditPolicy: (Long) -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    val textMode by Prefs.ruleTextMode.collectAsState()
    val routeScope by Prefs.routeScope.collectAsState()
    val appMode by Prefs.perAppMode.collectAsState()
    val apps by Prefs.perAppList.collectAsState()
    val s = state.settings
    val node = remember(state.selectedId) { Repo.selectedNodeLabel() }.ifEmpty { T("首页选中的节点") }
    val labels by produceState(emptyMap<String, String>()) { value = InstalledApps.load(ctx).associate { it.pkg to it.label } }
    val appLabel = { p: String -> labels[p] ?: p }
    LaunchedEffect(Unit) { Repo.loadPolicies() }

    // while a step is dragged, the order lives here; it is saved when the finger lifts
    val drag = rememberDragState()
    var local by remember { mutableStateOf<List<Rule>?>(null) }
    val rules = local ?: s.rules
    LaunchedEffect(drag.key) {
        val moved = local
        if (drag.key == null && moved != null) {
            Repo.saveRules(moved).onFailure { toast.error(it.message ?: T("保存失败")) }
            local = null
        }
    }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(-1) }
    var sceneSheet by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    fun save(next: List<Rule>) = scope.launch { Repo.saveRules(next).onFailure { toast.error(it.message ?: T("保存失败")) } }

    val exitLabel = { t: String ->
        when {
            t == "proxy" -> node
            else -> RuleUi.targetLabel(t, policies)
        }
    }

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
            modeOptions.map { T(it.second) }, modeOptions.indexOfFirst { it.first == s.routeMode }.coerceAtLeast(0),
            onSelect = { i -> scope.launch { Repo.setRouteMode(modeOptions[i].first) } },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), height = 38.dp,
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 6.dp, bottom = LocalBottomInset.current + 24.dp),
        ) {
            if (s.routeMode != "rule") {
                // what this mode does, and the way to the mode the path is for
                NbCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), color = c.accentSoft.copy(alpha = 0.6f)) {
                    Text(
                        if (s.routeMode == "global") T("全局代理：除了局域网和屏蔽广告，所有流量都走当前节点，下面的步骤暂不生效。")
                        else T("全部直连：不使用代理，下面的步骤暂不生效。"),
                        style = NbType.small.copy(fontSize = 13.sp, lineHeight = 19.sp), color = c.text,
                    )
                    Text(
                        T("切换到规则分流"), color = c.accentInk, fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
                        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(c.card)
                            .clickable { scope.launch { Repo.setRouteMode("rule") } }.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            if (textMode) {
                RulesText(s, policies)
            } else {
                // where connections come from
                val start = when {
                    routeScope != Prefs.SCOPE_APPS -> T("手机上所有应用")
                    appMode == Prefs.APPS_EXCLUDE -> T("除了排除的 %d 个应用", apps.size)
                    else -> T("选中的 %d 个应用", apps.size)
                }
                Row(Modifier.height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).clickable(onClick = onPerApp)) {
                    Rail(first = true) { StartDot() }
                    Row(
                        Modifier.weight(1f).padding(bottom = 6.dp).heightIn(min = DotTop * 2 + DotSize).padding(start = 2.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(start, style = NbType.bodyBold, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(T("代理范围"), style = NbType.small, color = c.muted)
                        Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.padding(start = 2.dp).size(14.dp))
                    }
                }

                // 1: LAN, fixed
                StepCard(
                    number = 1, active = true, last = false, fixed = true,
                    title = T("局域网和本机地址"), tag = null, sub = T("192.168.x.x、10.x.x.x、127.0.0.1 等"),
                    exit = T("直连"), exitTarget = "direct",
                )

                val shown = rules.indices.filter { !Route.hidden(s, rules[it]) }
                val keys = Route.keys(rules)
                val shownKeys = shown.map { keys[it] }
                // keyed: a moved step keeps its own composition (and the drag
                // gesture running in it), instead of the slot it moved out of
                shown.forEachIndexed { k, i -> key(keys[i]) {
                    val r = rules[i]
                    val key = keys[i]
                    val on = Route.active(s, r)
                    val target = Route.target(s, r)
                    val handle = Modifier.dragHandle(drag, key, shownKeys, gap = 6.dp) { from, to ->
                        local = Route.move(local ?: s.rules, (local ?: s.rules).indices.filter { !Route.hidden(s, (local ?: s.rules)[it]) }, from, to)
                    }
                    // only the card moves: the numbers and the line stay where they are
                    val card = Modifier.dragRow(drag, key)
                    Box(Modifier.dragAbove(drag, key)) {
                        when {
                            r.kind == "ads" -> StepCard(
                                number = k + 2, active = on, last = false, handle = handle, cardModifier = card,
                                title = T("屏蔽广告"), tag = T("内置"),
                                sub = if (s.blockAds) T("常见的广告和跟踪域名") else T("已关闭"),
                                exit = T("屏蔽"), exitTarget = "block",
                                checked = s.blockAds,
                                onToggle = { v -> scope.launch { Repo.updateSettings(buildJsonObject { put("blockAds", v) }) } },
                            )
                            r.kind == "scene" -> {
                                val id = r.values.firstOrNull().orEmpty()
                                StepCard(
                                    number = k + 2, active = on, last = false, handle = handle, cardModifier = card,
                                    title = Route.sceneTitle(id), tag = T("内置"),
                                    sub = when {
                                        !Route.sceneOn(s, id) -> T("已关闭")
                                        s.routeMode != "rule" -> T("规则分流时生效")
                                        else -> Route.sceneDesc(id) + " · " + ResolveDns.label(s.bypassDns)
                                    },
                                    exit = exitLabel(target), exitTarget = target,
                                    checked = Route.sceneOn(s, id),
                                    onToggle = { v -> scope.launch { Repo.saveRules(s.rules, s.scenes.map { if (it.id == id) it.copy(enabled = v) else it }) } },
                                    onClick = { sceneSheet = id },
                                )
                            }
                            else -> StepCard(
                                number = k + 2, active = on, last = false, handle = handle, cardModifier = card,
                                title = Route.title(r, appLabel), tag = if (r.isSet) T("规则集") else null,
                                sub = when {
                                    r.kind == "process" -> T("电脑程序，只在 Windows 版生效")
                                    r.disabled -> T("已停用")
                                    !r.filled -> T("还没填内容")
                                    else -> Route.summary(r, appLabel)
                                },
                                exit = exitLabel(target), exitTarget = target,
                                checked = !r.disabled,
                                onToggle = if (r.kind == "process") null else ({ v -> save(rules.toMutableList().also { it[i] = r.copy(disabled = !v) }) }),
                                onClick = { if (r.isSet) onEditSet(i) else editing = i },
                            )
                        }
                    }
                } }

                // last: everything else, fixed
                val finalTarget = if (s.routeMode == "direct") "direct" else "proxy"
                StepCard(
                    number = shown.size + 2, active = true, last = true, fixed = true,
                    title = T("其余流量"), tag = null, sub = T("上面都没有命中的"),
                    exit = exitLabel(finalTarget), exitTarget = finalTarget,
                )

                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NbButton(T("添加规则"), { adding = true }, Modifier.weight(1f), icon = NbIcons.Plus)
                    NbButton(T("添加规则集"), { onEditSet(-1) }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = NbIcons.Archive)
                }
                Text(
                    T("从上往下依次判断，在哪一步命中就从哪一步出去。按住左边的把手拖动，调整先后顺序。"),
                    style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )
            }

            // 分组代理: groups of nodes a rule or a scene can send traffic to (as in the Windows settings)
            Text(T("分组代理"), style = NbType.bodyBold.copy(fontSize = 15.sp), color = c.text, modifier = Modifier.padding(start = 4.dp, top = 22.dp))
            Text(
                T("一组节点（比如所有日本节点），自动用最快的或由你选一个。在规则的「走哪里」里选它，比如让 YouTube 走日本。"),
                style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp),
            )
            NbCard(padding = PaddingValues(0.dp)) {
                policies.forEach { p ->
                    val sub = when {
                        p.nodes.isEmpty() -> T("没有节点")
                        p.mode == "select" -> T("手动选择 · 用 %s", (p.nodes.firstOrNull { it.id == p.selected } ?: p.nodes.first()).let { dev.efah.nextbox.ui.flagged(it.name, it.region) })
                        else -> T("自动选最快 · %d 个节点", p.nodes.size)
                    }
                    dev.efah.nextbox.ui.NavRow(p.name, sub, onClick = { onEditPolicy(p.id) })
                    dev.efah.nextbox.ui.RowDivider()
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onEditPolicy(0) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(NbIcons.Plus, null, tint = c.accentInk, modifier = Modifier.size(18.dp))
                    Text(T("新建分组代理"), color = c.accentInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            NbCard(padding = PaddingValues(0.dp)) {
                if (!textMode) {
                    dev.efah.nextbox.ui.SwitchRow(T("隐藏未开启的步骤"), T("关闭的步骤不显示"), s.routeHideOff, { v ->
                        scope.launch { Repo.updateSettings(buildJsonObject { put("routeHideOff", v) }, reload = false) }
                    })
                }
                dev.efah.nextbox.ui.SwitchRow(T("文本模式"), T("每行一条规则，适合一次改很多条"), textMode, { Prefs.setRuleTextMode(it) })
            }
            dev.efah.nextbox.ui.NbTextButton(T("恢复默认"), { confirmReset = true }, Modifier.align(Alignment.End), color = c.muted)
        }
    }

    if (adding) AddRuleSheet(RuleDraft(), onDismiss = { adding = false })
    if (editing >= 0 && editing < s.rules.size) RuleEditSheet(editing, onDismiss = { editing = -1 })
    sceneSheet?.let { id ->
        NbSheet(onDismiss = { sceneSheet = null }) {
            Text(Route.sceneTitle(id), style = NbType.big, color = c.text)
            Text(Route.sceneDesc(id), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
            // always direct; what can be chosen is how names are resolved
            ResolveDnsChoice(s.bypassDns)
        }
    }
    if (confirmReset) {
        dev.efah.nextbox.ui.NbAlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(T("恢复默认分流规则")) },
            text = { Text(T("会删除你添加的所有规则和规则集，步骤顺序恢复成刚安装时的样子，并开启「绕过中国大陆」。路由模式、屏蔽广告和分组代理不变。")) },
            confirmButton = {
                NbButton(T("恢复默认"), {
                    confirmReset = false
                    val (r, sc) = Route.defaults()
                    scope.launch { Repo.saveRules(r, sc).onSuccess { toast.ok(T("已恢复默认分流规则")) } }
                }, kind = BtnKind.Danger)
            },
            dismissButton = { NbButton(T("取消"), { confirmReset = false }, kind = BtnKind.Secondary) },
        )
    }
}

/** The rules as text (see Route.toText); saved a moment after typing stops, when it reads without errors. */
@Composable
private fun RulesText(s: Settings, policies: List<PolicyView>) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(Route.toText(s.rules, policies)) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(600)
        val p = Route.fromText(text, policies, s.rules)
        errors = p.errors
        if (p.errors.isNotEmpty()) return@LaunchedEffect
        if (p.rules != s.rules) scope.launch { Repo.saveRules(p.rules, s.scenes) }
    }
    NbField(
        text, { text = it },
        textStyle = NbType.small.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 18.sp),
        minLines = 12,
        modifier = Modifier.fillMaxWidth(),
    )
    if (errors.isNotEmpty()) {
        errors.take(5).forEach { Text(it, style = NbType.small, color = c.bad, modifier = Modifier.padding(top = 4.dp)) }
    } else {
        Text(
            T("修改会自动保存。类型：geosite、suffix（域名）、domain（完整域名）、keyword、regex、geoip、cidr（IP 段）、package（安卓应用）；规则集里还可以用 url（规则集网址）。"),
            style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}

@Composable
private fun StartDot() {
    Box(Modifier.size(DotSize).clip(CircleShape).background(Nb.c.text), contentAlignment = Alignment.Center) {
        Icon(NbIcons.Phone, null, tint = Nb.c.bg, modifier = Modifier.size(14.dp))
    }
}

private val DotSize = 26.dp

/** Where a dot sits: centred on a one-line step card (44 dp of content and 6 dp of padding above and below). */
private val DotTop = 15.dp

/**
 * The left column of the path: a dot on the line that runs through every
 * step. Each row draws its own piece of the line over its whole height, so
 * the pieces meet; the first starts and the last ends at its dot.
 */
@Composable
private fun Rail(first: Boolean = false, last: Boolean = false, dot: @Composable () -> Unit) {
    val line = Nb.c.accent.copy(alpha = 0.35f)
    Box(
        Modifier.width(30.dp).fillMaxHeight().drawBehind {
            val x = size.width / 2
            val mid = (DotTop + DotSize / 2).toPx()
            val top = if (first) mid else 0f
            val bottom = if (last) mid else size.height
            if (bottom > top) drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.padding(top = DotTop)) { dot() }
    }
    Spacer(Modifier.width(10.dp))
}

/** One step of the route: its number, a drag handle, what it is, where it goes, and its switch. */
@Composable
fun StepCard(
    number: Int,
    active: Boolean,
    last: Boolean,
    title: String,
    tag: String?,
    sub: String,
    exit: String,
    exitTarget: String,
    fixed: Boolean = false,
    handle: Modifier? = null,
    checked: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    /** On the card only (see dragRow): the dot and the line stay in place. */
    cardModifier: Modifier = Modifier,
) {
    val c = Nb.c
    val (bg, fg) = RuleUi.targetColors(exitTarget, c)
    // a dot takes the colours of the step that moves into its place
    val anim = androidx.compose.animation.core.tween<Color>(220)
    val dotFill by animateColorAsState(if (highlight) fg else if (active) c.card else c.line.copy(alpha = 0.4f), anim, label = "dotFill")
    val dotRing by animateColorAsState(if (active || highlight) fg else c.line, anim, label = "dotRing")
    val dotText by animateColorAsState(if (active) fg else c.muted, anim, label = "dotText")
    Row(Modifier.height(IntrinsicSize.Min)) {
        Rail(last = last) {
            Box(
                Modifier.size(DotSize).clip(CircleShape).background(dotFill).border(2.dp, dotRing, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (highlight) Icon(NbIcons.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                else Text("$number", color = dotText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        NbCard(
            Modifier.weight(1f).padding(bottom = 6.dp).then(cardModifier).alpha(if (active || highlight) 1f else 0.7f),
            onClick = onClick,
            padding = PaddingValues(start = if (handle != null) 2.dp else 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            radius = 16.dp,
            color = if (highlight) bg else if (active) c.card else c.card.copy(alpha = 0.5f),
            border = if (highlight) fg else c.cardBorder,
        ) {
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    handle != null -> Box(handle.size(width = 30.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                        Icon(NbIcons.Grip, T("拖动调整顺序"), tint = c.muted, modifier = Modifier.size(16.dp))
                    }
                    fixed -> Unit
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tag != null) {
                            Text(
                                tag, color = c.accentInk, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 5.dp).clip(RoundedCornerShape(6.dp)).background(c.accentSoft).padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                        Text(title, style = NbType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(sub, style = NbType.small.copy(fontSize = 11.5.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    exit, color = if (active || highlight) fg else c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp).widthIn(max = 110.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (active || highlight) bg else c.line.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
                when {
                    onToggle != null -> dev.efah.nextbox.ui.NbSwitch(checked, onToggle, Modifier.padding(start = 4.dp), scale = 0.8f)
                    else -> Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

/** Editing one rule: its name, values, target, on / off, or deleting it. */
@Composable
private fun RuleEditSheet(index: Int, onDismiss: () -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    val rule = remember { state.settings.rules[index] }
    var name by remember { mutableStateOf(rule.name) }
    var text by remember { mutableStateOf(rule.values.joinToString(if (rule.kind == "regex") "\n" else "\n")) }
    var target by remember { mutableStateOf(rule.target) }
    var regions by remember { mutableStateOf(if (rule.kind == "geoip") rule.values.toSet() else emptySet()) }
    var apps by remember { mutableStateOf(if (rule.kind == "package") rule.values.toSet() else emptySet()) }
    var picking by remember { mutableStateOf(false) }
    val installed by produceState<List<AppItem>>(emptyList()) { value = InstalledApps.load(ctx) }

    fun values(): List<String> = when (rule.kind) {
        "geoip" -> regions.toList()
        "package" -> apps.toList()
        "regex" -> text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        else -> text.split('\n', ',', '，', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun save(next: List<Rule>, msg: String) {
        onDismiss()
        scope.launch { Repo.saveRules(next).onSuccess { toast.ok(msg) }.onFailure { toast.error(it.message ?: T("保存失败")) } }
    }

    NbSheet(onDismiss) {
        Text(T("编辑规则"), style = NbType.big, color = c.text)
        Text(Route.kindLabel(rule.kind), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        NbField(
            name, { name = it }, singleLine = true,
            label = { Text(T("名称（可选）")) },
            supportingText = { Text(T("活动页会用这个名称显示命中这条规则的连接")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        when (rule.kind) {
            "geoip" -> RegionField(regions) { regions = it }
            "package" -> {
                AppsField(apps, installed) { picking = true }
            }
            "process" -> Text(T("电脑程序，只在 Windows 版生效"), style = NbType.small, color = c.muted)
            else -> NbField(
                text, { text = it }, minLines = 3, maxLines = 8,
                label = { Text(T("一行一个")) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(T("走哪里"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
        Spacer(Modifier.height(8.dp))
        TargetChips(target, policies) { target = it }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NbButton(T("删除"), { save(state.settings.rules.filterIndexed { i, _ -> i != index }, T("已删除")) }, Modifier.weight(1f).height(48.dp), kind = BtnKind.Danger)
            NbButton(T("保存"), {
                    val v = values().map { RuleUi.normalize(rule.kind, it) }.filter { it.isNotEmpty() }.distinct()
                    if (v.isEmpty() && rule.kind != "process") { toast.error(T("还没填内容")); return@NbButton }
                    val next = state.settings.rules.toMutableList()
                    if (index < next.size) next[index] = rule.copy(name = name.trim(), values = if (rule.kind == "process") rule.values else v, target = target)
                    save(next, T("已保存"))
                }, Modifier.weight(2f).height(48.dp))
        }
    }
    if (picking) AppPicker(apps, onDone = { apps = it; picking = false }, onDismiss = { picking = false })
}

/** The regions of an IP region rule as chips, and the picker to change them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionField(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    val c = Nb.c
    var open by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        selected.forEach { code -> dev.efah.nextbox.ui.Chip(Regions.name(code), true, onClick = { onChange(selected - code) }) }
        dev.efah.nextbox.ui.Chip(if (selected.isEmpty()) T("选择地区") else T("＋ 地区"), false, onClick = { open = true })
    }
    if (open) RegionPicker(selected, onDone = { onChange(it); open = false }, onDismiss = { open = false })
}

/** The apps of an app rule as chips, and the button that opens the chooser. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsField(selected: Set<String>, installed: List<AppItem>, onPick: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        selected.forEach { p -> dev.efah.nextbox.ui.Chip(installed.firstOrNull { it.pkg == p }?.label ?: p, true, onClick = onPick) }
        dev.efah.nextbox.ui.Chip(if (selected.isEmpty()) T("选择应用") else T("＋ 应用"), false, onClick = onPick)
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

/** Direct, proxy, block and the policy groups as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetChips(selected: String, policies: List<PolicyView>, onPick: (String) -> Unit) {
    val c = Nb.c
    val options = buildList {
        add("direct")
        add("proxy")
        add("block")
        policies.forEach { add("policy:${it.id}") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { t ->
            val on = t == selected
            val (bg, fg) = RuleUi.targetColors(t, c)
            val label = if (t.startsWith("policy:")) T("分组代理 · %s", RuleUi.targetLabel(t, policies)) else RuleUi.targetLabel(t, policies)
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
