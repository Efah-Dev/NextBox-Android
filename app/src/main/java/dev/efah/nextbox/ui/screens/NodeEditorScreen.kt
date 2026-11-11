package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.NbJson
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.protocolName
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbCard
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

private val types = listOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic", "anytls", "socks", "http")

/** Protocols that always run over TLS. */
private val tlsAlways = setOf("hysteria2", "tuic", "anytls")
private val tlsOptional = setOf("vless", "vmess", "trojan", "http")
private val withTransport = setOf("vless", "vmess", "trojan")

private val ssMethods = listOf(
    "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
    "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305", "none",
)

private fun blank(type: String): JsonObject = buildJsonObject {
    put("type", type)
    put("server", "")
    put("server_port", 443)
    if (type in tlsAlways || type == "vless" || type == "trojan") put("tls", buildJsonObject { put("enabled", true) })
}

// ---- reading and writing "a.b.c" paths of an immutable JSON object ----

private fun JsonObject.at(path: String): JsonElement? {
    var cur: JsonElement? = this
    for (k in path.split('.')) cur = (cur as? JsonObject)?.get(k)
    return cur
}

private fun JsonObject.text(path: String): String = when (val v = at(path)) {
    is JsonPrimitive -> v.content
    is JsonArray -> v.joinToString(",") { (it as? JsonPrimitive)?.content.orEmpty() }
    else -> ""
}

private fun JsonObject.flag(path: String): Boolean = (at(path) as? JsonPrimitive)?.booleanOrNull == true

/** Returns a copy with path set to value; null removes it, and objects left empty are removed too. */
private fun JsonObject.with(path: String, value: JsonElement?): JsonObject {
    val keys = path.split('.')
    fun rec(obj: JsonObject, i: Int): JsonObject {
        val m = obj.toMutableMap()
        val k = keys[i]
        if (i == keys.lastIndex) {
            if (value == null) m.remove(k) else m[k] = value
        } else {
            val child = rec(obj[k] as? JsonObject ?: JsonObject(emptyMap()), i + 1)
            if (child.isEmpty()) m.remove(k) else m[k] = child
        }
        return JsonObject(m)
    }
    return rec(this, 0)
}

/**
 * Edits a node with a form for the common fields of each protocol, or as the
 * raw sing-box outbound JSON (everything the form does not show is kept).
 * id 0 creates a node in the current group.
 */
@Composable
fun NodeEditorScreen(id: Long, onClose: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    var original by remember { mutableStateOf<Profile?>(null) }
    var name by remember { mutableStateOf("") }
    var ob by remember { mutableStateOf(blank("vless")) }
    var mode by remember { mutableIntStateOf(0) } // 0 form, 1 JSON
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        val p = Repo.profile(id) ?: return@LaunchedEffect
        original = p
        name = p.name
        ob = p.outbound
    }

    fun parseJson(): JsonObject? = runCatching { NbJson.parseToJsonElement(json).jsonObject }.getOrElse {
        error = T("JSON 格式错误：") + it.message
        null
    }

    fun save() {
        var out = if (mode == 1) parseJson() ?: return else ob
        val type = out.text("type")
        if (type in tlsAlways) out = out.with("tls.enabled", JsonPrimitive(true))
        if (out.text("server").isBlank()) {
            error = T("请填写服务器地址")
            return
        }
        val p = (original ?: Profile(groupId = state.currentGroupId)).copy(name = name.trim(), outbound = out)
        scope.launch {
            Repo.saveProfile(p)
                .onSuccess { toast(T("已保存")); onClose() }
                .onFailure { error = it.message }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(
            if (id == 0L) T("添加节点") else T("编辑节点"),
            leading = { BarIcon(NbIcons.Back, T("返回"), onClose) },
        ) {
            Button(
                onClick = ::save,
                colors = ButtonDefaults.buttonColors(containerColor = c.accentStrong),
                modifier = Modifier.padding(end = 8.dp),
            ) { Text(T("保存")) }
        }
        Segmented(
            listOf(T("表单"), "JSON"), mode,
            onSelect = { m ->
                if (m == mode) return@Segmented
                error = null
                if (m == 1) {
                    json = pretty.encodeToString(JsonObject.serializer(), ob)
                    mode = 1
                } else {
                    parseJson()?.let { ob = it; mode = 0 }
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), height = 40.dp,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = LocalBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) Text(error!!, style = NbType.small, color = c.bad, modifier = Modifier.padding(horizontal = 4.dp))
            if (mode == 1) {
                OutlinedTextField(
                    json, { json = it; error = null },
                    label = { Text(T("sing-box 出站配置（JSON）")) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = c.text),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
                )
                Text(T("不需要写 tag。保存时名称仍取自表单里的「名称」。"), style = NbType.small, color = c.muted)
            } else {
                Form(name, { name = it }, ob) { ob = it; error = null }
            }
        }
    }
}

