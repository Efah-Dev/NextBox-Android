package dev.efah.nextbox.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.R
import dev.efah.nextbox.bg.Box
import dev.efah.nextbox.bg.Speed
import dev.efah.nextbox.bg.Status
import dev.efah.nextbox.data.ExitInfo
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.backdropSource
import dev.efah.nextbox.ui.glass
import dev.efah.nextbox.ui.rememberBackdrop
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.fmtBytes
import dev.efah.nextbox.ui.fmtDuration
import dev.efah.nextbox.ui.fmtSpeed
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val modes = listOf(
    "global" to "全局代理", // i18n:skip
    "rule" to "规则分流", // i18n:skip
    "direct" to "全部直连", // i18n:skip
)

/**
 * The routing mode (as on NextBox for Windows), the "bypass mainland China"
 * step of rule mode, and which apps use the VPN (代理范围).
 */
@Composable
private fun RouteSection(onPickApps: () -> Unit) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val mode = state.settings.routeMode
    val bypass = state.settings.bypassCn
    val routeScope by Prefs.routeScope.collectAsState()
    val apps by Prefs.perAppList.collectAsState()
    val appMode by Prefs.perAppMode.collectAsState()
    // one card, one line per row: the connect button above takes the height left
    NbCard(padding = PaddingValues(0.dp)) {
        Column {
            Segmented(
                modes.map { T(it.second) }, modes.indexOfFirst { it.first == mode }.coerceAtLeast(0),
                onSelect = { i -> scope.launch { Repo.setRouteMode(modes[i].first) } },
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), height = 40.dp,
            )
            val enabled = mode != "direct"
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable(enabled = enabled) { scope.launch { Repo.setBypass(!bypass) } }
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(T("绕过中国大陆"), style = NbType.body, color = if (enabled) c.text else c.muted, modifier = Modifier.weight(1f))
                // how it resolves names, while it is on
                if (bypass && mode == "rule") ResolveDnsChip(state.settings.bypassDns, Modifier.padding(end = 4.dp))
                androidx.compose.material3.Switch(
                    checked = bypass && mode == "rule", enabled = enabled,
                    onCheckedChange = { scope.launch { Repo.setBypass(it) } },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = c.accentStrong,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = c.card,
                        uncheckedBorderColor = c.muted,
                        uncheckedThumbColor = c.muted,
                    ),
                    modifier = Modifier.graphicsLayer { scaleX = 0.86f; scaleY = 0.86f },
                )
            }
            androidx.compose.material3.HorizontalDivider(color = c.line.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onPickApps)
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(T("代理范围"), style = NbType.body, color = c.text, modifier = Modifier.weight(1f))
                Text(
                    when {
                        routeScope != Prefs.SCOPE_APPS -> T("所有应用")
                        appMode == Prefs.APPS_EXCLUDE -> T("排除 %d 个应用", apps.size)
                        else -> T("只代理 %d 个应用", apps.size)
                    },
                    style = NbType.small, color = c.muted, maxLines = 1,
                )
                Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.padding(start = 4.dp).size(16.dp))
            }
        }
    }
}

@Composable
fun HomeScreen(onConnect: () -> Unit, onOpenNodes: () -> Unit, onImport: () -> Unit, onPickApps: () -> Unit) {
    val c = Nb.c
    val state by Repo.state.collectAsState()
    val status by Box.status.collectAsState()
    val startedAt by Box.startedAt.collectAsState()
    val speed by Box.speed.collectAsState()
    val session by Box.session.collectAsState()
    val history by Box.history.collectAsState()
    val error by Box.error.collectAsState()
    // re-read after latency tests too (profiles changes)
    val profiles by Repo.profiles.collectAsState()
    // starts from the node list it already has: an empty card for a moment
    // (each time the page is built) made the page jump
    val selected by produceState(profiles.firstOrNull { it.id == state.selectedId }, state.selectedId, profiles) { value = Repo.profile(state.selectedId) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (status == Status.Started) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "NextBox",
            leading = {
                Icon(
                    painterResource(R.drawable.ic_stat_paw), null, tint = c.accent,
                    modifier = Modifier.padding(start = 16.dp, end = 10.dp).size(30.dp),
                )
            },
        ) {
            BarIcon(NbIcons.Clipboard, T("从剪贴板导入"), onImport)
        }

        // One fixed screen, never scrolled: everything below the connect
        // button has a fixed height (the status always two lines, whatever it
        // says), and the button takes the height left. In a small window
        // (split screen, a floating window) the traffic chart goes first, then
        // the button shrinks.
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = LocalBottomInset.current)) {
            val compact = maxHeight < 560.dp
            val tiny = maxHeight < 480.dp
            val gap = if (compact) 8.dp else 12.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                // the disc takes 78% of the height left at most, so its lights
                // have room around it and are not cut off at the top
                val size = minOf(maxHeight * 0.78f, maxWidth * 0.5f, 176.dp)
                if (size >= 40.dp) ConnectButton(status, size, onConnect)
            }

            StatusBlock(status, now - startedAt, startedAt, error, state.selectedId, compact)

            NodeCard(selected, onOpenNodes)

            RouteSection(onPickApps)

            TrafficCard(speed, session, history, status == Status.Started, sparkHeight = if (tiny) 0.dp else if (compact) 32.dp else 48.dp)
        }
        }
    }
}

