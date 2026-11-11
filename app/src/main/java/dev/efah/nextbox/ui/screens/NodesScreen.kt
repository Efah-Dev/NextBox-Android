package dev.efah.nextbox.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Group
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.EmptyHint
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalGlass
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.UsageBar
import dev.efah.nextbox.ui.WindowBlurBehind
import dev.efah.nextbox.ui.fmtAgo
import dev.efah.nextbox.ui.fmtBytes
import dev.efah.nextbox.ui.fmtDate
import dev.efah.nextbox.ui.glass
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(onEdit: (Long) -> Unit, onImportClipboard: () -> Unit, onScan: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val state by Repo.state.collectAsState()
    val profiles by Repo.profiles.collectAsState()
    val testing by Repo.testing.collectAsState()
    val scope = rememberCoroutineScope()
    val group = state.groups.firstOrNull { it.id == state.currentGroupId }

    var sheet by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) } // sub | group | edit-group | delete-group | delete-nodes
    var menu by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    // a blocking progress dialog while a subscription downloads
    var fetching by remember { mutableStateOf<String?>(null) }
    // null = not searching
    var query by remember { mutableStateOf<String?>(null) }
    // null = normal mode, otherwise the ids picked in multi-select mode
    var picked by remember { mutableStateOf<Set<Long>?>(null) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) } // node name, share link

    val shown = remember(profiles, query) {
        val q = query?.trim()?.lowercase().orEmpty()
        if (q.isEmpty()) {
            profiles
        } else {
            profiles.filter { it.name.lowercase().contains(q) || it.type.contains(q) || it.outbound["server"].toString().lowercase().contains(q) }
        }
    }

    fun updateSub(g: Group) {
        if (updating) return
        updating = true
        fetching = T("正在更新“%s”…", g.name)
        scope.launch {
            Repo.updateSubscription(g.id)
                .onSuccess { toast(it) }
                .onFailure { toast(T("更新失败：") + (it.message ?: T("未知错误"))) }
            updating = false
            fetching = null
        }
    }

    fun shareLink(p: Profile, then: (String) -> Unit) {
        scope.launch {
            Repo.exportLinks(listOf(p.id)).onSuccess(then).onFailure { toast(it.message ?: T("导出失败")) }
        }
    }

    fetching?.let { msg ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            WindowBlurBehind()
            NbCard(padding = PaddingValues(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = c.accent)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(msg, style = NbType.bodyBold, color = c.text)
                        Text(T("正在下载并解析节点，节点多时需要十几秒"), style = NbType.small, color = c.muted)
                    }
                }
            }
        }
    }

    GlassScreen(
        header = {
            val sel = picked
            if (sel != null) {
                TopBar(T("已选 %d 个", sel.size), leading = { BarIcon(NbIcons.Close, T("退出多选"), { picked = null }) }) {
                    TextButton(onClick = { picked = if (sel.size == shown.size) emptySet() else shown.map { it.id }.toSet() }) {
                        Text(if (sel.size == shown.size && shown.isNotEmpty()) T("取消全选") else T("全选"), color = c.accentInk)
                    }
                    BarIcon(NbIcons.Bolt, T("测速"), { if (sel.isNotEmpty()) Repo.testLatency(sel.toList()) })
                    BarIcon(NbIcons.Trash, T("删除"), { if (sel.isNotEmpty()) dialog = "delete-nodes" }, tint = c.bad)
                }
            } else {
                TopBar(T("节点")) {
                    if (testing.isNotEmpty()) {
                        BarIcon(NbIcons.Close, T("停止测速"), { Repo.cancelTest() })
                    } else {
                        BarIcon(NbIcons.Bolt, T("全部测速"), { Repo.testLatency(shown.map { it.id }) })
                    }
                    BarIcon(NbIcons.Search, T("搜索节点"), { query = if (query == null) "" else null })
                    Box {
                        BarIcon(NbIcons.More, T("更多"), { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(T("多选")) }, onClick = { menu = false; picked = emptySet() })
                            DropdownMenuItem(text = { Text(T("按延迟排序")) }, onClick = { menu = false; scope.launch { Repo.sortGroup("latency") } })
                            DropdownMenuItem(text = { Text(T("按名称排序")) }, onClick = { menu = false; scope.launch { Repo.sortGroup("name") } })
                            if (group != null && !group.default) {
                                DropdownMenuItem(text = { Text(T("编辑分组")) }, onClick = { menu = false; dialog = "edit-group" })
                                DropdownMenuItem(text = { Text(T("删除分组"), color = c.bad) }, onClick = { menu = false; dialog = "delete-group" })
                            }
                        }
                    }
                }
            }
            if (query != null) {
                OutlinedTextField(
                    query.orEmpty(), { query = it }, singleLine = true,
                    placeholder = { Text(T("搜索名称、协议或服务器地址")) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(state.groups, key = { it.id }) { g ->
                    Chip(g.displayName, g.id == state.currentGroupId, onClick = {
                        picked = null
                        scope.launch { Repo.selectGroup(g.id) }
                    })
                }
            }
        },
        floating = { backdrop ->
            if (picked == null) {
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
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp + pad.calculateEndPadding(LayoutDirection.Ltr),
                top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (group != null && group.isSubscription && query == null && picked == null) {
                item(key = "sub") {
                    SubscriptionCard(group, profiles.size, updating, onRefresh = { updateSub(group) })
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
                NodeRow(
                    p,
                    selected = p.id == state.selectedId,
                    testing = p.id in testing,
                    picked = sel?.contains(p.id),
                    modifier = Modifier.animateItem(),
                    onClick = {
                        if (sel != null) {
                            picked = if (p.id in sel) sel - p.id else sel + p.id
                        } else {
                            scope.launch { Repo.select(p.id) }
                        }
                    },
                    onPick = { picked = setOf(p.id) },
                    onEdit = { onEdit(p.id) },
                    onTest = { Repo.testLatency(listOf(p.id)) },
                    onCopy = { shareLink(p) { copyToClipboard(it); toast(T("已复制分享链接")) } },
                    onQr = { shareLink(p) { link -> qr = p.name to link } },
                    onDelete = { scope.launch { Repo.deleteProfiles(listOf(p.id)) } },
                )
            }
        }
    }

    if (sheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheet = false }, sheetState = sheetState,
            containerColor = if (LocalGlass.current) c.card.copy(alpha = if (c.dark) 0.82f else 0.86f) else c.card,
            tonalElevation = 0.dp,
        ) {
            WindowBlurBehind()
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Text(T("添加"), style = NbType.big, color = c.text, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                SheetItem(NbIcons.Scan, T("扫描二维码")) { sheet = false; onScan() }
                SheetItem(NbIcons.Clipboard, T("从剪贴板导入分享链接")) { sheet = false; onImportClipboard() }
                SheetItem(NbIcons.Rss, T("添加订阅")) { sheet = false; dialog = "sub" }
                SheetItem(NbIcons.Folder, T("新建分组")) { sheet = false; dialog = "group" }
                SheetItem(NbIcons.Pen, T("手动添加节点")) { sheet = false; onEdit(0) }
            }
        }
    }

    qr?.let { (name, link) -> QrDialog(name, link) { qr = null } }

    when (dialog) {
        "sub" -> TwoFieldDialog(
            title = T("添加订阅"), first = T("名称"), second = T("订阅地址"), initialFirst = "", initialSecond = "",
            confirm = T("添加"), onDismiss = { dialog = null },
        ) { name, url ->
            dialog = null
            updating = true
            fetching = T("正在获取订阅节点…")
            scope.launch {
                Repo.addSubscription(name, url).onSuccess { toast(it) }.onFailure { toast(T("获取订阅失败：") + (it.message ?: T("未知错误"))) }
                updating = false
                fetching = null
            }
        }
        "group" -> TwoFieldDialog(
            title = T("新建分组"), first = T("分组名称"), second = null, initialFirst = "", initialSecond = "",
            confirm = T("创建"), onDismiss = { dialog = null },
        ) { name, _ ->
            dialog = null
            scope.launch { Repo.addGroup(name).onFailure { toast(it.message ?: T("创建失败")) } }
        }
        "edit-group" -> if (group != null) {
            TwoFieldDialog(
                title = T("编辑分组"), first = T("名称"), second = if (group.isSubscription) T("订阅地址") else null,
                initialFirst = group.name, initialSecond = group.url, confirm = T("保存"), onDismiss = { dialog = null },
            ) { name, url ->
                dialog = null
                scope.launch { Repo.editGroup(group, name, url).onFailure { toast(it.message ?: T("保存失败")) } }
            }
        }
        "delete-group" -> if (group != null) {
            NbAlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(T("删除分组")) },
                text = { Text(T("“%s”和其中的 %d 个节点都会被删除。", group.displayName, profiles.size)) },
                confirmButton = {
                    TextButton(onClick = { dialog = null; scope.launch { Repo.deleteGroup(group.id) } }) { Text(T("删除"), color = c.bad) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(T("取消")) } },
            )
        }
        "delete-nodes" -> {
            val ids = picked.orEmpty()
            NbAlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(T("删除节点")) },
                text = { Text(T("要删除选中的 %d 个节点吗？", ids.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        picked = null
                        scope.launch { Repo.deleteProfiles(ids) }
                    }) { Text(T("删除"), color = c.bad) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(T("取消")) } },
            )
        }
    }
}

