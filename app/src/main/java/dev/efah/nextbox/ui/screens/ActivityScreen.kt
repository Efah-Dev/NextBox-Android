package dev.efah.nextbox.ui.screens

import dev.efah.nextbox.ui.T
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.efah.nextbox.bg.Status
import dev.efah.nextbox.bg.toList
import dev.efah.nextbox.libbox.CommandClient
import dev.efah.nextbox.libbox.CommandClientHandler
import dev.efah.nextbox.libbox.CommandClientOptions
import dev.efah.nextbox.libbox.ConnectionEvents
import dev.efah.nextbox.libbox.Libbox
import dev.efah.nextbox.libbox.LogIterator
import dev.efah.nextbox.libbox.OutboundGroupItemIterator
import dev.efah.nextbox.libbox.OutboundGroupIterator
import dev.efah.nextbox.libbox.StatusMessage
import dev.efah.nextbox.libbox.StringIterator
import dev.efah.nextbox.ui.EmptyHint
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.fmtSpeed
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.efah.nextbox.bg.Box as Core

private data class Conn(
    val id: String,
    val pkg: String,
    val network: String,
    val destination: String,
    val rule: String,
    val outbound: String,
    val up: Long,
    val down: Long,
    val createdAt: Long,
)

/** A log line: id orders and keys it, time is the clock time ("14:53:48"). */
private data class LogLine(val id: Long, val level: Int, val time: String, val text: String)

/** Most log lines the page keeps; older ones are dropped from the top. */
private const val MAX_LOG_LINES = 500

private val main = android.os.Handler(android.os.Looper.getMainLooper())

// The core colours its log lines for terminals (ESC "[31m" ERROR ESC "[0m"): drop the codes.
private val ansi = Regex("\u001B\\[[0-9;]*m")

// "ERROR[0025] connection: …": the level, then seconds since the core instance started.
private val logPrefix = Regex("^([A-Z]+)\\[(\\d+)] ")

private var logSeq = 0L

/** Turns a core log message into a line with its clock time. */
private fun logLine(level: Int, message: String): LogLine {
    val text = message.replace(ansi, "")
    val now = System.currentTimeMillis()
    val m = logPrefix.find(text)
    val base = Core.coreStartedAt
    var at = now
    var body = text
    if (m != null) {
        body = m.groupValues[1] + " " + text.substring(m.range.last + 1)
        val t = base + (m.groupValues[2].toLongOrNull() ?: 0) * 1000
        // a line of an earlier instance (before a reload) would land in the future
        if (base > 0 && t <= now + 2000) at = t
    }
    val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        .format(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()))
    return LogLine(++logSeq, level, time, body)
}