/**
 * The connect button: a glass disc over colored lights. The lights drift
 * quickly while the core starts or stops, slowly while connected, and rest
 * otherwise, so the glass always has something to bend.
 */
@Composable
private fun ConnectButton(status: Status, diameter: Dp, onClick: () -> Unit) {
    val c = Nb.c
    val on = status == Status.Started
    val busy = status == Status.Starting || status == Status.Stopping
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.93f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "press",
    )
    val tint by animateColorAsState(
        if (on) c.accent.copy(alpha = 0.62f) else c.card.copy(alpha = if (c.dark) 0.30f else 0.40f), tween(450), label = "tint",
    )
    val solid by animateColorAsState(if (on) c.accent else c.card, tween(350), label = "solid")
    val paw by animateColorAsState(if (on) Color.White else c.accent, tween(350), label = "paw")
    val energy by animateFloatAsState(
        when (status) {
            Status.Started -> 1f
            Status.Starting, Status.Stopping -> 0.8f
            Status.Stopped -> 0.4f
        },
        tween(600), label = "energy",
    )

    var angle by remember { mutableFloatStateOf(0.6f) }
    LaunchedEffect(status) {
        val speed = when (status) {
            Status.Starting, Status.Stopping -> 2.4f
            Status.Started -> 0.3f
            Status.Stopped -> 0f
        }
        if (speed == 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            angle += (now - last) / 1e9f * speed
            last = now
        }
    }

    val backdrop = rememberBackdrop()
    val second = lerp(c.accent, Color(0xFF7C9BFF), 0.75f)
    val third = lerp(c.accent, Color(0xFFFFC46B), 0.6f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) {
            val r = diameter.toPx() / 2
            fun light(color: Color, alpha: Float, a: Float, distance: Float, radius: Float) {
                val p = center + Offset(cos(a) * distance, sin(a) * distance)
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = alpha * energy), Color.Transparent), p, radius),
                    radius, p,
                )
            }
            light(c.accent, 0.85f, angle, r * 0.72f, r * 1.2f)
            light(second, 0.70f, angle + 2.1f, r * 0.80f, r * 1.05f)
            light(third, 0.55f, angle + 4.2f, r * 0.70f, r * 0.95f)
        }
        if (busy) {
            // expanding ring while the core starts or stops
            val pulse = rememberInfiniteTransition(label = "pulse")
            val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ring")
            Box(
                Modifier
                    .size(diameter)
                    .graphicsLayer {
                        val s = 1f + ring * 0.24f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - ring
                    }
                    .border(3.dp, c.accent, CircleShape),
            )
        }
        Box(
            Modifier
                .size(diameter)
                .scale(scale)
                .glass(backdrop, radius = diameter / 2, tint = tint, solid = solid, blur = 12.dp, refraction = 24.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = if (on) T("断开连接") else T("连接")
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_stat_paw), null, tint = paw, modifier = Modifier.size(diameter * 0.5f))
        }
    }
}

