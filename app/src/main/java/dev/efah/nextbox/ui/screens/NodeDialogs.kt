package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.ClashImport
import dev.efah.nextbox.data.ClashInfo
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.ChoiceCard
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A row with a check box, a title and what it means; [enabled] false greys it out. */
@Composable
fun CheckRow(checked: Boolean, title: String, sub: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = Nb.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onChange(!checked) }.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onChange, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = c.accentStrong))
        Column(Modifier.weight(1f)) {
            Text(title, style = NbType.body, color = if (enabled) c.text else c.muted)
            if (sub.isNotEmpty()) Text(sub, style = NbType.small.copy(fontSize = 12.5.sp), color = c.muted)
        }
    }
}

/**
 * Before the first test of a whole group: what happens to its order
 * afterwards. Asked once; the answer is kept (Settings.sortAfterTest) and
 * changed in Settings → 测速 later. [onStart] gets the order to sort by, null
 * to keep it.
 */
@Composable
fun SortAfterTestDialog(count: Int, onDismiss: () -> Unit, onStart: (sortBy: String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf("default") }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T("测速 %d 个节点", count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(T("测完以后节点怎么排？"), style = NbType.small, color = Nb.c.muted)
                sortChoices().forEach { (value, title, sub) ->
                    ChoiceCard(choice == value, title, sub, onClick = { choice = value })
                }
                Text(T("只问这一次。以后要改，前往「设置 → 测速 → 测速后」。"), style = NbType.small, color = Nb.c.muted, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            NbButton(T("开始测速"), onClick = {
                scope.launch { Repo.updateSettings(buildJsonObject { put("sortAfterTest", choice) }, reload = false) }
                onStart(choice.takeIf { it != "none" })
            })
        },
        dismissButton = { NbButton(T("取消"), onDismiss, kind = BtnKind.Secondary) },
    )
}

/** The orders a group can have after a speed test (Settings.sortAfterTest values). */
@Composable
fun sortChoices() = listOf(
    Triple("default", T("使用默认排序"), T("恢复成订阅里的顺序")),
    Triple("none", T("保持现在的顺序"), T("只显示延迟，不动位置")),
    Triple("latency", T("按延迟排序"), T("最快的在最上面，超时的排到最后")),
)

/**
 * The parts of a Clash subscription besides its nodes: proxy groups, rules
 * and DNS settings are converted and follow every update. Rules are not
 * ticked by default: they would change the user's own routing.
 */
@Composable
fun ClashImportDialog(groupId: Long, info: ClashInfo, onDismiss: () -> Unit) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val p = info.preview
    val groups = p.groups.orEmpty()
    val cur = info.current
    var g by remember { mutableStateOf(cur?.groups ?: groups.isNotEmpty()) }
    var r by remember { mutableStateOf(cur?.rules ?: false) }
    var d by remember { mutableStateOf(cur?.dns ?: (p.dns > 0)) }
    // the subscription's own DNS is taken by default: it is only offered when
    // its config names a server that is not a public one
    val sub = p.subDns
    var sd by remember { mutableStateOf(cur?.subDns ?: (sub != null)) }
    val isClash = groups.isNotEmpty() || p.ruleTotal > 0 || p.dns > 0
    fun apply(opt: ClashImport) {
        onDismiss()
        scope.launch {
            Repo.applyClash(groupId, opt)
                .onSuccess { toast.ok(it.ifEmpty { T("只导入节点") }) }
                .onFailure { toast.error(it.message ?: T("导入失败")) }
        }
    }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isClash) T("订阅附带的分组和规则") else T("这个订阅带有专属 DNS 服务器")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    T("节点总以订阅为准。勾选的部分转换成 NextBox 自己的设置，以后随订阅更新；之后可以在分组菜单里重新选择。"),
                    style = NbType.small, color = c.muted, modifier = Modifier.padding(bottom = 8.dp),
                )
                CheckRow(true, T("节点"), T("%d 个", info.nodes), enabled = false) {}
                if (isClash) {
                    val names = groups.take(3).joinToString("、") + if (groups.size > 3) T(" 等") else ""
                    CheckRow(g && groups.isNotEmpty(), T("分组 → 分组代理"), if (groups.isEmpty()) T("没有含本订阅节点的分组") else T("%d 个：%s", groups.size, names), enabled = groups.isNotEmpty()) { g = it }
                    CheckRow(r && p.rules > 0, T("规则 → 规则集"), T("能转换 %d / %d 条，分成 %d 个规则集", p.rules, p.ruleTotal, p.sets), enabled = p.rules > 0) { r = it }
                    CheckRow(d && p.dns > 0, T("DNS 设置 → DNS 复写"), if (p.dns > 0) T("%d 条", p.dns) else T("订阅里没有可用的 DNS 设置"), enabled = p.dns > 0) { d = it }
                }
                if (sub != null) {
                    val detail = listOfNotNull(
                        sub.remote.takeIf { it.isNotEmpty() }?.let { T("远程 DNS：%s", it) },
                        sub.resolver.takeIf { it.isNotEmpty() }?.let { T("节点域名：%s", it) },
                    ).joinToString("\n")
                    CheckRow(sd, T("订阅自带的 DNS → 这个订阅的专属 DNS"), detail) { sd = it }
                }
                if (p.ruleTotal > p.rules) {
                    Text(T("%d 条规则无法转换，导入时会跳过", p.ruleTotal - p.rules), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                }
            }
        },
        confirmButton = { NbButton(T("导入"), { apply(ClashImport(g && groups.isNotEmpty(), r && p.rules > 0, d && p.dns > 0, sd && sub != null)) }) },
        dismissButton = { NbButton(T("只导入节点"), { apply(ClashImport()) }, kind = BtnKind.Secondary) },
    )
}
