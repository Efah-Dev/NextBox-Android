package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BtnKind
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbButton
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.RadioTitle
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
                    T("直连 DNS 可能把被封锁的网站解析成错误的地址，表现为已连接却打不开网页（已知的境外网站不受影响）。只在国内网站被误判走代理、又确实需要直连时使用。"),
                )
            },
            confirmButton = { NbButton(T("仍然使用"), { warn = false; save("direct") }, kind = BtnKind.Danger) },
            dismissButton = { NbButton(T("取消"), { warn = false }, kind = BtnKind.Secondary) },
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
 * on the home page. It opens a bottom sheet with both choices as cards; the
 * direct one shows its warning inside the sheet and needs a second tap.
 */
@Composable
fun ResolveDnsChip(value: String, modifier: Modifier = Modifier) {
    val c = Nb.c
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(c.accentSoft)
            .clickable { open = true }.padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(ResolveDns.short(value), color = c.accentInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Icon(NbIcons.Chevron, null, tint = c.accentInk, modifier = Modifier.padding(start = 2.dp).size(14.dp).rotate(90f))
    }
    if (open) ResolveDnsSheet(value) { open = false }
}

@Composable
private fun ResolveDnsSheet(value: String, onDismiss: () -> Unit) {
    val c = Nb.c
    val scope = rememberCoroutineScope()
    // the direct DNS waits for the second tap, below its warning
    var pendingDirect by remember { mutableStateOf(false) }
    fun save(v: String) {
        scope.launch { Repo.updateSettings(buildJsonObject { put("bypassDns", v) }) }
        onDismiss()
    }
    NbSheet(onDismiss) {
        Text(T("绕过中国大陆 · 解析方式"), style = NbType.bodyBold.copy(fontSize = 17.sp), color = c.text)
        Text(T("只影响既不在国内网站名单、也不在境外网站名单里的网站"), style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        val shown = if (pendingDirect) "direct" else value
        ResolveDns.values.forEach { v ->
            val on = shown == v
            Box(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, if (on) c.accentStrong else c.line, RoundedCornerShape(14.dp))
                    .background(if (on) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable {
                        when {
                            v == "direct" && value != "direct" -> pendingDirect = true
                            v != value -> save(v)
                            else -> { pendingDirect = false }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                RadioTitle(on, ResolveDns.labels()[ResolveDns.values.indexOf(v)], ResolveDns.desc(v)) {
                    if (v == "remote") {
                        Text(
                            T("推荐"), color = c.accentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(c.accentSoft).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        if (pendingDirect) {
            Text(
                T("被封锁的网站可能拿到错误的地址，表现为已连接却打不开网页。只有国内网站被误判走代理、又确实需要直连时再用。"),
                style = NbType.small.copy(fontSize = 13.sp), color = c.text,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.mid.copy(alpha = 0.14f)).padding(12.dp),
            )
            NbButton(T("仍然使用直连"), { save("direct") }, Modifier.fillMaxWidth().padding(top = 12.dp))
            NbButton(T("取消"), { pendingDirect = false }, Modifier.fillMaxWidth().padding(top = 8.dp), kind = BtnKind.Secondary)
        } else {
            NbButton(T("完成"), onDismiss, Modifier.fillMaxWidth(), kind = BtnKind.Secondary)
        }
    }
}