@Composable
private fun NodeCard(node: Profile?, onClick: () -> Unit) {
    val c = Nb.c
    // a flag in the name moves into the badge, so the row shows one flag only
    val (nameFlag, shownName) = node?.let { dev.efah.nextbox.ui.splitFlag(it.name) } ?: ("" to "")
    NbCard(onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                // the flag of the node's region, else two letters from its name
                val flag = nameFlag.ifEmpty { node?.let { dev.efah.nextbox.ui.flagOf(it.region) }.orEmpty() }
                if (flag.isNotEmpty()) {
                    Text(flag, fontSize = 24.sp)
                } else {
                    Text(
                        node?.let { badge(it.name) } ?: "?",
                        color = c.accentInk, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (node != null) shownName else T("还没有选择节点"), style = NbType.bodyBold.copy(fontSize = 16.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    node?.let { protocolName(it.type) } ?: T("到「节点」页导入订阅或分享链接"),
                    style = NbType.small.copy(fontSize = 13.sp), color = c.muted, maxLines = 1,
                )
            }
            if (node != null && node.latency != 0) {
                LatencyText(node.latency, Modifier.padding(start = 8.dp))
            }
            Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
    }
}

/** Two letters for the node badge: a region code if the name has one, else its first characters. */
fun badge(name: String): String {
    val regions = linkedMapOf(
        "香港" to "HK", "台湾" to "TW", "日本" to "JP", "东京" to "JP", "大阪" to "JP", "新加坡" to "SG", // i18n:skip
        "美国" to "US", "韩国" to "KR", "首尔" to "KR", "英国" to "UK", "德国" to "DE", "法国" to "FR", // i18n:skip
        "加拿大" to "CA", "澳大利亚" to "AU", "俄罗斯" to "RU", "印度" to "IN", "荷兰" to "NL", "土耳其" to "TR", // i18n:skip
    )
    regions.forEach { (k, v) -> if (name.contains(k)) return v }
    Regex("\\b([A-Z]{2})\\b").find(name)?.let { return it.groupValues[1] }
    return name.trim().take(2).uppercase().ifEmpty { "?" }
}

@Composable
fun LatencyText(latency: Int, modifier: Modifier = Modifier) {
    val c = Nb.c
    val (text, color) = when {
        latency < 0 -> T("超时") to c.bad
        latency == 0 -> "" to c.muted
        latency < 150 -> "$latency ms" to c.good
        latency < 300 -> "$latency ms" to c.mid
        else -> "$latency ms" to c.slow
    }
    if (text.isNotEmpty()) Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun TrafficCard(speed: Speed, session: Speed, history: List<Speed>, on: Boolean, sparkHeight: androidx.compose.ui.unit.Dp) {
    val c = Nb.c
    var today by remember { mutableLongStateOf(0L) }
    var period by remember { mutableLongStateOf(0L) }
    LaunchedEffect(on) {
        while (true) {
            val u = Repo.usage()
            today = u.today.total
            period = u.period.total
            delay(if (on) 5000 else 30000)
        }
    }
    NbCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            SpeedValue(T("上传"), fmtSpeed(speed.up), Modifier.weight(1f))
            SpeedValue(T("下载"), fmtSpeed(speed.down), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        if (sparkHeight > 0.dp) Sparkline(history, Modifier.fillMaxWidth().height(sparkHeight))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(T("本次 %s", fmtBytes(session.up + session.down)), style = NbType.small, color = c.muted, modifier = Modifier.weight(1f))
            Text(T("今日 %s · 本月 %s", fmtBytes(today), fmtBytes(period)), style = NbType.small, color = c.muted)
        }
    }
}

@Composable
private fun SpeedValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = NbType.small, color = Nb.c.muted)
        Text(value, style = NbType.big, color = Nb.c.text)
    }
}

