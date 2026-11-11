package dev.efah.nextbox.ui.screens

import dev.efah.nextbox.ui.T
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.efah.nextbox.ui.NbAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.bg.Box as Core
import dev.efah.nextbox.bg.Status
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.UsageReport
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.EmptyHint
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.SectionTitle
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.UsageBar
import dev.efah.nextbox.ui.fmtBytes
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UsageScreen() {
    val c = Nb.c
    val status by Core.status.collectAsState()
    val billingDay by Prefs.billingDay.collectAsState()
    var report by remember { mutableStateOf(UsageReport()) }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(status, billingDay, reload) {
        while (true) {
            report = Repo.usage()
            delay(if (status == Status.Started) 5000 else 60000)
        }
    }
    GlassScreen(header = {
        TopBar(T("用量")) {
            Box {
                BarIcon(NbIcons.More, T("更多"), { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(T("清空用量统计"), color = c.bad) }, onClick = { menu = false; confirmReset = true })
                }
            }
        }
    }) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "totals") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TotalCard(T("今日"), report.today.total, report.today.up, report.today.down, Modifier.weight(1f))
                    val periodLabel = if (billingDay == 1) T("本月") else T("本期（%s 起）", report.periodFrom.drop(5))
                    TotalCard(periodLabel, report.period.total, report.period.up, report.period.down, Modifier.weight(1f))
                }
            }
            item(key = "chart") {
                NbCard {
                    Text(T("最近 30 天"), style = NbType.section, color = c.muted)
                    Spacer(Modifier.height(12.dp))
                    DayBars(report, Modifier.fillMaxWidth().height(120.dp))
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(report.days.firstOrNull()?.date?.drop(5).orEmpty(), style = NbType.small, color = c.muted, modifier = Modifier.weight(1f))
                        Text(T("今天"), style = NbType.small, color = c.muted)
                    }
                }
            }
            item(key = "nodes-title") {
                SectionTitle(T("节点用量"), Modifier.padding(top = 4.dp))
            }
            if (report.nodes.isEmpty()) {
                item(key = "nodes-empty") { EmptyHint(T("还没有节点用量"), T("连接后，这里会显示每个节点用了多少流量")) }
            }
            val max = report.nodes.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
            items(report.nodes, key = { it.id }) { n ->
                NbCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.animateItem()) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(n.name, style = NbType.bodyBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (n.group == T("默认分组")) T("默认分组") else n.group) + " · " + protocolName(n.type), style = NbType.small, color = c.muted, maxLines = 1)
                        }
                        Text(fmtBytes(n.total), style = NbType.bodyBold, color = c.text, modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    UsageBar(n.total.toFloat() / max)
                    Spacer(Modifier.height(6.dp))
                    Text(T("上传 %s · 下载 %s", fmtBytes(n.up), fmtBytes(n.down)), style = NbType.small, color = c.muted)
                }
            }
            item(key = "note") {
                Text(
                    T("统计的是经过 NextBox 的流量。节点用量只计算实际走代理的部分，直连的流量不算在节点上。"),
                    style = NbType.small, color = c.muted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (confirmReset) {
        NbAlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(T("清空用量统计")) },
            text = { Text(T("每日统计和所有节点的流量记录都会清零。")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { Repo.resetUsage(); reload++ }
                }) { Text(T("清空"), color = c.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(T("取消")) } },
        )
    }
}

@Composable
private fun TotalCard(label: String, total: Long, up: Long, down: Long, modifier: Modifier) {
    val c = Nb.c
    NbCard(modifier, padding = PaddingValues(16.dp)) {
        Text(label, style = NbType.small, color = c.muted, maxLines = 1)
        Text(fmtBytes(total), style = NbType.big.copy(fontSize = 22.sp), color = c.text)
        Spacer(Modifier.height(4.dp))
        Text("↑ ${fmtBytes(up)}  ↓ ${fmtBytes(down)}", style = NbType.small, color = c.muted, maxLines = 1)
    }
}

/** One bar per day; today in the accent color. Bars grow in when shown. */
@Composable
private fun DayBars(report: UsageReport, modifier: Modifier) {
    val c = Nb.c
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val days = report.days
        if (days.isEmpty()) return@Canvas
        val max = days.maxOf { it.total }.coerceAtLeast(1).toFloat()
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (days.size - 1)) / days.size
        days.forEachIndexed { i, d ->
            val h = (d.total / max) * size.height * grow.value
            val x = i * (w + gap)
            val color = if (i == days.lastIndex) c.accent else c.accentSoft
            if (d.total > 0) {
                drawRoundRect(color, Offset(x, size.height - h.coerceAtLeast(3f)), Size(w, h.coerceAtLeast(3f)), CornerRadius(w / 3, w / 3))
            } else {
                drawRoundRect(c.line, Offset(x, size.height - 3f), Size(w, 3f), CornerRadius(1.5f, 1.5f))
            }
        }
    }
}
