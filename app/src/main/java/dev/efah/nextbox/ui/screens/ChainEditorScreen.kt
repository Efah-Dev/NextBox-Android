package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.dragHandle
import dev.efah.nextbox.ui.dragRow
import dev.efah.nextbox.ui.flagged
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.label
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbField
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.rememberDragState
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.TopBar
import kotlinx.coroutines.launch

/** Creates (id 0) or edits a chain proxy: traffic passes its nodes in order. */
@Composable
fun ChainEditorScreen(id: Long, exit: Long = 0, onClose: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    var all by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var hops by remember { mutableStateOf<List<Long>>(emptyList()) }
    var groupId by remember { mutableStateOf(-1L) }
    var picking by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(id == 0L) }
    val drag = rememberDragState()
    LaunchedEffect(id) {
        all = Repo.allProfiles()
        if (id != 0L) {
            Repo.profile(id)?.let { p ->
                name = p.name
                hops = p.chain
                groupId = p.groupId
            }
            loaded = true
        } else {
            // "经过其他节点连接…" on a node: it is the exit, the user adds what comes before
            if (exit != 0L) hops = listOf(exit)
            // a chain lives in a manual group: the current one when it is manual
            groupId = state.groups.firstOrNull { it.id == state.currentGroupId && !it.isSubscription }?.id ?: -1L
        }
    }
    val byId = all.associateBy { it.id }
    // a new chain is named after its hops until the user types a name
    var autoName by remember { mutableStateOf(id == 0L) }
    LaunchedEffect(hops, all) {
        if (autoName && hops.size >= 2) name = hops.mapNotNull { byId[it]?.name }.joinToString(" → ")
    }

    GlassScreen(header = {
        TopBar(if (id == 0L) T("新建链式代理") else T("编辑链式代理"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) {
            if (id != 0L) {
                BarIcon(NbIcons.Trash, T("删除"), tint = c.bad) {
                    scope.launch { Repo.deleteProfiles(listOf(id)).onSuccess { onClose() } }
                }
            }
        }
    }) { pad ->
        if (!loaded) return@GlassScreen
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = LocalBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NbField(name, { name = it; autoName = false }, label = { Text(T("名称")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                T("流量从上往下依次经过这些节点，网站看到的是最后一个节点的 IP。需要 2 到 8 个节点，按住右边的把手拖动调整顺序。"),
                style = NbType.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Column {
                Endpoint(NbIcons.Phone, T("这台手机"))
                hops.forEachIndexed { i, hid ->
                    key(hid) {
                    Connector()
                    val p = byId[hid]
                    NbCard(Modifier.dragRow(drag, hid), padding = PaddingValues(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), radius = 18.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = c.accentInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(
                                    when (i) {
                                        0 -> T("入口")
                                        hops.size - 1 -> T("出口")
                                        else -> T("中转")
                                    },
                                    style = NbType.small.copy(fontSize = 11.sp), color = c.muted,
                                )
                                dev.efah.nextbox.ui.FlagText(p?.label ?: T("节点已不存在"), style = NbType.body, color = if (p == null) c.bad else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p != null) Text(protocolName(p.type), style = NbType.small, color = c.muted)
                            }
                            IconButton(onClick = { hops = hops.filterIndexed { j, _ -> j != i } }) {
                                Icon(NbIcons.Close, T("移除"), tint = c.muted, modifier = Modifier.size(16.dp))
                            }
                            Box(
                                Modifier.size(44.dp).dragHandle(drag, hid, hops, gap = 14.dp, onMove = { from, to ->
                                    hops = hops.toMutableList().also { it.add(to, it.removeAt(from)) }
                                }),
                                contentAlignment = Alignment.Center,
                            ) { Icon(NbIcons.Grip, T("拖动调整顺序"), tint = if (drag.key == hid) c.accentInk else c.muted.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
                        }
                    }
                    }
                }
                if (hops.size < 8) {
                    Connector()
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(18.dp))
                            .border(1.5.dp, c.line, RoundedCornerShape(18.dp)).clickable { picking = true },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(NbIcons.Plus, null, tint = c.accentInk, modifier = Modifier.size(18.dp))
                        Text(T("添加节点"), color = c.accentInk, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Connector()
                Endpoint(NbIcons.Globe, if (hops.isEmpty()) T("网站") else T("网站看到：%s 的 IP", byId[hops.last()]?.label ?: "?"))
            }
            NbButton(T("保存"), {
                    scope.launch {
                        Repo.saveChain(id, groupId, name, hops)
                            .onSuccess { toast.ok(T("已保存链式代理")); onClose() }
                            .onFailure { toast.error(it.message ?: T("保存失败")) }
                    }
                }, Modifier.fillMaxWidth().height(50.dp), enabled = hops.size >= 2)
        }
    }

    if (picking) {
        NodePicker(
            all.filter { !it.isChain && it.id !in hops },
            title = T("选择节点"),
            ordered = true,
            max = 8 - hops.size,
            onDismiss = { picking = false },
        ) { ids ->
            picking = false
            // in the order they were ticked; with a preset exit node, new hops go in front of it
            hops = if (exit != 0L && hops.lastOrNull() == exit) hops.dropLast(1) + ids + exit else hops + ids
        }
    }
}

@Composable
private fun Endpoint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(start = 12.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Nb.c.muted, modifier = Modifier.size(20.dp))
        Text(text, style = NbType.small, color = Nb.c.muted, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Connector() {
    Box(Modifier.padding(start = 21.dp).width(2.dp).height(14.dp).background(Nb.c.accent.copy(alpha = 0.45f)))
}

/**
 * Picks nodes from every group: narrowed by subscription (or manual group),
 * by region and by name; each row says which group the node comes from.
 * Several can be ticked at once. With ordered, the ticks are numbered and
 * onDone gets the nodes in the order they were ticked (a chain's hops); max
 * caps how many. initial is what is ticked to begin with.
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
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(0L) } // 0: every group
    var region by remember { mutableStateOf<String?>(null) } // null: every region, "": not recognised
    var picked by remember { mutableStateOf(initial) }
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
    val shown = inGroup.filter { (inRegion == null || it.region == inRegion) && (q.isEmpty() || it.name.lowercase().contains(q)) }

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

    NbSheet(onDismiss) {
        Text(title, style = NbType.big, color = c.text, modifier = Modifier.padding(bottom = 10.dp))
        NbField(query, { query = it }, singleLine = true, placeholder = { Text(T("搜索节点")) }, modifier = Modifier.fillMaxWidth())
        if (groups.size > 1) {
            Text(T("订阅 / 分组"), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(T("全部") + " " + nodes.size, group == 0L, onClick = { group = 0L })
                groups.forEach { g -> Chip(g.displayName + " " + nodes.count { it.groupId == g.id }, group == g.id, onClick = { group = g.id }) }
            }
        }
        if (regions.size > 1) {
            Text(T("地区"), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(T("全部"), inRegion == null, onClick = { region = null })
                regions.forEach { (code, n) ->
                    val label = if (code.isEmpty()) T("未识别") else flagged(placeName[code] ?: code, code)
                    Chip("$label $n", inRegion == code, onClick = { region = code })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ordered) T("按点选的顺序加入") else T("已选 %d 个", picked.size),
                style = NbType.small, color = c.muted, modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (!ordered && shown.isNotEmpty()) {
                val all = shown.all { it.id in picked }
                dev.efah.nextbox.ui.NbTextButton(if (all) T("取消全选") else T("全选这 %d 个", shown.size), {
                    picked = if (all) picked - shown.map { it.id }.toSet() else picked + shown.map { it.id }.filter { it !in picked }
                })
            }
        }
        Box(Modifier.fillMaxWidth().height(sheetListHeight(0.45f, 440.dp))) {
            if (shown.isEmpty()) {
                Text(
                    if (nodes.isEmpty()) T("没有可以加入的节点") else T("没有符合条件的节点"),
                    style = NbType.small, color = c.muted, modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp),
                )
            }
            LazyColumn {
                items(shown, key = { it.id }) { p ->
                    val n = picked.indexOf(p.id)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (n >= 0) c.accentSoft.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { toggle(p.id) }.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // a numbered circle for a chain's order, a tick otherwise
                        Box(
                            Modifier.padding(end = 10.dp).size(24.dp).clip(CircleShape)
                                .background(if (n >= 0) c.accentStrong else androidx.compose.ui.graphics.Color.Transparent)
                                .border(1.5.dp, if (n >= 0) c.accentStrong else c.muted, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (n >= 0) {
                                if (ordered) {
                                    Text("${n + 1}", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(NbIcons.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
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
                        LatencyText(p.latency)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NbButton(T("取消"), onDismiss, Modifier.weight(1f).height(48.dp), kind = BtnKind.Secondary)
            NbButton(when {
                        ordered -> if (picked.isEmpty()) T("选择节点") else T("加入 %d 个节点", picked.size)
                        else -> T("确定（%d 个）", picked.size)
                    }, { onDone(picked) }, Modifier.weight(2f).height(48.dp), enabled = ordered.not() || picked.isNotEmpty())
        }
    }
}
