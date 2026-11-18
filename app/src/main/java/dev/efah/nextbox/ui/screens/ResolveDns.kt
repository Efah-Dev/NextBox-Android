package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import dev.efah.nextbox.ui.NbIcons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * How "bypass mainland China" learns the IP address of a domain name
 * (Settings.bypassDns): with the remote DNS through the proxy (the default),
 * which is never tampered with, or with the direct DNS, which recognises
 * Chinese CDNs but can answer blocked sites with wrong addresses, and the
 * proxy is then sent to those. Known foreign sites are not resolved either way.
 */
object ResolveDns {
    val values = listOf("remote", "direct")
    fun labels() = listOf(T("接管 DNS 解析"), T("直连 DNS 解析"))
    fun label(value: String) = labels()[values.indexOf(value).coerceAtLeast(0)]
    /** For the tag on the home page. */
    fun short(value: String) = if (value == "direct") T("直连 DNS") else T("接管 DNS")
    fun hint(value: String) = if (value == "direct") T("能认出国内 CDN，被封锁的网站可能解析出错") else T("经代理用远程 DNS 解析，不会被污染")
    fun desc(value: String): String =
        if (value == "direct") T("用直连 DNS 解析，能认出国内 CDN 网站；但被封锁的网站可能拿到错误的地址，导致打不开。")
        else T("用远程 DNS 经代理解析，不会被污染；部分国内 CDN 网站可能被当成境外而走代理。")
}

/**
 * The choice between the two: the direct DNS only after a warning. Calls
 * content with the selected index and the function that picks one.
 */
@Composable
private fun ResolveDnsPicker(value: String, content: @Composable (Int, (Int) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var warn by remember { mutableStateOf(false) }
    fun save(v: String) = scope.launch { Repo.updateSettings(buildJsonObject { put("bypassDns", v) }) }
    content(ResolveDns.values.indexOf(value).coerceAtLeast(0)) { i ->
        val v = ResolveDns.values[i]
        if (v == value) return@content
        if (v == "direct") warn = true else save(v)
    }
    if (warn) {
        NbAlertDialog(
            onDismissRequest = { warn = false },
            title = { Text(T("改用直连 DNS 解析？")) },
            text = {
                Text(
                    T("直连 DNS 会把一些被封锁的网站解析成错误的地址，代理随后连到这些地址，表现为已连接却打不开网页。已知的境外网站不受影响，冷门网站可能受影响。只有在国内网站被误判走代理、又确实需要直连时再用。"),
                )
            },
            confirmButton = { TextButton(onClick = { warn = false; save("direct") }) { Text(T("仍然使用"), color = Nb.c.bad) } },
            dismissButton = { TextButton(onClick = { warn = false }) { Text(T("取消")) } },
        )
    }
}

/** The choice with what it means, for the "bypass mainland China" step. */
@Composable
fun ResolveDnsChoice(value: String) {
    val c = Nb.c
    Text(T("解析方式"), style = NbType.bodyBold.copy(fontSize = 14.sp), color = c.text)
    ResolveDnsPicker(value) { selected, pick ->
        Segmented(ResolveDns.labels(), selected, onSelect = pick, modifier = Modifier.padding(top = 8.dp), height = 38.dp)
    }
    Text(ResolveDns.desc(value), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 6.dp))
}

/**
 * The choice as a small tag between "bypass mainland China" and its switch
 * on the home page, opening a menu with both and what they mean.
 */
@Composable
fun ResolveDnsChip(value: String, modifier: Modifier = Modifier) {
    val c = Nb.c
    var open by remember { mutableStateOf(false) }
    ResolveDnsPicker(value) { selected, pick ->
        Box(modifier) {
            Row(
                Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(c.accentSoft)
                    .clickable { open = true }.padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ResolveDns.short(value), color = c.accentInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Icon(NbIcons.Chevron, null, tint = c.accentInk, modifier = Modifier.padding(start = 2.dp).size(14.dp).rotate(90f))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.card) {
                ResolveDns.labels().forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(vertical = 4.dp).widthIn(max = 240.dp)) {
                                Text(label, color = if (i == selected) c.accentInk else c.text, fontSize = 14.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal)
                                Text(ResolveDns.hint(ResolveDns.values[i]), style = NbType.small, color = c.muted)
                            }
                        },
                        leadingIcon = {
                            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                if (i == selected) Icon(NbIcons.Check, null, tint = c.accentInk, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = { open = false; pick(i) },
                    )
                }
            }
        }
    }
}
