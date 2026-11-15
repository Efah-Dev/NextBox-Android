package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Policy
import dev.efah.nextbox.data.PolicyNode
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch

/**
 * A policy group ("分组代理"): nodes chosen by region, keyword or by hand,
 * used automatically (the fastest) or as picked. Rules can send traffic to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PolicyEditorScreen(id: Long, onClose: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    val existing = policies.firstOrNull { it.id == id }
    var p by remember { mutableStateOf(existing?.toPolicy() ?: Policy(name = "", match = "country", mode = "auto")) }
    var preview by remember { mutableStateOf<List<PolicyNode>>(emptyList()) }
    var allRegions by remember { mutableStateOf(false) }
    var pickNodes by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { all = Repo.allProfiles() }
    LaunchedEffect(p) { preview = Repo.previewPolicy(p) }

    GlassScreen(header = {
        TopBar(if (id == 0L) T("新建策略组") else T("编辑策略组"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) {
            if (id != 0L) BarIcon(NbIcons.Trash, T("删除"), tint = c.bad) { confirmDelete = true }
        }
    }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = LocalBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(p.name, { p = p.copy(name = it) }, label = { Text(T("名称")) }, placeholder = { Text(T("例如：日本自动")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text(T("怎么选节点"), style = NbType.section, color = c.muted, modifier = Modifier.padding(start = 4.dp))
            val matches = listOf("country", "keyword", "manual")
            Segmented(listOf(T("按地区"), T("按关键字"), T("手动挑选")), matches.indexOf(p.match).coerceAtLeast(0), onSelect = { p = p.copy(match = matches[it]) })
            when (p.match) {
                "country" -> {
                    val places = Repo.places
                    val shown = if (allRegions) places else places.take(12)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { pl -> Chip("${pl.code} ${pl.name}", p.country == pl.code, onClick = { p = p.copy(country = pl.code) }) }
                        if (!allRegions && places.size > 12) Chip(T("更多…"), false, onClick = { allRegions = true })
                    }
                    Text(T("按节点名称识别地区，例如「东京」「JP」和国旗。"), style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp))
                }
                "keyword" -> OutlinedTextField(
                    p.keyword, { p = p.copy(keyword = it) }, singleLine = true,
                    label = { Text(T("关键字")) }, placeholder = { Text("IPLC 专线") },
                    supportingText = { Text(T("节点名称包含任意一个就算，用空格隔开")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> Button(
                    onClick = { pickNodes = true },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentSoft, contentColor = c.accentInk),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (p.members.isEmpty()) T("选择节点") else T("已选 %d 个节点，点击修改", p.members.size)) }
            }

            if (p.match != "manual") {
                Text(T("从哪个分组里找"), style = NbType.section, color = c.muted, modifier = Modifier.padding(start = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(T("所有分组"), p.groupId == 0L, onClick = { p = p.copy(groupId = 0) })
                    state.groups.forEach { g -> Chip(g.displayName, p.groupId == g.id, onClick = { p = p.copy(groupId = g.id) }) }
                }
            }

            Text(T("使用方式"), style = NbType.section, color = c.muted, modifier = Modifier.padding(start = 4.dp))
            Segmented(listOf(T("自动用最快的"), T("我来选")), if (p.mode == "select") 1 else 0, onSelect = { p = p.copy(mode = if (it == 1) "select" else "auto") })
            Text(
                if (p.mode == "select") T("在下面的列表里点一个节点。") else T("连接后每 10 分钟测一次，自动用延迟最低的节点。"),
                style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp),
            )

            Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(T("匹配到 %d 个节点", preview.size), style = NbType.section.copy(fontWeight = FontWeight.Bold), color = c.text)
                if (p.match != "manual") Text(T("订阅更新后自动跟着变"), style = NbType.small, color = c.muted)
            }
            if (preview.isNotEmpty()) {
                NbCard(padding = PaddingValues(0.dp)) {
                    preview.take(30).forEachIndexed { i, n ->
                        if (i > 0) HorizontalDivider(color = c.line, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
                        val chosen = p.mode == "select" && (p.selected == n.id || (p.selected == 0L && i == 0))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(enabled = p.mode == "select") { p = p.copy(selected = n.id) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(n.name, style = NbType.body.copy(fontSize = 14.sp), color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (chosen) {
                                Text(
                                    T("正在用"), color = c.accentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(7.dp)).background(c.accentSoft).padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                            LatencyText(n.latency)
                        }
                    }
                }
                if (preview.size > 30) Text(T("还有 %d 个没有列出", preview.size - 30), style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp))
            }

            Button(
                onClick = {
                    scope.launch {
                        Repo.savePolicy(p).onSuccess { onClose() }.onFailure { toast(it.message ?: T("保存失败")) }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accentStrong),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(T("保存"), fontWeight = FontWeight.Bold) }
        }
    }

    if (pickNodes) {
        var chosen by remember { mutableStateOf(p.members.toSet()) }
        var query by remember { mutableStateOf("") }
        val q = query.trim().lowercase()
        NbAlertDialog(
            onDismissRequest = { pickNodes = false },
            title = { Text(T("选择节点")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(T("搜索节点")) }, modifier = Modifier.fillMaxWidth())
                    val shown = all.filter { q.isEmpty() || it.name.lowercase().contains(q) }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(shown.size) { i ->
                            val n = shown[i]
                            val on = n.id in chosen
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { chosen = if (on) chosen - n.id else chosen + n.id },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(on, { chosen = if (on) chosen - n.id else chosen + n.id }, colors = CheckboxDefaults.colors(checkedColor = c.accentStrong))
                                Text(n.name, style = NbType.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                LatencyText(n.latency)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    p = p.copy(members = all.map { it.id }.filter { it in chosen })
                    pickNodes = false
                }) { Text(T("确定")) }
            },
            dismissButton = { TextButton(onClick = { pickNodes = false }) { Text(T("取消")) } },
        )
    }

    if (confirmDelete) {
        NbAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(T("删除策略组")) },
            text = {
                Text(
                    if ((existing?.rules ?: 0) > 0) T("有 %d 条规则或场景用到它，删除后改走首页选中的节点。", existing?.rules ?: 0) else T("确定删除“%s”吗？", p.name),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { Repo.deletePolicy(id).onSuccess { onClose() } }
                }) { Text(T("删除"), color = c.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(T("取消")) } },
        )
    }
}
