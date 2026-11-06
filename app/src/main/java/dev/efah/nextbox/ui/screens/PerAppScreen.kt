package dev.efah.nextbox.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.efah.nextbox.bg.Box
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppItem(val pkg: String, val label: String, val system: Boolean, val icon: Bitmap?)


@Composable
fun PerAppScreen(onClose: () -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val routeScope by Prefs.routeScope.collectAsState()
    val selected by Prefs.perAppList.collectAsState()
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                .filter { it.packageName != ctx.packageName }
                .map {
                    AppItem(
                        pkg = it.packageName,
                        label = pm.getApplicationLabel(it).toString(),
                        system = it.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        icon = runCatching { pm.getApplicationIcon(it).toBitmap(72, 72) }.getOrNull(),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }
    // the VPN only picks up the new app list when the core reloads
    DisposableEffect(Unit) { onDispose { Box.reloadIfRunning() } }

    Column(Modifier.fillMaxSize()) {
        TopBar("指定应用 · 已选 ${selected.size}", leading = { BarIcon(NbIcons.Back, "返回", onClose) }) {
            TextButton(onClick = { showSystem = !showSystem }) { Text(if (showSystem) "隐藏系统应用" else "显示系统应用") }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (routeScope == Prefs.SCOPE_APPS) {
                    "只有勾选的应用经过 VPN，其他应用直接联网。开启「绕过中国大陆」时，这些应用访问国内网站也直连。"
                } else {
                    "在首页把路由模式切到「指定应用」后，这里勾选的应用才会单独走 VPN。"
                },
                style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp),
            )
            OutlinedTextField(query, { query = it }, placeholder = { Text("搜索应用") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            return
        }
        val q = query.trim().lowercase()
        val shown = list.filter { (showSystem || !it.system || it.pkg in selected) && (q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q)) }
            .sortedByDescending { it.pkg in selected }
        LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)) {
            items(shown, key = { it.pkg }) { a ->
                val checked = a.pkg in selected
                val enabled = true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = enabled) { Prefs.setPerAppList(if (checked) selected - a.pkg else selected + a.pkg) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (a.icon != null) Image(a.icon.asImageBitmap(), null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.label, style = NbType.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.pkg, style = NbType.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Checkbox(
                        checked = checked, enabled = enabled,
                        onCheckedChange = { Prefs.setPerAppList(if (checked) selected - a.pkg else selected + a.pkg) },
                        colors = CheckboxDefaults.colors(checkedColor = c.accentStrong),
                    )
                }
            }
        }
    }
}