@Composable
private fun SubscriptionCard(g: Group, count: Int, updating: Boolean, onRefresh: () -> Unit) {
    val c = Nb.c
    NbCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(T("%s · %d 个节点", g.name, count), style = NbType.bodyBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (updating) T("正在更新…") else fmtAgo(g.updatedAt), style = NbType.small, color = c.muted)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.accentSoft)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(updating, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "refresh") { busy ->
                    if (busy) {
                        // the spinner only animates while it is on screen
                        val spin = rememberInfiniteTransition(label = "spin")
                        val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
                        Icon(NbIcons.Refresh, null, tint = c.accentInk, modifier = Modifier.size(20.dp).rotate(angle))
                    } else {
                        Icon(NbIcons.Refresh, T("更新订阅"), tint = c.accentInk, modifier = Modifier.size(20.dp))
                    }
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
}

/** picked: null in normal mode, otherwise whether the row is ticked in multi-select mode. */
@Composable
private fun NodeRow(
    p: Profile,
    selected: Boolean,
    testing: Boolean,
    picked: Boolean?,
    modifier: Modifier,
    onClick: () -> Unit,
    onPick: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onCopy: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Nb.c
    var menu by remember { mutableStateOf(false) }
    val lit = if (picked != null) picked else selected
    val bg by animateColorAsState(if (lit) c.accentSoft.copy(alpha = if (c.dark) 0.6f else 0.55f) else c.card, label = "rowbg")
    val border by animateColorAsState(if (lit) c.accent.copy(alpha = 0.6f) else c.cardBorder, label = "rowb")
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(if (lit) 1.5.dp else 1.dp, border, RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = { if (picked == null) menu = true })
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (picked != null) {
                Box(
                    Modifier
                        .padding(end = 12.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (picked) c.accentStrong else Color.Transparent)
                        .border(1.5.dp, if (picked) c.accentStrong else c.muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (picked) Icon(NbIcons.Check, T("已选中"), tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    p.name, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
                Text(protocolName(p.type), style = NbType.small, color = c.muted)
            }
            if (testing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
            } else {
                LatencyText(p.latency)
            }
            val checkScale by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.55f), label = "check")
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) {
                if (checkScale > 0.01f) {
                    Icon(
                        NbIcons.Check, if (selected) T("当前节点") else null, tint = c.accentInk,
                        modifier = Modifier.size(20.dp).graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkScale.coerceIn(0f, 1f)
                        },
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(T("编辑")) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(T("测速")) }, onClick = { menu = false; onTest() })
            DropdownMenuItem(text = { Text(T("复制分享链接")) }, onClick = { menu = false; onCopy() })
            DropdownMenuItem(text = { Text(T("显示二维码")) }, onClick = { menu = false; onQr() })
            DropdownMenuItem(text = { Text(T("多选")) }, onClick = { menu = false; onPick() })
            DropdownMenuItem(text = { Text(T("删除"), color = c.bad) }, onClick = { menu = false; onDelete() })
        }
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
    onConfirm: (String, String) -> Unit,
) {
    var a by remember { mutableStateOf(initialFirst) }
    var b by remember { mutableStateOf(initialSecond) }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(a, { a = it }, label = { Text(first) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (second != null) {
                    OutlinedTextField(b, { b = it }, label = { Text(second) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(a.trim(), b.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T("取消")) } },
    )
}

fun copyToClipboard(text: String) {
    dev.efah.nextbox.App.clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NextBox", text))
}