/** Live connections and core logs, streamed from the command server while visible. */
@Composable
fun ActivityScreen(onShowPath: (host: String, pkg: String) -> Unit) {
    val c = Nb.c
    val status by Core.status.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val conns = remember { mutableStateMapOf<String, Conn>() }
    val logs = remember { mutableStateListOf<LogLine>() }
    var client by remember { mutableStateOf<CommandClient?>(null) }

    DisposableEffect(status, tab) {
        if (status != Status.Started) return@DisposableEffect onDispose {}
        val handler = object : CommandClientHandler {
            override fun connected() {}
            override fun disconnected(message: String?) {}
            override fun setDefaultLogLevel(level: Int) {}
            override fun clearLogs() {
                main.post { logs.clear() }
            }

            override fun writeLogs(messageList: LogIterator?) {
                val it = messageList ?: return
                // the core streams every level; the page shows warnings and errors only (0 panic … 3 warn)
                val batch = buildList { while (it.hasNext()) it.next().let { e -> if (e.level <= 3) add(logLine(e.level, e.message.orEmpty())) } }
                if (batch.isEmpty()) return
                main.post {
                    logs.addAll(batch)
                    if (logs.size > MAX_LOG_LINES) logs.removeRange(0, logs.size - MAX_LOG_LINES)
                }
            }

            override fun writeStatus(message: StatusMessage?) {}
            override fun writeGroups(message: OutboundGroupIterator?) {}
            override fun writeOutbounds(message: OutboundGroupItemIterator?) {}
            override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) {}
            override fun updateClashMode(newMode: String?) {}
            override fun writeConnectionEvents(events: ConnectionEvents?) {
                val ev = events ?: return
                // read everything on this (Go) thread, apply on the main thread
                val reset = ev.reset
                val closed = mutableListOf<String>()
                val deltas = mutableListOf<Triple<String, Long, Long>>()
                val upserts = mutableListOf<Conn>()
                val it = ev.iterator()
                while (it.hasNext()) {
                    val e = it.next()
                    val x = e.connection
                    when {
                        e.type.toLong() == Libbox.ConnectionEventClosed -> closed += e.id
                        x == null -> deltas += Triple(e.id, e.uplinkDelta, e.downlinkDelta)
                        x.closedAt != 0L -> closed += x.id
                        else -> upserts += Conn(
                            id = x.id,
                            pkg = x.processInfo?.packageNames()?.toList()?.firstOrNull().orEmpty(),
                            network = x.network.uppercase(),
                            destination = x.displayDestination(), rule = x.rule, outbound = x.outbound,
                            up = x.uplink, down = x.downlink, createdAt = x.createdAt,
                        )
                    }
                }
                main.post {
                    if (reset) conns.clear()
                    closed.forEach { id -> conns.remove(id) }
                    deltas.forEach { (id, up, down) -> conns[id]?.let { old -> conns[id] = old.copy(up = up, down = down) } }
                    upserts.forEach { cn -> conns[cn.id] = cn }
                }
            }
        }
        val cl = Libbox.newCommandClient(
            handler,
            CommandClientOptions().apply {
                addCommand(if (tab == 0) Libbox.CommandConnections else Libbox.CommandLog)
                statusInterval = 1_000_000_000L
            },
        )
        runCatching { cl.connect() }
        client = cl
        onDispose {
            client = null
            runCatching { cl.disconnect() }
        }
    }

    GlassScreen(header = {
        TopBar(T("活动")) {
            if (tab == 0 && conns.isNotEmpty()) {
                TextButton(onClick = { runCatching { client?.closeConnections() } }) { Text(T("全部断开"), color = c.bad) }
            }
        }
        Segmented(
            listOf(if (conns.isEmpty()) T("连接") else T("连接 %d", conns.size), T("日志")), tab, { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), height = 40.dp,
        )
    }) { pad ->
        AnimatedContent(tab, modifier = Modifier.fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "act") { t ->
            when {
                status != Status.Started -> EmptyHint(modifier = Modifier.padding(pad), title = T("未连接"), sub = T("连接后可以在这里查看实时连接和日志"))
                t == 0 -> ConnectionList(conns.values.sortedByDescending { it.createdAt }, pad, onShowPath) { id -> runCatching { client?.closeConnection(id) } }
                else -> LogList(logs, pad)
            }
        }
    }
}

@Composable
private fun ConnectionList(list: List<Conn>, pad: PaddingValues, onShowPath: (String, String) -> Unit, onClose: (String) -> Unit) {
    if (list.isEmpty()) {
        EmptyHint(T("暂无连接"), T("有应用联网时会显示在这里"), Modifier.padding(pad))
        return
    }
    val showApps by dev.efah.nextbox.data.Prefs.showApps.collectAsState()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "hint") {
            Text(
                T("点一条连接，可以把它的网站或应用加进分流规则。") + if (showApps) "" else "\n" + T("想看到每个连接来自哪个应用，可以在「设置 → 高级」里打开「显示连接所属应用」。"),
                style = NbType.small, color = Nb.c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        items(list, key = { it.id }) { x -> ConnRow(x, Modifier.animateItem(), onShowPath, onClose) }
    }
}

private val iconCache = HashMap<String, Drawable?>()
private val labelCache = HashMap<String, String>()

