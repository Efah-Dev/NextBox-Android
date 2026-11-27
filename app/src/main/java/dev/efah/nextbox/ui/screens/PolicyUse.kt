package dev.efah.nextbox.ui.screens

// 分组代理 on the node page: connect through a policy group instead of one
// node (as in NextBox for Windows). An automatic group switches to its
// fastest member by itself; in a manual group a member is picked here.
// Picking a node in any group goes back to that node.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.PolicyView
import dev.efah.nextbox.ui.FlagText
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.flagOf
import dev.efah.nextbox.ui.splitFlag
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType

/**
 * One policy group: tap the card to connect through it (again to go back to
 * the node); in a manual group, tap a member to use it. [now] is the member
 * in use while connected through this group (0 otherwise).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PolicyUseCard(p: PolicyView, used: Boolean, now: Long, onUse: () -> Unit, onPick: (Long) -> Unit) {
    val c = Nb.c
    val current = when {
        used && now != 0L -> now
        p.mode == "select" -> p.selected.takeIf { id -> p.nodes.any { it.id == id } } ?: p.nodes.firstOrNull()?.id ?: 0L
        else -> 0L
    }
    NbCard(
        onClick = onUse,
        padding = PaddingValues(14.dp),
        border = if (used) c.accent.copy(alpha = 0.6f) else c.cardBorder,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .border(if (used) 5.dp else 2.dp, if (used) c.accent else c.line, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = NbType.bodyBold, color = if (used) c.accentInk else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (p.mode == "auto") T("自动选最快 · 每 %d 分钟", if (p.interval > 0) p.interval else 10) else T("手动选择"),
                    style = NbType.small, color = c.muted, maxLines = 1,
                )
            }
            when {
                p.nodes.isEmpty() -> Text(T("没有可用节点"), style = NbType.small, color = c.bad)
                used -> Text(T("正在使用"), style = NbType.small.copy(fontWeight = FontWeight.SemiBold), color = c.accentInk)
            }
        }
        if (p.nodes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.nodes.take(12).forEach { n ->
                    val on = n.id == current
                    val (flag, name) = splitFlag(n.name)
                    Row(
                        Modifier
                            .widthIn(max = 200.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) c.accentSoft else c.card)
                            .border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(50))
                            .then(if (p.mode == "select") Modifier.clickable { onPick(n.id) } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val f = flag.ifEmpty { flagOf(n.region) }
                        if (f.isNotEmpty()) {
                            FlagText(f, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            name, fontSize = 12.sp, color = if (on) c.text else c.muted,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (n.latency != 0) {
                            Spacer(Modifier.width(6.dp))
                            LatencyText(n.latency)
                        }
                    }
                }
                if (p.nodes.size > 12) {
                    Text(T("等 %d 个", p.nodes.size), fontSize = 12.sp, color = c.muted, modifier = Modifier.align(Alignment.CenterVertically).padding(4.dp))
                }
            }
        }
    }
}
