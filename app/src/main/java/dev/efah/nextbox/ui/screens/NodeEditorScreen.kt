package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.NbJson
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

private const val TEMPLATE = """{
  "type": "vless",
  "server": "example.com",
  "server_port": 443,
  "uuid": "",
  "tls": {
    "enabled": true,
    "server_name": "example.com"
  }
}"""

/**
 * Edits a node as its sing-box outbound JSON (the Windows app's "JSON" tab).
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
    var json by remember { mutableStateOf(if (id == 0L) TEMPLATE else "") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        val p = Repo.profile(id) ?: return@LaunchedEffect
        original = p
        name = p.name
        json = pretty.encodeToString(JsonObject.serializer(), p.outbound)
    }

    fun save() {
        val ob = runCatching { NbJson.parseToJsonElement(json).jsonObject }.getOrElse {
            error = "JSON 格式错误：${it.message}"
            return
        }
        val p = (original ?: Profile(groupId = state.currentGroupId)).copy(name = name.trim(), outbound = ob)
        scope.launch {
            Repo.saveProfile(p)
                .onSuccess { toast("已保存"); onClose() }
                .onFailure { error = it.message }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(
            if (id == 0L) "添加节点" else "编辑节点",
            leading = { BarIcon(NbIcons.Back, "返回", onClose) },
        ) {
            Button(
                onClick = ::save,
                colors = ButtonDefaults.buttonColors(containerColor = c.accentStrong),
                modifier = Modifier.padding(end = 8.dp),
            ) { Text("保存") }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                json, { json = it; error = null },
                label = { Text("sing-box 出站配置（JSON）") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = c.text),
                isError = error != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
            )
            if (error != null) Text(error!!, style = NbType.small, color = c.bad)
            Text(
                "不需要写 tag。分享链接可以直接在「节点」页从剪贴板导入，表单编辑会在后续版本加入。",
                style = NbType.small, color = c.muted,
            )
        }
    }
}