@Composable
private fun ColumnScope.Form(name: String, onName: (String) -> Unit, ob: JsonObject, set: (JsonObject) -> Unit) {
    val type = ob.text("type")

    @Composable
    fun Field(label: String, path: String, number: Boolean = false, mono: Boolean = false, hint: String = "") {
        OutlinedTextField(
            ob.text(path),
            { v ->
                val t = v.trim()
                set(
                    ob.with(
                        path,
                        when {
                            t.isEmpty() -> null
                            number -> t.toIntOrNull()?.let { JsonPrimitive(it) } ?: return@OutlinedTextField
                            else -> JsonPrimitive(t)
                        },
                    ),
                )
            },
            label = { Text(label) },
            placeholder = if (hint.isEmpty()) null else ({ Text(hint) }),
            singleLine = true,
            textStyle = if (mono) TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Nb.c.text) else TextStyle(fontSize = 15.sp, color = Nb.c.text),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Section(T("基本")) {
        OutlinedTextField(name, onName, label = { Text(T("名称")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Choice(T("协议"), type, types.map { it to protocolName(it) }) { t ->
            if (t != type) set(blank(t).with("server", ob.at("server")).with("server_port", ob.at("server_port")))
        }
        Field(T("服务器地址"), "server", hint = "example.com")
        Field(T("端口"), "server_port", number = true)
    }

    Section(T("认证")) {
        when (type) {
            "vless" -> {
                Field("UUID", "uuid", mono = true)
                Choice("Flow", ob.text("flow"), listOf("" to T("无"), "xtls-rprx-vision" to "xtls-rprx-vision")) {
                    set(ob.with("flow", if (it.isEmpty()) null else JsonPrimitive(it)))
                }
            }
            "vmess" -> {
                Field("UUID", "uuid", mono = true)
                Choice(T("加密方式"), ob.text("security").ifEmpty { "auto" }, listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none").map { it to it }) {
                    set(ob.with("security", JsonPrimitive(it)))
                }
                Field("Alter ID", "alter_id", number = true, hint = "0")
            }
            "trojan", "anytls" -> Field(T("密码"), "password", mono = true)
            "shadowsocks" -> {
                Choice(T("加密方式"), ob.text("method").ifEmpty { ssMethods[3] }, ssMethods.map { it to it }) {
                    set(ob.with("method", JsonPrimitive(it)))
                }
                Field(T("密码"), "password", mono = true)
            }
            "hysteria2" -> {
                Field(T("密码"), "password", mono = true)
                OutlinedTextField(
                    ob.text("obfs.password"),
                    { v ->
                        val t = v.trim()
                        set(
                            if (t.isEmpty()) {
                                ob.with("obfs", null)
                            } else {
                                ob.with("obfs.type", JsonPrimitive("salamander")).with("obfs.password", JsonPrimitive(t))
                            },
                        )
                    },
                    label = { Text(T("混淆密码（可选）")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Field(T("上行带宽 Mbps（可选）"), "up_mbps", number = true)
                Field(T("下行带宽 Mbps（可选）"), "down_mbps", number = true)
            }
            "tuic" -> {
                Field("UUID", "uuid", mono = true)
                Field(T("密码"), "password", mono = true)
                Choice(T("拥塞控制"), ob.text("congestion_control").ifEmpty { "cubic" }, listOf("cubic", "new_reno", "bbr").map { it to it }) {
                    set(ob.with("congestion_control", JsonPrimitive(it)))
                }
            }
            "socks", "http" -> {
                Field(T("用户名（可选）"), "username")
                Field(T("密码（可选）"), "password", mono = true)
            }
        }
    }

    if (type in tlsAlways || type in tlsOptional) {
        Section("TLS") {
            val on = type in tlsAlways || ob.flag("tls.enabled")
            if (type in tlsOptional) {
                Toggle(T("启用 TLS"), on) { v -> set(if (v) ob.with("tls.enabled", JsonPrimitive(true)) else ob.with("tls", null)) }
            }
            if (on) {
                Field(T("SNI（服务器名称）"), "tls.server_name", hint = "example.com")
                OutlinedTextField(
                    ob.text("tls.alpn"),
                    { v ->
                        val items = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        set(ob.with("tls.alpn", if (items.isEmpty()) null else JsonArray(items.map { JsonPrimitive(it) })))
                    },
                    label = { Text(T("ALPN（可选，逗号分隔）")) }, placeholder = { Text("h2,http/1.1") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Toggle(T("允许不安全的证书"), ob.flag("tls.insecure")) { v ->
                    set(ob.with("tls.insecure", if (v) JsonPrimitive(true) else null))
                }
                if (type in tlsOptional) {
                    Choice(
                        T("uTLS 指纹"), ob.text("tls.utls.fingerprint"),
                        listOf("" to T("无")) + listOf("chrome", "firefox", "safari", "ios", "android", "edge", "random").map { it to it },
                    ) { fp ->
                        set(
                            if (fp.isEmpty()) {
                                ob.with("tls.utls", null)
                            } else {
                                ob.with("tls.utls.enabled", JsonPrimitive(true)).with("tls.utls.fingerprint", JsonPrimitive(fp))
                            },
                        )
                    }
                }
                if (type == "vless" || type == "trojan") {
                    val reality = ob.flag("tls.reality.enabled")
                    Toggle("Reality", reality) { v ->
                        set(if (v) ob.with("tls.reality.enabled", JsonPrimitive(true)) else ob.with("tls.reality", null))
                    }
                    if (reality) {
                        Field(T("公钥"), "tls.reality.public_key", mono = true)
                        Field("Short ID", "tls.reality.short_id", mono = true)
                    }
                }
            }
        }
    }

    if (type in withTransport) {
        Section(T("传输")) {
            val t = ob.text("transport.type")
            Choice(T("传输方式"), t, listOf("" to "TCP", "ws" to "WebSocket", "grpc" to "gRPC", "httpupgrade" to "HTTPUpgrade")) { v ->
                set(if (v.isEmpty()) ob.with("transport", null) else ob.with("transport", buildJsonObject { put("type", v) }))
            }
            when (t) {
                "ws" -> {
                    Field(T("路径"), "transport.path", hint = "/")
                    Field(T("Host（可选）"), "transport.headers.Host")
                }
                "httpupgrade" -> {
                    Field(T("路径"), "transport.path", hint = "/")
                    Field(T("Host（可选）"), "transport.host")
                }
                "grpc" -> Field(T("服务名称"), "transport.service_name")
            }
        }
    }

    Text(
        T("表单只列出常用字段，其余字段可以切到 JSON 编辑，两边的修改会互相保留。"),
        style = NbType.small, color = Nb.c.muted, modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    NbCard {
        Text(title, style = NbType.section, color = Nb.c.muted, modifier = Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Nb.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = NbType.body, color = c.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accentStrong, checkedThumbColor = Color.White,
                uncheckedTrackColor = c.card, uncheckedBorderColor = c.muted, uncheckedThumbColor = c.muted,
            ),
        )
    }
}

/** A labelled value that opens a menu of options (value to label). */
@Composable
private fun Choice(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val c = Nb.c
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = NbType.body, color = c.text, modifier = Modifier.weight(1f))
            Text(options.firstOrNull { it.first == value }?.second ?: value, style = NbType.body, color = c.accentInk)
            Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}
