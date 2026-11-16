package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.DupSet
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.label
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch

/**
 * 删除重复节点, as in NextBox for Windows: the nodes of all groups that
 * connect the same way (server, port, password …, any name), which one of
 * each is kept and which go, and one tap to remove the copies.
 */
@Composable
fun DuplicatesSheet(onDismiss: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    var sets by remember { mutableStateOf<List<DupSet>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sets = Repo.findDuplicates() }
    val groups = state.groups.associateBy { it.id }
    val drops = sets.orEmpty().sumOf { it.drop.size }
    // copies in a subscription come back with its next update when the kept node is elsewhere
    val comeBack = sets.orEmpty().any { d -> d.drop.any { groups[it.groupId]?.isSubscription == true && it.groupId != d.keep.groupId } }

    NbSheet(onDismiss) {
        Text(T("删除重复节点"), style = NbType.big, color = c.text, modifier = Modifier.padding(bottom = 10.dp))
        when {
            sets == null -> Text(T("正在查找…"), style = NbType.small, color = c.muted, modifier = Modifier.padding(vertical = 16.dp))
            sets!!.isEmpty() -> {
                Text(T("没有重复的节点"), style = NbType.bodyBold, color = c.text)
                Text(T("所有分组里的节点都各不相同。"), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
            else -> {
                Text(
                    T("找到 %d 个重复节点：服务器、端口、密码等连接参数完全一样，只是名字不同。每组保留一个，其余删除。", drops),
                    style = NbType.small.copy(fontSize = 13.5.sp), color = c.text,
                )
                Box(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(max = sheetListHeight(0.45f, 420.dp))) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sets!!, key = { it.keep.id }) { d ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp))) {
                                DupRow(d.keep, if (d.running) T("正在使用") else T("保留"), keep = true, group = groups[d.keep.groupId]?.displayName.orEmpty())
                                d.drop.forEach { p ->
                                    HorizontalDivider(color = c.line, thickness = 1.dp)
                                    DupRow(p, T("删除"), keep = false, group = groups[p.groupId]?.displayName.orEmpty())
                                }
                            }
                        }
                    }
                }
                Text(
                    T("正在使用的节点不会被删除；分组代理和链式代理里用到被删节点的，会自动改用保留的那个。"),
                    style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 10.dp),
                )
                if (comeBack) {
                    Text(
                        T("订阅里的节点在下次更新订阅时会重新出现。同一个订阅里的重复节点，更新时会自动去掉。"),
                        style = NbType.small, color = c.mid, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(if (sets.isNullOrEmpty()) T("关闭") else T("取消"))
            }
            if (!sets.isNullOrEmpty()) {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            Repo.deleteDuplicates()
                                .onSuccess { toast(T("已删除 %d 个重复节点", it.toInt())); onDismiss() }
                                .onFailure { toast(it.message ?: T("删除失败")) }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = c.bad),
                    modifier = Modifier.weight(2f).height(48.dp),
                ) { Text(T("删除 %d 个", drops), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** A node of a duplicate set: a badge (kept / in use / removed), its name, protocol and group. */
@Composable
private fun DupRow(p: Profile, badge: String, keep: Boolean, group: String) {
    val c = Nb.c
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (keep) c.good else c.bad,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background((if (keep) c.good else c.bad).copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                p.label, style = NbType.body.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (keep) c.text else c.muted, textDecoration = if (keep) null else TextDecoration.LineThrough,
            )
            Text(
                (if (keep) protocolName(p.type) + " · " else "") + group,
                style = NbType.small.copy(fontSize = 11.5.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
