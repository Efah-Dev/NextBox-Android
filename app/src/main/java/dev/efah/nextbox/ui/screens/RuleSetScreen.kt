package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.Rule
import dev.efah.nextbox.data.SetItem
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.InfoIcon
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbField
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TagInput
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.TopBar
import kotlinx.coroutines.launch

/**
 * 规则集: a named rule of its own that matches by many kinds at once —
 * domains, IP ranges, apps, site categories, IP regions and rule-sets at an
 * address — and sends all of it to one target (as on NextBox for Windows).
 * Only the kinds it has are shown; more are added from the list below them.
 * Values are tags: what is not a value of its kind is never saved. index is
 * the rule set in Settings.rules, -1 for a new one (added before "bypass
 * mainland China").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleSetScreen(index: Int, onClose: () -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val policies by Repo.policies.collectAsState()
    val original = remember { state.settings.rules.getOrNull(index)?.takeIf { it.isSet } }
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var target by remember { mutableStateOf(original?.target ?: "proxy") }
    val values = remember {
        mutableStateMapOf<String, List<String>>().apply { original?.items?.forEach { put(it.kind, it.values) } }
    }
    // what is typed in each kind's field and not added yet
    val drafts = remember { mutableStateMapOf<String, String>() }
    // the kinds on screen: the ones with values, then the ones added
    val kinds = remember {
        mutableStateListOf<String>().apply {
            addAll(Route.setKinds.filter { values[it].orEmpty().isNotEmpty() })
            if (isEmpty()) add("suffix")
        }
    }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf("") }
    var pickApps by remember { mutableStateOf(false) }
    val checks = remember { mutableStateMapOf<String, String>() } // url -> "" checking, "ok", or the error
    val installed by produceState<List<AppItem>>(emptyList()) { value = InstalledApps.load(ctx) }
    LaunchedEffect(Unit) { Repo.loadPolicies() }

    fun bad(parts: List<String>) = toast.error(T("不是有效的值：%s", parts.take(3).joinToString("、")))

    fun show(kind: String) { if (kind !in kinds) kinds += kind }

    fun applyBulk() {
        val wrong = mutableListOf<String>()
        bulk.split('\n').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            val p = Route.parseItem(line)
            if (p == null || p.second.isEmpty() || p.first !in Route.setKinds) { wrong += line; return@forEach }
            val (ok, no) = if (p.first == "geoip" || p.first == "package") listOf(p.second.lowercase()) to emptyList() else RuleUi.parseValues(p.first, p.second)
            if (ok.isNotEmpty()) { values[p.first] = (values[p.first].orEmpty() + ok).distinct(); show(p.first) }
            wrong += no
        }
        if (wrong.isEmpty()) { bulk = ""; bulkOpen = false } else { bulk = wrong.joinToString("\n"); bad(wrong) }
    }

    fun save() {
        // what is still typed in a field counts too
        var wrong = emptyList<String>()
        drafts.forEach { (k, t) ->
            val (ok, no) = RuleUi.parseValues(k, t)
            if (ok.isNotEmpty()) values[k] = (values[k].orEmpty() + ok).distinct()
            wrong = wrong + no
        }
        drafts.clear()
        if (wrong.isNotEmpty()) { bad(wrong); return }
        val its = Route.setKinds.mapNotNull { k -> values[k]?.takeIf { it.isNotEmpty() }?.let { SetItem(k, it) } } +
            (original?.items?.filter { it.kind == "process" } ?: emptyList()) // the desktop app's, kept
        if (name.isBlank() || its.isEmpty()) { toast.error(T("需要名称和至少一项内容")); return }
        val set = Rule(kind = "set", name = name.trim(), target = target, items = its, disabled = original?.disabled ?: false)
        val rules = state.settings.rules.toMutableList()
        if (original != null && index < rules.size) rules[index] = set else rules.add(Route.insertAt(rules), set)
        scope.launch {
            Repo.saveRules(rules).onSuccess { toast.ok(T("已保存规则集「%s」", set.name)); onClose() }.onFailure { toast.error(it.message ?: T("保存失败")) }
        }
    }

    GlassScreen(header = {
        TopBar(if (original != null) T("编辑规则集") else T("新建规则集"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) {
            Text(
                T("保存"), color = c.accentInk, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = ::save).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 6.dp, bottom = LocalBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NbField(
                name, { name = it }, singleLine = true,
                label = { Text(T("名称")) }, placeholder = { Text(T("例如：公司内网、游戏加速")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text(T("命中后走"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                TargetChips(target, policies) { target = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(T("内容"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
                InfoIcon(
                    T("规则集"),
                    T("把域名、IP、应用、网站分类等放进同一个规则集，命中其中任何一项都走同一个去向。活动页会显示命中的规则集名称。") + "\n\n" +
                        T("输入后按回车或「+」加入，可以一次粘贴多个，用换行、逗号或空格分隔。格式不对的值不会保存。"),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(
                    if (bulkOpen) T("收起") else T("批量粘贴"), color = c.accentInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { bulkOpen = !bulkOpen }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            if (bulkOpen) {
                NbField(
                    bulk, { bulk = it }, minLines = 4,
                    label = { Text(T("每行一个")) },
                    placeholder = { Text("google.com\n1.2.3.0/24\npackage:com.example.app\ngeosite:netflix\nhttps://example.com/rules.srs") },
                    supportingText = { Text(T("不写类型时：网址是规则集网址，IP 是 IP 段，其余当作域名")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                NbButton(T("加入"), ::applyBulk, Modifier.fillMaxWidth(), enabled = bulk.isNotBlank())
            }

            kinds.forEach { kind ->
                val list = values[kind].orEmpty()
                NbCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Route.kindLabel(kind), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
                        Text(
                            listOfNotNull(kindNote(kind), list.size.takeIf { it > 0 }?.toString()).joinToString(" · "),
                            style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 6.dp).weight(1f),
                        )
                        Text(
                            if (list.isEmpty()) T("移除") else T("清空"), style = NbType.small, color = c.muted,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                if (list.isEmpty()) { kinds -= kind; drafts.remove(kind) } else values[kind] = emptyList()
                            }.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    when (kind) {
                        "geoip" -> RegionField(list.toSet()) { values["geoip"] = it.toList() }
                        "package" -> AppsField(list.toSet(), installed) { pickApps = true }
                        else -> {
                            TagInput(
                                list, { values[kind] = it },
                                placeholder = when (kind) {
                                    "suffix" -> "google.com"
                                    "domain" -> "www.google.com"
                                    "keyword" -> "google"
                                    "regex" -> "^ads?\\."
                                    "geosite" -> "netflix"
                                    "cidr" -> "1.2.3.4 / 1.2.3.0/24"
                                    else -> "https://example.com/rules.srs"
                                },
                                parse = { RuleUi.parseValues(kind, it) }, onBad = ::bad,
                                draft = drafts[kind].orEmpty(), onDraft = { drafts[kind] = it },
                                splitOn = if (kind == "regex") "" else "\n,，; ",
                                keyboard = if (kind == "url") KeyboardType.Uri else KeyboardType.Text,
                                label = { v -> if (kind == "geosite") RuleUi.services.firstOrNull { v in it.geosite }?.let { T(it.label) } ?: v else v },
                            )
                            if (kind == "geosite") {
                                Text(T("点一下加入整组域名"), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RuleUi.services.filter { it.geosite.isNotEmpty() && it.offered }.forEach { sv ->
                                        val on = list.containsAll(sv.geosite)
                                        Chip(T(sv.label), on, onClick = {
                                            values["geosite"] = if (on) list - sv.geosite.toSet() else (list + sv.geosite).distinct()
                                        })
                                    }
                                }
                            }
                            if (kind == "url") UrlChecks(list, checks)
                        }
                    }
                }
            }

            // the kinds not on screen yet
            val more = Route.setKinds.filter { it !in kinds }
            if (more.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(12.dp),
                ) {
                    Text(T("＋ 添加其他类型"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.accentInk, modifier = Modifier.padding(bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        more.forEach { k -> Chip(Route.kindLabel(k), false, onClick = { show(k); if (k == "package") pickApps = true }) }
                    }
                }
            }
            original?.items?.firstOrNull { it.kind == "process" }?.let {
                Text(T("电脑程序 %d 个，只在 Windows 版生效，会原样保留。", it.values.size), style = NbType.small, color = c.muted)
            }
            NbButton(T("保存"), ::save, Modifier.fillMaxWidth().padding(top = 4.dp))
            if (original != null) {
                NbButton(T("删除规则集"), {
                    scope.launch {
                        Repo.saveRules(state.settings.rules.filterIndexed { i, _ -> i != index })
                            .onSuccess { toast.ok(T("已删除")); onClose() }
                    }
                }, Modifier.fillMaxWidth(), kind = BtnKind.Danger)
            }
        }
    }
    if (pickApps) AppPicker(values["package"].orEmpty().toSet(), onDone = { values["package"] = it.toList(); pickApps = false }, onDismiss = { pickApps = false })
}

/** What a kind matches, in a few words (shown next to its name). */
@Composable
private fun kindNote(kind: String): String? = when (kind) {
    "suffix" -> T("含子域名")
    "domain" -> T("完全相同")
    "keyword" -> T("包含这段文字")
    else -> null
}