@Composable
private fun ConnRow(x: Conn, modifier: Modifier, onShowPath: (String, String) -> Unit, onClose: (String) -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var icon by remember(x.pkg) { mutableStateOf(iconCache[x.pkg]) }
    var label by remember(x.pkg) { mutableStateOf(labelCache[x.pkg] ?: x.pkg.ifEmpty { T("未识别应用") }) }
    LaunchedEffect(x.pkg) {
        if (x.pkg.isEmpty() || labelCache.containsKey(x.pkg)) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val pm = ctx.packageManager
                val info = InstalledApps.info(pm, x.pkg)
                labelCache[x.pkg] = pm.getApplicationLabel(info).toString()
                iconCache[x.pkg] = pm.getApplicationIcon(info)
            }
        }
        label = labelCache[x.pkg] ?: x.pkg
        icon = iconCache[x.pkg]
    }
    val proxied = x.outbound != "direct"
    Box(modifier) {
        NbCard(padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), radius = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.combinedClickable(onClick = { menu = true }, onLongClick = { menu = true })) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    val d = icon
                    if (d != null) {
                        Image(d.toBitmap(96, 96).asImageBitmap(), null, Modifier.size(32.dp))
                    } else {
                        Text(label.take(1), color = c.accentInk, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text("  ${x.network}", fontSize = 11.sp, color = c.muted)
                    }
                    Text(x.destination, fontSize = 13.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (x.rule.isNotEmpty()) x.rule + " → " else "") + if (proxied) T("代理") else T("直连"),
                        fontSize = 11.sp, color = if (proxied) c.accentInk else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("↓ " + fmtSpeed(x.down), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text("↑ " + fmtSpeed(x.up), fontSize = 11.sp, color = c.muted)
                }
            }
        }
        if (menu) {
            val host = hostOf(x.destination)
            ConnectionRuleSheet(
                host = host, pkg = x.pkg, appLabel = label, proxied = proxied,
                onShowPath = { onShowPath(host, x.pkg) }, onDisconnect = { onClose(x.id) }, onDismiss = { menu = false },
            )
        }
    }
}

@Composable
private fun LogList(logs: List<LogLine>, pad: PaddingValues) {
    val c = Nb.c
    if (logs.isEmpty()) {
        EmptyHint(T("暂无日志"), T("这里只显示内核的警告和错误，没有就说明一切正常"), Modifier.padding(pad))
        return
    }
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = logs.lastIndex)
    // The list follows new lines only while the user is at the bottom; once
    // they scroll up it stays where it is and offers a way back instead.
    var follow by remember { mutableStateOf(true) }
    var seenId by remember { mutableStateOf(logs.last().id) }
    val atBottom by remember { derivedStateOf { !state.canScrollForward } }
    val lastId = logs.last().id
    LaunchedEffect(state.isScrollInProgress, atBottom) {
        if (atBottom) follow = true else if (state.isScrollInProgress) follow = false
    }
    LaunchedEffect(lastId, atBottom) {
        if (follow && !atBottom) state.scrollToItem(logs.lastIndex)
        if (follow || atBottom) seenId = lastId
    }
    val unseen = if (follow) 0 else logs.count { it.id > seenId }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // keys keep the lines the user is reading in place while old ones are dropped from the top
            items(logs, key = { it.id }) { l ->
                val color = when {
                    l.level <= 2 -> c.bad
                    l.level == 3 -> c.mid
                    else -> c.text
                }
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(color = c.muted)) { append(l.time) }
                        append("  ")
                        withStyle(androidx.compose.ui.text.SpanStyle(color = color)) { append(l.text) }
                    },
                    fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = {
                        dev.efah.nextbox.App.clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NextBox", l.time + " " + l.text))
                        toast(T("已复制这一行"))
                    }),
                )
            }
        }
        if (!atBottom && !follow) {
            Text(
                if (unseen > 0) "↓ " + T("%d 条新日志", unseen) else "↓ " + T("回到最新"),
                color = c.accentInk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = pad.calculateBottomPadding() + 12.dp)
                    .clip(RoundedCornerShape(18.dp)).background(c.accentSoft)
                    .combinedClickable(onClick = {
                        scope.launch {
                            state.animateScrollToItem(logs.lastIndex)
                            follow = true
                        }
                    })
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}
