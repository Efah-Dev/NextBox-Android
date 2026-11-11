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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
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

private val scopes = listOf(
    Prefs.SCOPE_GLOBAL to "全局代理", // i18n:skip
    Prefs.SCOPE_APPS to "指定应用", // i18n:skip
    Prefs.SCOPE_DIRECT to "全部直连", // i18n:skip
)

/** Routing scope + the "bypass mainland China" switch that applies to both proxied scopes. */
@Composable
private fun RouteSection(onPickApps: () -> Unit) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    val routeScope by Prefs.routeScope.collectAsState()
    val bypass by Prefs.bypassCn.collectAsState()
    val apps by Prefs.perAppList.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Segmented(scopes.map { T(it.second) }, scopes.indexOfFirst { it.first == routeScope }, onSelect = { i ->
            val target = scopes[i].first
            scope.launch { Repo.setRoute(scope = target) }
            if (target == Prefs.SCOPE_APPS && apps.isEmpty()) onPickApps()
        })
        NbCard(padding = PaddingValues(0.dp)) {
            val enabled = routeScope != Prefs.SCOPE_DIRECT
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { scope.launch { Repo.setRoute(bypass = !bypass) } }
                    .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(T("绕过中国大陆"), style = NbType.body, color = if (enabled) c.text else c.muted)
                    Text(
                        when (routeScope) {
                            Prefs.SCOPE_APPS -> if (apps.isEmpty()) T("还没有选择应用") else T("已选 %d 个应用", apps.size) + if (bypass) T(" · 国内网站直连") else ""
                            Prefs.SCOPE_DIRECT -> T("全部直连时不需要")
                            else -> if (bypass) T("国内网站和 IP 直连，其余走代理") else T("所有流量都走代理")
                        },
                        style = NbType.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (routeScope == Prefs.SCOPE_APPS) {
                    androidx.compose.material3.TextButton(onClick = onPickApps) { Text(T("选择应用"), color = c.accentInk) }
                }
                androidx.compose.material3.Switch(
                    checked = bypass && enabled, enabled = enabled,
                    onCheckedChange = { scope.launch { Repo.setRoute(bypass = it) } },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = c.accentStrong,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = c.card,
                        uncheckedBorderColor = c.muted,
                        uncheckedThumbColor = c.muted,
                    ),
                )
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
    val selected by produceState<Profile?>(null, state.selectedId, profiles) { value = Repo.profile(state.selectedId) }

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

        // No scrolling: the connect button takes whatever height is left, so
        // the page fills any screen size exactly.
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = LocalBottomInset.current)) {
            val compact = maxHeight < 560.dp
            val gap = if (compact) 8.dp else 12.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val size = minOf(maxHeight * 0.78f, maxWidth * 0.5f, 176.dp).coerceAtLeast(72.dp)
                ConnectButton(status, size, onConnect)
            }

            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(160)) + slideOutVertically { -it / 3 })
                },
                modifier = Modifier.fillMaxWidth(),
                label = "status",
            ) { st ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (title, sub) = when (st) {
                        Status.Started -> T("已连接") to fmtDuration((now - startedAt).coerceAtLeast(0) / 1000) + T(" · 点击断开")
                        Status.Starting -> T("正在连接…") to T("正在启动内核")
                        Status.Stopping -> T("正在断开…") to ""
                        Status.Stopped -> T("未连接") to (error ?: T("点击连接"))
                    }
                    Text(title, style = NbType.title, color = c.text)
                    if (sub.isNotEmpty()) {
                        Text(
                            sub, style = NbType.small.copy(fontSize = 13.sp),
                            color = if (st == Status.Stopped && error != null) c.bad else c.muted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
            }

            NodeCard(selected, onOpenNodes)

            RouteSection(onPickApps)

            TrafficCard(speed, session, history, status == Status.Started, sparkHeight = if (compact) 32.dp else 48.dp)
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
    NbCard(onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    node?.let { badge(it.name) } ?: "?",
                    color = c.accentInk, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(node?.name ?: T("还没有选择节点"), style = NbType.bodyBold.copy(fontSize = 16.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Sparkline(history, Modifier.fillMaxWidth().height(sparkHeight))
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