/** The rule-set addresses: whether each one downloads, and a button to check now. */
@Composable
private fun UrlChecks(urls: List<String>, checks: MutableMap<String, String>) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    urls.forEach { u ->
        val st = checks[u] ?: return@forEach
        Text(
            when (st) {
                "" -> T("正在检查：%s", u)
                "ok" -> T("可以使用：%s", u)
                else -> T("不能使用：%s", st)
            },
            style = NbType.small, maxLines = 2,
            color = when (st) { "ok" -> RuleUi.targetColors("direct", c).second; "" -> c.muted; else -> c.bad },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    NbButton(
        T("检查（现在下载一次）"),
        {
            urls.forEach { u ->
                checks[u] = ""
                scope.launch { Repo.checkRuleSetUrl(u).onSuccess { checks[u] = "ok" }.onFailure { checks[u] = it.message ?: T("下载失败") } }
            }
        },
        Modifier.fillMaxWidth().padding(top = 10.dp), kind = BtnKind.Secondary, enabled = urls.isNotEmpty() && checks.values.none { it == "" },
    )
    Text(
        T("支持 sing-box 的 .srs、.json 规则集和 Clash 规则源（YAML 或文本，不支持 mrs）。连接时下载，「更新规则集」时重新下载。"),
        style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 8.dp),
    )
}