/** Download (accent) and upload (grey) speed over the last minute. */
@Composable
private fun Sparkline(history: List<Speed>, modifier: Modifier) {
    val c = Nb.c
    val grow by animateFloatAsState(1f, tween(600), label = "grow")
    Canvas(modifier) {
        if (history.size < 2) return@Canvas
        val max = (history.maxOf { maxOf(it.up, it.down) }).coerceAtLeast(16 * 1024).toFloat()
        fun line(values: List<Long>): Path {
            val p = Path()
            val step = size.width / (values.size - 1)
            values.forEachIndexed { i, v ->
                val x = i * step
                val y = size.height - (v / max) * (size.height - 4f) * grow - 2f
                if (i == 0) {
                    p.moveTo(x, y)
                } else {
                    val px = (i - 1) * step
                    val py = size.height - (values[i - 1] / max) * (size.height - 4f) * grow - 2f
                    val mid = (px + x) / 2
                    p.cubicTo(mid, py, mid, y, x, y)
                }
            }
            return p
        }
        drawLine(c.line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 2f)
        drawPath(line(history.map { it.up }), c.muted.copy(alpha = 0.7f), style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line(history.map { it.down }), c.accent, style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The status under the connect button, always two lines high so that nothing
 * below moves: the state (and how long it has been connected), then where the
 * traffic leaves while connected, otherwise what to do or what went wrong. A
 * long error is cut to one line; tapping it shows all of it.
 */
@Composable
private fun StatusBlock(status: Status, connectedMs: Long, session: Long, error: String?, nodeId: Long, compact: Boolean) {
    val c = Nb.c
    var showError by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().height(if (compact) 46.dp else 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "status",
        ) { st ->
            Text(
                when (st) {
                    Status.Started -> T("已连接") + " · " + fmtDuration(connectedMs.coerceAtLeast(0) / 1000)
                    Status.Starting -> T("正在连接…")
                    Status.Stopping -> T("正在断开…")
                    Status.Stopped -> T("未连接")
                },
                style = if (compact) NbType.title.copy(fontSize = 19.sp) else NbType.title, color = c.text, maxLines = 1,
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (status) {
                Status.Started -> ExitLine(nodeId, session)
                Status.Starting -> Text(T("正在启动内核"), style = NbType.small.copy(fontSize = 13.sp), color = c.muted, maxLines = 1)
                Status.Stopping -> {}
                Status.Stopped -> Text(
                    error ?: T("点击连接"), style = NbType.small.copy(fontSize = 13.sp), color = if (error != null) c.bad else c.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp).let { if (error != null) it.clickable { showError = true } else it },
                )
            }
        }
    }
    if (showError && error != null) {
        NbAlertDialog(
            onDismissRequest = { showError = false },
            title = { Text(T("连接失败")) },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { showError = false }) { Text(T("知道了")) } },
        )
    }
}

/**
 * Where the connected node's traffic leaves (落地 IP, its network and the
 * latency), as on the desktop. The result is kept in Repo: coming back to the
 * page shows it; a check runs after connecting or switching nodes and when
 * the last one is 5 minutes old (30 seconds after a failure), only while the
 * page is shown. Tap: show or hide the full IP (or check again after a
 * failure); long press: copy it.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ExitLine(nodeId: Long, session: Long) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val all by Repo.exit.collectAsState()
    val state = all?.takeIf { it.nodeId == nodeId && it.session == session }
    var reveal by remember { mutableStateOf(false) }
    LaunchedEffect(nodeId, session) {
        // a new connection or node: let the core settle first
        if (Repo.exitDueIn(nodeId, session) == 0L && state?.info == null) delay(1500)
        while (true) {
            Repo.refreshExit(nodeId, session)
            delay(Repo.exitDueIn(nodeId, session).coerceAtLeast(1000))
        }
    }
    val e = state?.info
    val ok = state?.ok == true
    val checking = state == null || (state.checking && e == null)
    val dot = when {
        checking -> c.muted
        !ok -> c.bad
        e!!.latency < 300 -> c.good
        else -> c.mid
    }
    val text = when {
        checking -> T("检测落地 IP…")
        !ok -> if (e?.failed == "test") T("无法访问测速地址，点击重试") else T("落地检测失败，点击重试")
        else -> listOfNotNull(
            (dev.efah.nextbox.ui.flagOf(e!!.country) + " " + if (reveal) e.ip else maskIp(e.ip)).trim(),
            e.asn.takeIf { it > 0 }?.let { "AS$it" + if (e.org.isNotEmpty()) " " + shortOrg(e.org) else "" },
            T("%d ms", e.latency),
        ).joinToString(" · ")
    }
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = { if (ok) reveal = !reveal else scope.launch { Repo.refreshExit(nodeId, session, force = true) } },
                onLongClick = { if (ok) { clipboard.setText(androidx.compose.ui.text.AnnotatedString(e!!.ip)); toast(T("已复制")) } },
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(
            text, style = NbType.small.copy(fontSize = 13.sp), color = if (ok || checking) c.muted else c.bad,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** 103.151.12.21 → 103.•.•.21; IPv6 keeps its first and last group. */
private fun maskIp(ip: String): String {
    val v4 = ip.split(".")
    if (v4.size == 4) return v4[0] + ".•.•." + v4[3]
    val v6 = ip.split(":").filter { it.isNotEmpty() }
    return if (v6.size >= 2) v6.first() + ":•:" + v6.last() else ip
}

/** "Cloudflare, Inc." → "Cloudflare": the company forms are left out (as on the desktop). */
private fun shortOrg(org: String): String =
    org.replace(Regex("[,.]?s+(inc|llc|ltd|limited|co|corp|corporation|gmbh|s.?a|b.?v|pte|plc|ag|oy|ab|srl|sas|kk).?", RegexOption.IGNORE_CASE), "")
        .trimEnd(',', '.', ' ')
