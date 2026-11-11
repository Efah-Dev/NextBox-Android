package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.EmptyHint
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A rule kind: the prefix stored in the rule line, its label and an input hint. */
private class Kind(val prefix: String, val label: String, val hint: String)

private val kinds = listOf(
    Kind("suffix", "域名后缀", "example.com"), // i18n:skip
    Kind("domain", "完整域名", "www.example.com"), // i18n:skip
    Kind("keyword", "域名关键字", "google"), // i18n:skip
    Kind("ip", "IP 或网段", "8.8.8.8 或 1.2.3.0/24"), // i18n:skip
    Kind("geosite", "网站分类", "netflix、openai、category-ads-all…"), // i18n:skip
    Kind("geoip", "国家或地区 IP", "jp、us、telegram…"), // i18n:skip
    Kind("process", "应用包名", "com.example.app"), // i18n:skip
)

private val aliases = mapOf("full" to "domain", "domain_suffix" to "suffix", "ip_cidr" to "ip")

/** Splits a stored rule line into (kind, value); lines without a prefix are suffixes or IPs. */
private fun parse(line: String): Pair<Kind, String> {
    val i = line.indexOf(':')
    if (i > 0) {
        val prefix = line.substring(0, i).lowercase().let { aliases[it] ?: it }
        kinds.firstOrNull { it.prefix == prefix }?.let { return it to line.substring(i + 1).trim() }
    }
    val isIp = line.firstOrNull()?.isDigit() == true && line.all { it.isDigit() || it == '.' || it == '/' } || line.count { it == ':' } >= 2
    return (if (isIp) kinds[3] else kinds[0]) to line
}

private fun lines(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

/**
 * Custom routing rules: what always goes direct, always through the proxy, or
 * is blocked. They are checked before "bypass mainland China" and stored in
 * the same one-rule-per-line format as the desktop app.
 */
@Composable
fun RulesScreen(onClose: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val routeScope by Prefs.routeScope.collectAsState()
    val s = state.settings
    var tab by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }

    val key = listOf("directRules", "proxyRules", "blockRules")[tab]
    val rules = lines(listOf(s.directRules, s.proxyRules, s.blockRules)[tab])

    fun save(list: List<String>) {
        scope.launch {
            Repo.updateSettings(buildJsonObject { put(key, list.joinToString("\n")) })
                .onFailure { toast(it.message ?: T("保存失败")) }
        }
    }

    GlassScreen(
        header = {
            TopBar(T("自定义规则"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) {
                TextButton(onClick = { adding = true }) { Text(T("添加"), color = c.accentInk, fontWeight = FontWeight.Bold) }
            }
            Segmented(
                listOf(T("直连 %d", lines(s.directRules).size), T("代理 %d", lines(s.proxyRules).size), T("屏蔽 %d", lines(s.blockRules).size)),
                tab, { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), height = 40.dp,
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "note") {
                Text(
                    when {
                        routeScope == Prefs.SCOPE_DIRECT && tab != 2 -> T("当前是「全部直连」，直连和代理规则暂时不起作用。")
                        tab == 0 -> T("匹配的连接不走代理，直接联网。")
                        tab == 1 -> T("匹配的连接始终走代理，即使开启了「绕过中国大陆」。")
                        else -> T("匹配的连接会被拦截，例如广告或不想让应用访问的地址。")
                    },
                    style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            if (rules.isEmpty()) {
                item(key = "empty") { EmptyHint(T("还没有规则"), T("点右上角「添加」")) }
            }
            itemsIndexed(rules) { i, line ->
                val (kind, value) = parse(line)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.card)
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        T(kind.label), fontSize = 12.sp, color = c.accentInk, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.accentSoft).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Text(
                        value, style = NbType.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    BarIcon(NbIcons.Trash, T("删除"), { save(rules.filterIndexed { j, _ -> j != i }) }, tint = c.muted)
                }
            }
        }
    }

    if (adding) {
        var kind by remember { mutableStateOf(kinds[0]) }
        var value by remember { mutableStateOf("") }
        NbAlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(listOf(T("添加直连规则"), T("添加代理规则"), T("添加屏蔽规则"))[tab]) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        kinds.forEach { k -> Chip(T(k.label), k === kind, onClick = { kind = k }) }
                    }
                    OutlinedTextField(
                        value, { value = it }, singleLine = true, placeholder = { Text(T(kind.hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (kind.prefix == "geosite" || kind.prefix == "geoip") {
                        Text(T("这类规则需要下载规则集，第一次连接时会自动下载。"), style = NbType.small, color = c.muted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = value.trim()
                    if (v.isEmpty() || v.contains('\n')) return@TextButton
                    adding = false
                    val line = if (kind.prefix == "suffix") v else "${kind.prefix}:$v"
                    if (line !in rules) save(rules + line)
                }) { Text(T("添加")) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(T("取消")) } },
        )
    }
}
