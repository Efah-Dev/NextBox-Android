package dev.efah.nextbox.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Group
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.ChoiceOption
import dev.efah.nextbox.ui.ChoiceSheet
import dev.efah.nextbox.ui.dragRow
import dev.efah.nextbox.ui.EmptyHint
import dev.efah.nextbox.ui.fmtAgo
import dev.efah.nextbox.ui.fmtBytes
import dev.efah.nextbox.ui.fmtDate
import dev.efah.nextbox.ui.glass
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.label
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.longPressDrag
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbField
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.NbMenu
import dev.efah.nextbox.ui.NbMenuDivider
import dev.efah.nextbox.ui.NbMenuItem
import dev.efah.nextbox.ui.rememberDragState
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.UsageBar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun NodesScreen(
    onEdit: (Long) -> Unit,
    onNew: (type: String) -> Unit,
    onImportClipboard: () -> Unit,
    onScan: () -> Unit,
    onNewChain: (exit: Long) -> Unit,
) {
    val c = Nb.c
    val toast = LocalToast.current
    val state by Repo.state.collectAsState()
    val profiles by Repo.profiles.collectAsState()
    val testing by Repo.testing.collectAsState()
    val updating by Repo.updating.collectAsState()
    val scope = rememberCoroutineScope()
    val group = state.groups.firstOrNull { it.id == state.currentGroupId }

    var sheet by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) } // sub | group | edit-group | delete-group | delete-nodes | duplicates | order
    // the group the group dialogs are about (a long press on another group's chip selects it first)
    var dialogGroup by remember { mutableStateOf<Group?>(null) }
    var menu by remember { mutableStateOf(false) }
    // null = not searching
    var query by remember { mutableStateOf<String?>(null) }
    // null = normal mode, otherwise the ids picked in multi-select mode
    var picked by remember { mutableStateOf<Set<Long>?>(null) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) } // node name, share link
    // the nodes in the order being dragged into, until the new order is saved
    var arranged by remember { mutableStateOf<List<Profile>?>(null) }
    // the nodes the delete confirmation is about
    var deleting by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // the nodes of a group test waiting for the "order afterwards?" answer
    var sortAsk by remember { mutableStateOf<List<Long>?>(null) }
    val drag = rememberDragState()
    val list = rememberLazyListState()
    // picking several nodes: sideways moves are ours, not a swipe to the next page
    val busy = picked != null || drag.key != null
    LaunchedEffect(busy) { dev.efah.nextbox.ui.PageSwipe.blocked = busy }
    DisposableEffect(Unit) { onDispose { dev.efah.nextbox.ui.PageSwipe.blocked = false } }

    val base = arranged ?: profiles
    val shown = remember(base, query) {
        val q = query?.trim()?.lowercase().orEmpty()
        if (q.isEmpty()) {
            base
        } else {
            base.filter { it.name.lowercase().contains(q) || it.type.contains(q) || serverOf(it).lowercase().contains(q) }
        }
    }
    // dragging works on the whole list only, not on search results or while picking
    val canDrag = query.isNullOrBlank() && picked == null

    fun testGroup(ids: List<Long>) {
        val pref = state.settings.sortAfterTest
        when {
            ids.size < 2 || pref == "none" -> Repo.testLatency(ids)
            pref == "latency" || pref == "default" -> Repo.testLatency(ids, pref)
            else -> sortAsk = ids
        }
    }

    fun shareLink(p: Profile, then: (String) -> Unit) {
        scope.launch {
            Repo.exportLinks(listOf(p.id)).onSuccess(then).onFailure { toast.error(it.message ?: T("导出失败")) }
        }
    }

    fun groupAction(g: Group, action: String) {
        when (action) {
            "update" -> Repo.updateInBackground(g.id)
            "test" -> testGroup(profiles.map { it.id })
            "clash" -> Repo.reviewClash(g.id)
            else -> { dialogGroup = g; dialog = action }
        }
    }

    GlassScreen(
        header = {
            val sel = picked
            if (sel != null) {
                TopBar(T("已选 %d 个", sel.size), leading = { BarIcon(NbIcons.Close, T("退出多选"), { picked = null }) }) {
                    val all = sel.size == shown.size && shown.isNotEmpty()
                    Text(
                        if (all) T("取消全选") else T("全选"), color = c.accentInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp))
                            .clickable { picked = if (all) emptySet() else shown.map { it.id }.toSet() }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            } else {
                TopBar(T("节点")) {
                    if (testing.isNotEmpty()) {
                        BarIcon(NbIcons.Close, T("停止测速"), { Repo.cancelTest() })
                    } else {
                        BarIcon(NbIcons.Bolt, T("全部测速"), { testGroup(shown.map { it.id }) })
                    }
                    BarIcon(NbIcons.Search, T("搜索节点"), { query = if (query == null) "" else null })
                    Box {
                        BarIcon(NbIcons.More, T("更多"), { menu = true })
                        NbMenu(menu, { menu = false }) {
                            NbMenuItem(T("多选"), NbIcons.CheckBox, { menu = false; picked = emptySet() })
                            NbMenuItem(T("排列方式…"), NbIcons.Sort, { menu = false; dialog = "order" })
                            NbMenuItem(T("删除重复节点"), NbIcons.Copy, { menu = false; dialog = "duplicates" })
                            if (group != null && !group.default) {
                                NbMenuDivider()
                                GroupMenuItems(group, { menu = false }) { groupAction(group, it) }
                            }
                        }
                    }
                }
            }
            if (query != null && picked == null) {
                NbField(
                    query.orEmpty(), { query = it }, singleLine = true,
                    placeholder = { Text(T("搜索名称、协议或服务器地址")) },
                    leadingIcon = { Icon(NbIcons.Search, null, tint = c.muted, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(state.groups, key = { it.id }) { g ->
                    var chipMenu by remember { mutableStateOf(false) }
                    Box {
                        Chip(g.displayName, g.id == state.currentGroupId, onClick = {
                            picked = null
                            scope.launch { Repo.selectGroup(g.id) }
                        }, onLongClick = {
                            picked = null
                            if (g.id != state.currentGroupId) scope.launch { Repo.selectGroup(g.id) }
                            chipMenu = true
                        })
                        NbMenu(chipMenu, { chipMenu = false }, offset = DpOffset(0.dp, 6.dp)) {
                            if (g.default) {
                                NbMenuItem(T("测速这个分组"), NbIcons.Bolt, { chipMenu = false; groupAction(g, "test") })
                            } else {
                                GroupMenuItems(g, { chipMenu = false }) { groupAction(g, it) }
                            }
                        }
                    }
                }
            }
        },
        floating = { backdrop ->
            val sel = picked
            if (sel != null) {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = LocalBottomInset.current + 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NbButton(T("测速"), { Repo.testLatency(sel.toList()) }, Modifier.weight(1f), kind = BtnKind.Secondary, enabled = sel.isNotEmpty(), icon = NbIcons.Bolt)
                    NbButton(
                        if (sel.isEmpty()) T("删除") else T("删除 %d 个", sel.size), { deleting = sel; dialog = "delete-nodes" },
                        Modifier.weight(1f), kind = BtnKind.Danger, enabled = sel.isNotEmpty(), icon = NbIcons.Trash,
                    )
                }
            } else {
                val shape = RoundedCornerShape(28.dp)
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = LocalBottomInset.current + 6.dp)
                        .height(56.dp)
                        .shadow(10.dp, shape, ambientColor = c.accentStrong, spotColor = c.accentStrong)
                        .glass(
                            backdrop, radius = 28.dp, refraction = 10.dp,
                            tint = c.accentStrong.copy(alpha = 0.78f), solid = c.accentStrong,
                        )
                        .clickable { sheet = true }
                        .padding(start = 18.dp, end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(NbIcons.Plus, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(T("添加"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        },
    ) { pad ->
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp + pad.calculateEndPadding(LayoutDirection.Ltr),
                top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (Repo.ADDING in updating) {
                item(key = "adding") {
                    NbCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
                            Spacer(Modifier.width(12.dp))
                            Text(T("正在添加订阅，下载完成后自动切换过去"), style = NbType.small, color = c.text)
                        }
                    }
                }
            }
            if (group != null && group.isSubscription && query == null && picked == null) {
                item(key = "sub") {
                    var subMenu by remember { mutableStateOf(false) }
                    Box {
                        SubscriptionCard(
                            group, profiles.size, group.id in updating,
                            onRefresh = { Repo.updateInBackground(group.id) },
                            onLongClick = { subMenu = true },
                        )
                        NbMenu(subMenu, { subMenu = false }, offset = DpOffset(24.dp, 4.dp)) {
                            GroupMenuItems(group, { subMenu = false }) { groupAction(group, it) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    if (profiles.isEmpty()) {
                        EmptyHint(T("这个分组还没有节点"), T("点右下角「添加」：订阅、扫码、剪贴板或手动添加"))
                    } else {
                        EmptyHint(T("没有匹配的节点"), T("换个关键词试试"))
                    }
                }
            }
            items(shown, key = { it.id }) { p ->
                val sel = picked
                var rowMenu by remember { mutableStateOf(false) }
                Box(
                    Modifier.animateItem(placementSpec = if (drag.key != null) null else androidx.compose.animation.core.spring())
                        .dragRow(drag, p.id)
                        .longPressDrag(
                            drag, list, p.id, shown.map { it.id }, gap = 6.dp, enabled = canDrag,
                            onMove = { from, to -> arranged = shown.toMutableList().also { it.add(to, it.removeAt(from)) } },
                            onDrop = {
                                val ids = arranged?.map { it.id }
                                if (ids != null && ids != profiles.map { it.id }) {
                                    scope.launch {
                                        Repo.reorderProfiles(ids).onFailure { toast.error(it.message ?: T("保存失败")) }
                                        arranged = null
                                    }
                                } else {
                                    arranged = null
                                }
                            },
                            onHold = { rowMenu = true },
                        ),
                ) {
                    NodeRow(
                        p,
                        selected = p.id == state.selectedId,
                        testing = p.id in testing,
                        picked = sel?.contains(p.id),
                        lifted = drag.key == p.id,
                        onClick = {
                            if (sel != null) {
                                picked = if (p.id in sel) sel - p.id else sel + p.id
                            } else {
                                scope.launch { Repo.select(p.id) }
                            }
                        },
                        // in multi-select mode, and in search results (no dragging), a long press is the menu
                        onLongClick = if (!canDrag && sel == null) ({ rowMenu = true }) else null,
                    )
                    NbMenu(rowMenu, { rowMenu = false }, offset = DpOffset(24.dp, 0.dp)) {
                        NbMenuItem(T("编辑"), NbIcons.Edit, { rowMenu = false; onEdit(p.id) })
                        NbMenuItem(T("测速"), NbIcons.Bolt, { rowMenu = false; Repo.testLatency(listOf(p.id)) })
                        NbMenuItem(T("复制分享链接"), NbIcons.Link, { rowMenu = false; shareLink(p) { copyToClipboard(it); toast.ok(T("已复制分享链接")) } })
                        NbMenuItem(T("显示二维码"), NbIcons.Qr, { rowMenu = false; shareLink(p) { link -> qr = p.name to link } })
                        if (!p.isChain) NbMenuItem(T("经过其他节点连接…"), NbIcons.Chain, { rowMenu = false; onNewChain(p.id) })
                        NbMenuItem(T("多选"), NbIcons.CheckBox, { rowMenu = false; picked = setOf(p.id) })
                        NbMenuDivider()
                        NbMenuItem(T("删除"), NbIcons.Trash, { rowMenu = false; deleting = setOf(p.id); dialog = "delete-nodes" }, danger = true)
                    }
                }
            }
        }
    }

    if (sheet) {
        NbSheet({ sheet = false }) {
            Text(T("添加"), style = NbType.big, color = c.text, modifier = Modifier.padding(bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportTile(NbIcons.Rss, T("添加订阅"), Modifier.weight(1f)) { sheet = false; dialog = "sub" }
                ImportTile(NbIcons.Scan, T("扫描二维码"), Modifier.weight(1f)) { sheet = false; onScan() }
                ImportTile(NbIcons.Clipboard, T("剪贴板导入"), Modifier.weight(1f)) { sheet = false; onImportClipboard() }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accentSoft.copy(alpha = 0.5f))
                    .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable { sheet = false; onNewChain(0) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(c.accentStrong), contentAlignment = Alignment.Center) {
                    Icon(NbIcons.Link, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(T("新建链式代理"), style = NbType.bodyBold, color = c.text)
                    Text(T("流量依次经过几个节点，网站看到最后一个节点的 IP"), style = NbType.small, color = c.muted)
                }
                Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(T("手动填写"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
            Spacer(Modifier.height(8.dp))
            val protocols = listOf("shadowsocks", "vmess", "vless", "trojan", "hysteria2", "tuic", "anytls", "naive", "socks", "http")
            (protocols.map { it to protocolName(it) } + ("json" to T("粘贴 JSON"))).chunked(3).forEach { row ->
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (type, label) ->
                        Box(
                            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp))
                                .clickable { sheet = false; onNew(type) },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = NbType.small.copy(fontSize = 13.sp), color = c.text, maxLines = 1) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            SheetItem(NbIcons.Folder, T("新建分组")) { sheet = false; dialog = "group" }
        }
    }

    qr?.let { (name, link) -> QrDialog(name, link) { qr = null } }
    sortAsk?.let { ids -> SortAfterTestDialog(ids.size, { sortAsk = null }) { by -> sortAsk = null; Repo.testLatency(ids, by) } }

    val dg = dialogGroup ?: group
    when (dialog) {
        "sub" -> TwoFieldDialog(
            title = T("添加订阅"), first = T("名称"), second = T("订阅地址"), initialFirst = "", initialSecond = "",
            confirm = T("添加"), onDismiss = { dialog = null },
        ) { name, url ->
            dialog = null
            Repo.addInBackground(name, url)
        }
        "group" -> TwoFieldDialog(
            title = T("新建分组"), first = T("分组名称"), second = null, initialFirst = "", initialSecond = "",
            confirm = T("创建"), onDismiss = { dialog = null },
        ) { name, _ ->
            dialog = null
            scope.launch { Repo.addGroup(name).onFailure { toast.error(it.message ?: T("创建失败")) } }
        }
        "edit-group" -> if (dg != null) {
            TwoFieldDialog(
                title = if (dg.isSubscription) T("编辑订阅") else T("编辑分组"), first = T("名称"), second = if (dg.isSubscription) T("订阅地址") else null,
                initialFirst = dg.name, initialSecond = dg.url, confirm = T("保存"), onDismiss = { dialog = null },
            ) { name, url ->
                dialog = null
                scope.launch { Repo.editGroup(dg, name, url).onFailure { toast.error(it.message ?: T("保存失败")) } }
            }
        }
        "order" -> ChoiceSheet(
            T("排列方式"),
            listOf(
                ChoiceOption("default", T("默认排序"), if (group?.isSubscription == true) T("订阅里的顺序") else T("添加的先后")),
                ChoiceOption("latency", T("按延迟"), T("最快的在最上面")),
                ChoiceOption("name", T("按名称"), ""),
                ChoiceOption("region", T("按地区"), T("同一地区的排在一起")),
            ),
            current = "", onDismiss = { dialog = null }, sub = T("也可以长按节点拖动排序"),
        ) { by ->
            dialog = null
            scope.launch { Repo.sortGroup(by).onFailure { toast.error(it.message ?: T("保存失败")) } }
        }
        "duplicates" -> DuplicatesSheet(onDismiss = { dialog = null })
        "delete-group" -> if (dg != null) {
            NbAlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(T("删除分组")) },
                text = { Text(if (dg.id == group?.id) T("“%s”和其中的 %d 个节点都会被删除。", dg.displayName, profiles.size) else T("“%s”和其中的节点都会被删除。", dg.displayName)) },
                confirmButton = { NbButton(T("删除"), { dialog = null; scope.launch { Repo.deleteGroup(dg.id) } }, kind = BtnKind.Danger) },
                dismissButton = { NbButton(T("取消"), { dialog = null }, kind = BtnKind.Secondary) },
            )
        }
        "delete-nodes" -> {
            val ids = deleting
            NbAlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(T("删除节点")) },
                text = {
                    val broken = remember(ids) { Repo.chainsUsing(ids) }
                    val what = if (ids.size == 1) T("要删除“%s”吗？", profiles.firstOrNull { it.id in ids }?.name.orEmpty()) else T("要删除选中的 %d 个节点吗？", ids.size)
                    Text(what + if (broken.isEmpty()) "" else "\n\n" + T("这些链式代理会用不了：%s", broken.joinToString("、")))
                },
                confirmButton = {
                    NbButton(T("删除"), {
                        dialog = null
                        picked = null
                        scope.launch { Repo.deleteProfiles(ids) }
                    }, kind = BtnKind.Danger)
                },
                dismissButton = { NbButton(T("取消"), { dialog = null }, kind = BtnKind.Secondary) },
            )
        }
    }
}

/** The menu items of a group (not the default one): on the ⋮ menu, a long press on its card or its chip. */
@Composable
private fun GroupMenuItems(g: Group, close: () -> Unit, onAction: (String) -> Unit) {
    fun act(a: String) { close(); onAction(a) }
    if (g.isSubscription) NbMenuItem(T("更新订阅"), NbIcons.Refresh, { act("update") })
    NbMenuItem(if (g.isSubscription) T("编辑订阅") else T("编辑分组"), NbIcons.Edit, { act("edit-group") })
    NbMenuItem(T("测速这个分组"), NbIcons.Bolt, { act("test") })
    // only for a Clash subscription (asked about its other parts once)
    if (g.isSubscription && g.clash != null) NbMenuItem(T("订阅附带的分组和规则"), NbIcons.Layers, { act("clash") }, sub = T("机场的分组、规则、DNS"))
    NbMenuDivider()
    NbMenuItem(T("删除分组"), NbIcons.Trash, { act("delete-group") }, danger = true)
}

/** "host:port" of a node, "" for a chain or a node without one. */
private fun serverOf(p: Profile): String {
    val host = runCatching { p.outbound["server"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
    if (host.isEmpty()) return ""
    val port = runCatching { p.outbound["server_port"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
    val h = if (':' in host) "[$host]" else host
    return if (port.isEmpty()) h else "$h:$port"
}

/** The short name of a protocol in the tag before the address. */
private fun protocolTag(type: String): String = when (type) {
    "shadowsocks" -> "SS"
    "hysteria2" -> "HY2"
    "hysteria" -> "HY"
    "naive" -> "Naive"
    "wireguard" -> "WG"
    "chain" -> T("链式")
    else -> protocolName(type)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscriptionCard(g: Group, count: Int, updating: Boolean, onRefresh: () -> Unit, onLongClick: () -> Unit) {
    val c = Nb.c
    NbCard(Modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = {}, onLongClick = onLongClick), padding = PaddingValues(0.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(T("%s · %d 个节点", g.name, count), style = NbType.bodyBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (updating) T("正在更新，可以继续操作…") else fmtAgo(g.updatedAt), style = NbType.small, color = if (updating) c.accentInk else c.muted)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft).clickable(enabled = !updating, onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (updating) {
                        // the spinner only animates while it is on screen
                        val spin = rememberInfiniteTransition(label = "spin")
                        val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
                        Icon(NbIcons.Refresh, null, tint = c.accentInk, modifier = Modifier.size(20.dp).rotate(angle))
                    } else {
                        Icon(NbIcons.Refresh, T("更新订阅"), tint = c.accentInk, modifier = Modifier.size(20.dp))
                    }
                }
            }
            val u = g.usage
            if (u != null && u.total > 0) {
                Spacer(Modifier.height(10.dp))
                UsageBar((u.upload + u.download).toFloat() / u.total)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        T("已用 %s / %s", fmtBytes(u.upload + u.download), fmtBytes(u.total)),
                        style = NbType.small, color = c.muted, modifier = Modifier.weight(1f),
                    )
                    if (u.expire > 0) Text(T("到期 %s", fmtDate(u.expire)), style = NbType.small, color = c.muted)
                }
            }
        }
        AnimatedVisibility(updating) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = c.accent, trackColor = c.accentSoft)
        }
    }
}

/**
 * picked: null in normal mode, otherwise whether the row is ticked in
 * multi-select mode (the box takes the place of the check mark, on the
 * right). The row's menu and dragging come from its parent (longPressDrag);
 * [onLongClick] is for when dragging is off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeRow(
    p: Profile,
    selected: Boolean,
    testing: Boolean,
    picked: Boolean?,
    lifted: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val c = Nb.c
    val lit = picked ?: selected
    val bg by animateColorAsState(
        if (lifted) c.card else if (lit) c.accentSoft.copy(alpha = if (c.dark) 0.6f else 0.55f) else c.card, label = "rowbg",
    )
    val border by animateColorAsState(if (lifted || lit) c.accent.copy(alpha = 0.6f) else c.cardBorder, label = "rowb")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(if (lit || lifted) 1.5.dp else 1.dp, border, RoundedCornerShape(16.dp))
            // with dragging on, the long press belongs to longPressDrag: an empty one here keeps it from being a tap
            .combinedClickable(onClick = onClick, onLongClick = onLongClick ?: {}, hapticFeedbackEnabled = onLongClick != null)
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            dev.efah.nextbox.ui.FlagText(
                p.label, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                // a tight line height: the default one made the tag, and the row, taller than the text
                Text(
                    protocolTag(p.type), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = c.muted, maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(c.line).padding(horizontal = 5.dp, vertical = 2.dp),
                )
                val detail = if (p.isChain) p.members.joinToString(" → ") { it.name.ifEmpty { T("已删除") } } else serverOf(p)
                if (detail.isNotEmpty()) {
                    Text(
                        detail, style = NbType.small.copy(fontSize = 12.sp, fontFamily = if (p.isChain) FontFamily.Default else FontFamily.Monospace),
                        color = c.muted, maxLines = 1, overflow = if (p.isChain) TextOverflow.Ellipsis else TextOverflow.MiddleEllipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        if (testing) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
        } else {
            LatencyText(p.latency)
        }
        // the check mark of the node in use, or the box of multi-select mode
        Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterEnd) {
            androidx.compose.animation.AnimatedVisibility(picked != null, enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start), exit = fadeOut() + shrinkHorizontally()) {
                val on = picked == true
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (on) c.accentStrong else Color.Transparent)
                        .border(1.5.dp, if (on) c.accentStrong else c.muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(NbIcons.Check, T("已选中"), tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            if (picked == null && selected) {
                Icon(NbIcons.Check, T("当前节点"), tint = c.accentInk, modifier = Modifier.size(20.dp).graphicsLayer { })
            }
        }
    }
}

@Composable
private fun ImportTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Nb.c
    Column(
        modifier.height(86.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.accentInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = NbType.small.copy(fontSize = 13.sp), color = c.text, maxLines = 1)
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = Nb.c
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accentInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = NbType.body, color = c.text)
    }
}

@Composable
fun TwoFieldDialog(
    title: String,
    first: String,
    second: String?,
    initialFirst: String,
    initialSecond: String,
    confirm: String,
    onDismiss: () -> Unit,
    note: String? = null,
    onConfirm: (String, String) -> Unit,
) {
    var a by remember { mutableStateOf(initialFirst) }
    var b by remember { mutableStateOf(initialSecond) }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (note != null) Text(note, style = NbType.small, color = Nb.c.muted)
                NbField(a, { a = it }, label = { Text(first) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (second != null) {
                    NbField(b, { b = it }, label = { Text(second) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { NbButton(confirm, { onConfirm(a.trim(), b.trim()) }) },
        dismissButton = { NbButton(T("取消"), onDismiss, kind = BtnKind.Secondary) },
    )
}

fun copyToClipboard(text: String) {
    dev.efah.nextbox.App.clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NextBox", text))
}
