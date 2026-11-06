package dev.efah.nextbox.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Group
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.EmptyHint
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.UsageBar
import dev.efah.nextbox.ui.fmtAgo
import dev.efah.nextbox.ui.fmtBytes
import dev.efah.nextbox.ui.fmtDate
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(onEdit: (Long) -> Unit, onImportClipboard: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val state by Repo.state.collectAsState()
    val profiles by Repo.profiles.collectAsState()
    val testing by Repo.testing.collectAsState()
    val scope = rememberCoroutineScope()
    val group = state.groups.firstOrNull { it.id == state.currentGroupId }

    var sheet by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) } // "sub" | "group" | "edit-group" | "delete-group"
    var menu by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    // a blocking progress dialog while a subscription downloads
    var fetching by remember { mutableStateOf<String?>(null) }

    fun updateSub(g: Group) {
        if (updating) return
        updating = true
        fetching = "正在更新“${g.name}”…"
        scope.launch {
            Repo.updateSubscription(g.id)
                .onSuccess { toast(it) }
                .onFailure { toast("更新失败：" + (it.message ?: "未知错误")) }
            updating = false
            fetching = null
        }
    }

    fetching?.let { msg ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            NbCard(padding = PaddingValues(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = c.accent)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(msg, style = NbType.bodyBold, color = c.text)
                        Text("正在下载并解析节点，节点多时需要十几秒", style = NbType.small, color = c.muted)
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("节点") {
                if (testing.isNotEmpty()) {
                    BarIcon(NbIcons.Close, "停止测速", { Repo.cancelTest() })
                } else {
                    BarIcon(NbIcons.Bolt, "全部测速", { Repo.testLatency(profiles.map { it.id }) })
                }
                Box {
                    BarIcon(NbIcons.More, "更多", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("按延迟排序") }, onClick = { menu = false; scope.launch { Repo.sortGroup("latency") } })
                        DropdownMenuItem(text = { Text("按名称排序") }, onClick = { menu = false; scope.launch { Repo.sortGroup("name") } })
                        if (group != null && !group.default) {
                            DropdownMenuItem(text = { Text("编辑分组") }, onClick = { menu = false; dialog = "edit-group" })
                            DropdownMenuItem(text = { Text("删除分组", color = c.bad) }, onClick = { menu = false; dialog = "delete-group" })
                        }
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(state.groups, key = { it.id }) { g ->
                    Chip(g.displayName, g.id == state.currentGroupId, onClick = { scope.launch { Repo.selectGroup(g.id) } })
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (group != null && group.isSubscription) {
                    item(key = "sub") {
                        SubscriptionCard(group, profiles.size, updating, onRefresh = { updateSub(group) })
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (profiles.isEmpty()) {
                    item(key = "empty") {
                        EmptyHint("这个分组还没有节点", "点右下角「添加」：订阅、剪贴板或手动添加")
                    }
                }
                items(profiles, key = { it.id }) { p ->
                    NodeRow(
                        p,
                        selected = p.id == state.selectedId,
                        testing = p.id in testing,
                        modifier = Modifier.animateItem(),
                        onSelect = { scope.launch { Repo.select(p.id) } },
                        onEdit = { onEdit(p.id) },
                        onTest = { Repo.testLatency(listOf(p.id)) },
                        onCopy = {
                            scope.launch {
                                Repo.exportLinks(listOf(p.id))
                                    .onSuccess { copyToClipboard(it); toast("已复制分享链接") }
                                    .onFailure { toast(it.message ?: "导出失败") }
                            }
                        },
                        onDelete = { scope.launch { Repo.deleteProfiles(listOf(p.id)) } },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { sheet = true },
            icon = { Icon(NbIcons.Plus, null, modifier = Modifier.size(22.dp)) },
            text = { Text("添加", fontWeight = FontWeight.Bold) },
            containerColor = c.accentStrong,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (sheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = sheetState, containerColor = c.card) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Text("添加", style = NbType.big, color = c.text, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                SheetItem(NbIcons.Clipboard, "从剪贴板导入分享链接") { sheet = false; onImportClipboard() }
                SheetItem(NbIcons.Rss, "添加订阅") { sheet = false; dialog = "sub" }
                SheetItem(NbIcons.Folder, "新建分组") { sheet = false; dialog = "group" }
                SheetItem(NbIcons.Pen, "手动添加节点") { sheet = false; onEdit(0) }
            }
        }
    }

    when (dialog) {
        "sub" -> TwoFieldDialog(
            title = "添加订阅", first = "名称", second = "订阅地址", initialFirst = "", initialSecond = "",
            confirm = "添加", onDismiss = { dialog = null },
        ) { name, url ->
            dialog = null
            updating = true
            fetching = "正在获取订阅节点…"
            scope.launch {
                Repo.addSubscription(name, url).onSuccess { toast(it) }.onFailure { toast("获取订阅失败：" + (it.message ?: "未知错误")) }
                updating = false
                fetching = null
            }
        }
        "group" -> TwoFieldDialog(
            title = "新建分组", first = "分组名称", second = null, initialFirst = "", initialSecond = "",
            confirm = "创建", onDismiss = { dialog = null },
        ) { name, _ ->
            dialog = null
            scope.launch { Repo.addGroup(name).onFailure { toast(it.message ?: "创建失败") } }
        }
        "edit-group" -> if (group != null) {
            TwoFieldDialog(
                title = "编辑分组", first = "名称", second = if (group.isSubscription) "订阅地址" else null,
                initialFirst = group.name, initialSecond = group.url, confirm = "保存", onDismiss = { dialog = null },
            ) { name, url ->
                dialog = null
                scope.launch { Repo.editGroup(group, name, url).onFailure { toast(it.message ?: "保存失败") } }
            }
        }
        "delete-group" -> if (group != null) {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("删除分组") },
                text = { Text("“${group.displayName}”和其中的 ${profiles.size} 个节点都会被删除。") },
                confirmButton = {
                    TextButton(onClick = { dialog = null; scope.launch { Repo.deleteGroup(group.id) } }) { Text("删除", color = c.bad) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun SubscriptionCard(g: Group, count: Int, updating: Boolean, onRefresh: () -> Unit) {
    val c = Nb.c
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    NbCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${g.name} · $count 个节点", style = NbType.bodyBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (updating) "正在更新…" else fmtAgo(g.updatedAt), style = NbType.small, color = c.muted)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.accentSoft)
                    .combinedClickableSafe(onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(NbIcons.Refresh, "更新订阅", tint = c.accentInk, modifier = Modifier.size(20.dp).rotate(if (updating) angle else 0f))
            }
        }
        val u = g.usage
        if (u != null && u.total > 0) {
            Spacer(Modifier.height(10.dp))
            UsageBar((u.upload + u.download).toFloat() / u.total)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("已用 ${fmtBytes(u.upload + u.download)} / ${fmtBytes(u.total)}", style = NbType.small, color = c.muted, modifier = Modifier.weight(1f))
                if (u.expire > 0) Text("到期 ${fmtDate(u.expire)}", style = NbType.small, color = c.muted)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSafe(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeRow(
    p: Profile,
    selected: Boolean,
    testing: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Nb.c
    var menu by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (selected) c.accentSoft.copy(alpha = if (c.dark) 0.6f else 0.55f) else c.card, label = "rowbg")
    val border by animateColorAsState(if (selected) c.accent.copy(alpha = 0.6f) else c.cardBorder, label = "rowb")
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(if (selected) 1.5.dp else 1.dp, border, RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onSelect, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            val checkScale by androidx.compose.animation.core.animateFloatAsState(
                if (selected) 1f else 0f,
                androidx.compose.animation.core.spring(dampingRatio = 0.55f),
                label = "check",
            )
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) {
                if (checkScale > 0.01f) {
                    Icon(
                        NbIcons.Check, if (selected) "已选中" else null, tint = c.accentInk,
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
            DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("测速") }, onClick = { menu = false; onTest() })
            DropdownMenuItem(text = { Text("复制分享链接") }, onClick = { menu = false; onCopy() })
            DropdownMenuItem(text = { Text("删除", color = c.bad) }, onClick = { menu = false; onDelete() })
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
            .combinedClickableSafe(onClick)
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
    AlertDialog(
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

fun copyToClipboard(text: String) {
    dev.efah.nextbox.App.clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NextBox", text))
}
