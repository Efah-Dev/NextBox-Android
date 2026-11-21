package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch

/**
 * 规则集: a named rule of its own that matches by many kinds at once —
 * domains, IP ranges, apps, site categories, IP regions and sing-box
 * rule-sets at an address — and sends all of it to one target (as on
 * NextBox for Windows). Every kind is listed; the empty ones are left out
 * when saving. index is the rule set in Settings.rules, -1 for a new one
 * (added before "bypass mainland China").
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
    // the typed kinds keep their text as typed; values are read from it on save
    val texts = remember {
        mutableStateMapOf<String, String>().apply {
            listOf("suffix", "domain", "keyword", "regex", "geosite", "cidr", "url").forEach { k -> put(k, values[k].orEmpty().joinToString("\n")) }
        }
    }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf("") }
    var pickApps by remember { mutableStateOf(false) }
    val checks = remember { mutableStateMapOf<String, String>() } // url -> "" checking, "ok", or the error
    val installed by produceState<List<AppItem>>(emptyList()) { value = InstalledApps.load(ctx) }
    LaunchedEffect(Unit) { Repo.loadPolicies() }

    fun split(kind: String, text: String): List<String> =
        (if (kind == "regex") text.split('\n') else text.split('\n', ',', '，', ' ', ';'))
            .map { RuleUi.normalize(kind, it.trim()) }.filter { it.isNotEmpty() }.distinct()

    fun items(): List<SetItem> = Route.setKinds.mapNotNull { k ->
        val v = texts[k]?.let { split(k, it) } ?: values[k].orEmpty()
        if (v.isEmpty()) null else SetItem(k, v)
    } + (original?.items?.filter { it.kind == "process" } ?: emptyList()) // the desktop app's, kept

    fun applyBulk() {
        val bad = mutableListOf<String>()
        bulk.split('\n').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            val p = Route.parseItem(line)
            when {
                p == null || p.second.isEmpty() -> bad += line
                p.first in texts -> texts[p.first] = (texts[p.first].orEmpty().trim() + "\n" + p.second).trim()
                p.first == "geoip" || p.first == "package" -> values[p.first] = (values[p.first].orEmpty() + p.second).distinct()
                else -> bad += line
            }
        }
        if (bad.isEmpty()) { bulk = ""; bulkOpen = false } else toast(T("不认识：%s", bad.take(3).joinToString("、")))
    }

    fun save() {
        val its = items()
        if (name.isBlank() || its.isEmpty()) { toast(T("需要名称和至少一项内容")); return }
        val set = Rule(kind = "set", name = name.trim(), target = target, items = its, disabled = original?.disabled ?: false)
        val rules = state.settings.rules.toMutableList()
        if (original != null && index < rules.size) rules[index] = set else rules.add(Route.insertAt(rules), set)
        scope.launch {
            Repo.saveRules(rules).onSuccess { toast(T("已保存规则集「%s」", set.name)); onClose() }.onFailure { toast(it.message ?: T("保存失败")) }
        }
    }

    GlassScreen(header = {
        TopBar(if (original != null) T("编辑规则集") else T("新建规则集"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) {
            TextButton(onClick = ::save) { Text(T("保存"), color = c.accentInk, fontWeight = FontWeight.Bold) }
        }
    }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 6.dp, bottom = LocalBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                T("把域名、IP、应用、网站分类等放进同一个规则集，它们命中后都走同一个去向。活动页会显示命中的规则集名称。多个值用换行、逗号或空格分隔。"),
                style = NbType.small, color = c.muted,
            )
            OutlinedTextField(
                name, { name = it }, singleLine = true,
                label = { Text(T("名称")) }, placeholder = { Text(T("例如：公司内网、游戏加速")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text(T("去向"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
                Spacer(Modifier.height(8.dp))
                TargetChips(target, policies) { target = it }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(T("内容"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text, modifier = Modifier.weight(1f))
                TextButton(onClick = { bulkOpen = !bulkOpen }) { Text(T("批量添加"), color = c.accentInk) }
            }
            if (bulkOpen) {
                OutlinedTextField(
                    bulk, { bulk = it }, minLines = 4,
                    label = { Text(T("每行一个")) },
                    placeholder = { Text("google.com\n1.2.3.0/24\npackage:com.example.app\ngeosite:netflix\nhttps://example.com/rules.srs") },
                    supportingText = { Text(T("不写类型时：网址是规则集网址，IP 是 IP 段，其余当作域名")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = ::applyBulk, enabled = bulk.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentStrong),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(T("加入")) }
            }

            Route.setKinds.forEach { kind ->
                NbCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
                    Text(Route.kindLabel(kind), style = NbType.bodyBold.copy(fontSize = 13.5.sp), color = c.text, modifier = Modifier.padding(bottom = 6.dp))
                    when (kind) {
                        "geoip" -> RegionField(values["geoip"].orEmpty().toSet()) { values["geoip"] = it.toList() }
                        "package" -> AppsField(values["package"].orEmpty().toSet(), installed) { pickApps = true }
                        else -> {
                            OutlinedTextField(
                                texts[kind].orEmpty(), { texts[kind] = it },
                                minLines = if (kind == "url") 2 else 1, maxLines = 8,
                                placeholder = {
                                    Text(
                                        when (kind) {
                                            "suffix" -> "google.com, youtube.com"
                                            "domain" -> "www.google.com"
                                            "keyword" -> "google"
                                            "regex" -> "^ads?\\."
                                            "geosite" -> "netflix, openai"
                                            "cidr" -> "8.8.8.8, 1.2.3.0/24"
                                            else -> "https://example.com/rules.srs"
                                        },
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = if (kind == "url") KeyboardType.Uri else KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (kind == "geosite") {
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RuleUi.services.filter { it.geosite.isNotEmpty() && it.offered }.forEach { sv ->
                                        val have = split("geosite", texts["geosite"].orEmpty())
                                        val on = have.containsAll(sv.geosite)
                                        Chip(T(sv.label), on, onClick = {
                                            texts["geosite"] = (if (on) have - sv.geosite.toSet() else (have + sv.geosite).distinct()).joinToString("\n")
                                        })
                                    }
                                }
                            }
                            if (kind == "url") {
                                val urls = split("url", texts["url"].orEmpty())
                                urls.forEach { u ->
                                    val st = checks[u]
                                    Text(
                                        when (st) {
                                            null -> u
                                            "" -> T("正在检查：%s", u)
                                            "ok" -> T("可以使用：%s", u)
                                            else -> T("不能使用：%s", st)
                                        },
                                        style = NbType.small, maxLines = 2,
                                        color = when (st) { "ok" -> RuleUi.targetColors("direct", c).second; null, "" -> c.muted; else -> c.bad },
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                TextButton(
                                    enabled = urls.isNotEmpty() && checks.values.none { it == "" },
                                    onClick = {
                                        urls.forEach { u ->
                                            checks[u] = ""
                                            scope.launch { Repo.checkRuleSetUrl(u).onSuccess { checks[u] = "ok" }.onFailure { checks[u] = it.message ?: T("下载失败") } }
                                        }
                                    },
                                ) { Text(T("检查（现在下载一次）"), color = c.accentInk) }
                                Text(T("规则集网址支持 sing-box 的 .srs、.json 规则集，以及 Clash 规则源（YAML 或文本，不支持 mrs）。连接时下载，「更新规则集」时重新下载。"), style = NbType.small, color = c.muted)
                            }
                        }
                    }
                }
            }
            original?.items?.firstOrNull { it.kind == "process" }?.let {
                Text(T("电脑程序 %d 个，只在 Windows 版生效，会原样保留。", it.values.size), style = NbType.small, color = c.muted)
            }
            Button(
                onClick = ::save,
                colors = ButtonDefaults.buttonColors(containerColor = c.accentStrong),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(T("保存"), fontWeight = FontWeight.Bold) }
            if (original != null) {
                TextButton(
                    onClick = {
                        scope.launch {
                            Repo.saveRules(state.settings.rules.filterIndexed { i, _ -> i != index })
                                .onSuccess { toast(T("已删除")); onClose() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(T("删除规则集"), color = c.bad) }
            }
        }
    }
    if (pickApps) AppPicker(values["package"].orEmpty().toSet(), onDone = { values["package"] = it.toList(); pickApps = false }, onDismiss = { pickApps = false })
}
