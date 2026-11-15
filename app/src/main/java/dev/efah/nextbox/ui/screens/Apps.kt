package dev.efah.nextbox.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppItem(val pkg: String, val label: String, val system: Boolean, val icon: Bitmap?)

/** The installed apps, loaded once per process (labels and icons are slow to read). */
object InstalledApps {
    private val mutex = Mutex()
    private var cache: List<AppItem>? = null

    suspend fun load(ctx: Context, refresh: Boolean = false): List<AppItem> = mutex.withLock {
        cache?.takeIf { !refresh }?.let { return it }
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            installed(pm)
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
        }.also { cache = it }
    }

    /** The label of an app, or null when it is not installed. */
    fun label(ctx: Context, pkg: String): String? = cache?.firstOrNull { it.pkg == pkg }?.label
        ?: runCatching { ctx.packageManager.let { pm -> pm.getApplicationLabel(info(pm, pkg)).toString() } }.getOrNull()

    // PackageManager.ApplicationInfoFlags only exists from Android 13; calling it
    // on Android 10-12 throws NoClassDefFoundError (the 1.0 crash on "selected apps").
    fun installed(pm: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

    fun info(pm: PackageManager, pkg: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0)
        }
}

/**
 * A searchable list of installed apps with check boxes. header goes above the
 * list (inside it, so it scrolls along); selected packages that are not
 * installed are listed too, so they can be unchecked.
 */
@Composable
fun AppChooser(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    showBulkActions: Boolean = true,
    padding: PaddingValues? = null,
    header: LazyListScope.() -> Unit = {},
) {
    val c = Nb.c
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = InstalledApps.load(ctx) }

    val list = apps
    val q = query.trim().lowercase()
    val installed = list.orEmpty().associateBy { it.pkg }
    val missing = selected.filter { it !in installed }.map { AppItem(it, it, false, null) }
    val shown = (missing + list.orEmpty())
        .filter { (showSystem || !it.system || it.pkg in selected) && (q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q)) }
        .sortedByDescending { it.pkg in selected }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = padding ?: PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = LocalBottomInset.current + 24.dp),
    ) {
        header()
        item(key = "tools") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    placeholder = { Text(T("搜索应用名或包名")) },
                    leadingIcon = { Icon(NbIcons.Search, null, tint = c.muted) },
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(T("显示系统应用"), showSystem, onClick = { showSystem = !showSystem })
                    Spacer(Modifier.weight(1f))
                    if (showBulkActions && list != null) {
                        // bulk actions work on what the list shows (search and system filter)
                        val visible = shown.map { it.pkg }.toSet()
                        TextButton(onClick = { onChange((selected - visible) + (visible - selected)) }) { Text(T("反选"), color = c.accentInk) }
                        TextButton(onClick = { onChange(selected + visible) }) { Text(T("全选"), color = c.accentInk) }
                        TextButton(onClick = { onChange(selected - visible) }) { Text(T("清空"), color = c.accentInk) }
                    }
                }
            }
        }
        if (list == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            }
            return@LazyColumn
        }
        items(shown, key = { it.pkg }) { a ->
            val checked = a.pkg in selected
            val toggle = { onChange(if (checked) selected - a.pkg else selected + a.pkg) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = toggle)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (a.icon != null) {
                    Image(a.icon.asImageBitmap(), null, Modifier.size(36.dp))
                } else {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) { Text(a.label.take(1).uppercase(), color = c.accentInk, style = NbType.bodyBold) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = NbType.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (a.pkg !in installed) T("未安装") + " · " + a.pkg else a.pkg,
                        style = NbType.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Checkbox(checked = checked, onCheckedChange = { toggle() }, colors = CheckboxDefaults.colors(checkedColor = c.accentStrong))
            }
        }
    }
}
