package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.dragSelect
import dev.efah.nextbox.ui.flagged
import dev.efah.nextbox.ui.label
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbField
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.rememberDragSelectState
import dev.efah.nextbox.ui.SortChip
import dev.efah.nextbox.ui.Sorting
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType

/**
 * Picks nodes from every group, on a page of its own (a list in a bottom
 * sheet fought the sheet over every swipe): narrowed by subscription (or
 * manual group), by region and by name; each row says which group the node
 * comes from. Rows are ticked by tapping, or by long pressing one and
 * dragging over the others. The nodes shown can be speed tested and sorted
 * by latency here. With ordered, the ticks are numbered and onDone gets the
 * nodes in the order they were ticked (a chain's hops); max caps how many.
 * initial is what is ticked to begin with.
 */
@Composable
fun NodePicker(
    nodes: List<Profile>,
    title: String,
    initial: List<Long> = emptyList(),
    ordered: Boolean = false,
    max: Int = Int.MAX_VALUE,
    onDismiss: () -> Unit,
    onDone: (List<Long>) -> Unit,
) {
    val c = Nb.c
    val toast = LocalToast.current
    val state by Repo.state.collectAsState()
    val testing by Repo.testing.collectAsState()
    val measured by Repo.latencies.collectAsState()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(0L) } // 0: every group
    var region by remember { mutableStateOf<String?>(null) } // null: every region, "": not recognised
    var picked by remember { mutableStateOf(initial) }
    // "" (the lists' order), "asc", "desc": by latency
    var sortDir by remember { mutableStateOf("") }
    var leaving by remember { mutableStateOf(false) }
    val groups = state.groups.filter { g -> nodes.any { it.groupId == g.id } }
    val groupName = state.groups.associate { it.id to it.displayName }
    val placeName = Repo.places.associate { it.code to it.name }
    val inGroup = nodes.filter { group == 0L || it.groupId == group }
    // regions of the chosen group, most nodes first
    val regions = inGroup.groupingBy { it.region }.eachCount().entries
        .sortedWith(compareBy<Map.Entry<String, Int>>({ it.key.isEmpty() }, { -it.value }, { it.key }))
    // a region the chosen group does not have counts as "every region"
    val inRegion = region?.takeIf { r -> regions.any { it.key == r } }
    val q = query.trim().lowercase()
    val latencyOf = { p: Profile -> measured[p.id] ?: p.latency }
    val shown = inGroup.filter { (inRegion == null || it.region == inRegion) && (q.isEmpty() || it.name.lowercase().contains(q)) }
        .let { Sorting.byLatency(it, sortDir, latencyOf) }
    val busy = shown.any { it.id in testing }

    fun toggle(id: Long) {
        picked = when {
            id in picked -> picked - id
            picked.size >= max -> {
                toast(T("最多再加 %d 个", max))
                picked
            }
            else -> picked + id
        }
    }

    // leaving with changes asks first
    val leave = { if (picked != initial) leaving = true else onDismiss() }

    PickerPage(title, picked.size, onDone = { onDone(picked) }, onDismiss = leave) {
        NbField(
            query, { query = it }, singleLine = true,
            placeholder = { Text(T("搜索节点")) },
            leadingIcon = { Icon(NbIcons.Search, null, tint = c.muted) },
            shape = RoundedCornerShape(22.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )
        if (groups.size > 1) {
            Row(
                Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(T("全部") + " " + nodes.size, group == 0L, onClick = { group = 0L })
                groups.forEach { g -> Chip(g.displayName + " " + nodes.count { it.groupId == g.id }, group == g.id, onClick = { group = g.id }) }
            }
        }
        if (regions.size > 1) {
            Row(
                Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(T("全部"), inRegion == null, onClick = { region = null })
                regions.forEach { (code, n) ->
                    val label = if (code.isEmpty()) T("未识别") else flagged(placeName[code] ?: code, code)
                    Chip("$label $n", inRegion == code, onClick = { region = code })
                }
            }
        }

        // how many are ticked, testing and sorting what is shown, ticking all of it
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ordered) T("按点选的顺序加入") else T("已选 %d 个", picked.size),
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.muted, maxLines = 1,
            )
            Spacer(Modifier.size(6.dp))
            SmallIcon(if (busy) T("停止测速") else T("测速"), on = false, enabled = shown.isNotEmpty(), onClick = {
                if (busy) Repo.cancelTest() else Repo.testLatency(shown.map { it.id }, among = shown)
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = c.accent)
                else Icon(NbIcons.Bolt, null, modifier = Modifier.size(18.dp))
            }
            SortChip(sortDir, null, T("按延迟排序") + " · " + Sorting.dirLabel(sortDir), onClick = { sortDir = Sorting.next(sortDir) })
            Spacer(Modifier.weight(1f))
            if (!ordered && shown.isNotEmpty()) {
                val all = shown.all { it.id in picked }
                Text(
                    if (all) T("取消全选") else T("全选这 %d 个", shown.size),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.accentInk, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                        picked = if (all) picked - shown.map { it.id }.toSet() else picked + shown.map { it.id }.filter { it !in picked }
                    }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }

        val list = rememberLazyListState()
        // a new order starts at the top: the list would otherwise keep the row
        // that was first in view, and the fastest nodes end up above the screen
        LaunchedEffect(sortDir) { if (shown.isNotEmpty()) list.scrollToItem(0) }
        val select = rememberDragSelectState()
        val keys = shown.map { it.id }
        if (shown.isEmpty()) {
            Text(
                if (nodes.isEmpty()) T("没有可以加入的节点") else T("没有符合条件的节点"),
                style = NbType.small, color = c.muted, modifier = Modifier.padding(vertical = 24.dp, horizontal = 20.dp),
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().dragSelect(select, list, keys, picked.toSet()) { now ->
                val kept = picked.filter { it in now }
                // new ticks in the order the finger reached them
                val added = keys.filter { it in now && it !in picked }
                    .let { a -> if (select.current < select.anchor) a.reversed() else a }
                val room = (max - kept.size).coerceAtLeast(0)
                if (added.size > room) toast(T("最多再加 %d 个", max))
                picked = kept + added.take(room)
            },
            state = list,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        ) {
            items(shown, key = { it.id }) { p ->
                val n = picked.indexOf(p.id)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (n >= 0) c.accentSoft.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable { toggle(p.id) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // a numbered circle for a chain's order, a tick otherwise
                    Box(
                        Modifier.padding(end = 10.dp).size(24.dp).clip(CircleShape)
                            .background(if (n >= 0) c.accentStrong else Color.Transparent)
                            .border(1.5.dp, if (n >= 0) c.accentStrong else c.muted, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (n >= 0) {
                            if (ordered) {
                                Text("${n + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(NbIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        dev.efah.nextbox.ui.FlagText(p.label, style = NbType.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                protocolName(p.type),
                                p.region.takeIf { it.isNotEmpty() }?.let { placeName[it] ?: it },
                                groupName[p.groupId],
                            ).joinToString(" · "),
                            style = NbType.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (p.id in testing) {
                        CircularProgressIndicator(Modifier.padding(end = 4.dp).size(14.dp), strokeWidth = 2.dp, color = c.accent)
                    } else {
                        LatencyText(latencyOf(p))
                    }
                }
            }
        }
    }

    if (leaving) {
        NbAlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text(T("放弃这次的选择？")) },
            text = { Text(T("刚才勾选的变化不会保存。")) },
            confirmButton = { NbButton(T("放弃"), { leaving = false; onDismiss() }, kind = BtnKind.Danger) },
            dismissButton = { NbButton(T("继续选择"), { leaving = false }, kind = BtnKind.Secondary) },
        )
    }
}

/** A small icon button of the picker's bar; [on] marks a switch that is on. */
@Composable
private fun SmallIcon(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val c = Nb.c
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
            .background(if (on) c.accentSoft else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (!enabled) c.muted.copy(alpha = 0.5f) else if (on) c.accentInk else c.muted,
        ) { icon() }
    }
}
