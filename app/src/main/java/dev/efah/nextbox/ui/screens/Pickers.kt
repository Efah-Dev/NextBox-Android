package dev.efah.nextbox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.Chip
import dev.efah.nextbox.ui.GlowBackground
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.flagged
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType

/**
 * A full-screen page for picking from a long list (apps, regions, services)
 * over a sheet. It is a window of its own: the list scrolls by itself, with
 * no bottom sheet around it to drag along, and it has the whole screen.
 */
@Composable
fun PickerPage(title: String, count: Int, onDone: () -> Unit, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Nb.c
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        LaunchedEffect(c.dark) {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !c.dark
                    isAppearanceLightNavigationBars = !c.dark
                }
            }
        }
        val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(shown, enter = slideInVertically(tween(240)) { it / 10 } + fadeIn(tween(200))) {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                GlowBackground(strength = 0.6f)
                Column(Modifier.fillMaxSize().imePadding()) {
                    TopBar(title, leading = { BarIcon(NbIcons.Back, T("返回"), onDismiss) })
                    Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentStrong),
                        modifier = Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp).fillMaxWidth().height(50.dp),
                    ) { Text(if (count == 0) T("完成") else T("完成（%d 个）", count), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private val packageName = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

/** Picking apps: the installed ones, searchable, or a package name typed by hand. */
@Composable
fun AppPicker(initial: Set<String>, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val c = Nb.c
    val toast = LocalToast.current
    var picked by remember { mutableStateOf(initial) }
    var manual by remember { mutableStateOf(false) }
    PickerPage(T("选择应用"), picked.size, onDone = { onDone(picked) }, onDismiss = onDismiss) {
        AppChooser(
            selected = picked, onChange = { picked = it }, showBulkActions = false,
            padding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            trailing = { TextButton(onClick = { manual = true }) { Text(T("手动输入包名"), color = c.accentInk) } },
        )
    }
    if (manual) {
        var pkg by remember { mutableStateOf("") }
        NbAlertDialog(
            onDismissRequest = { manual = false },
            title = { Text(T("手动输入包名")) },
            text = {
                OutlinedTextField(
                    pkg, { pkg = it }, singleLine = true, placeholder = { Text("com.example.app") },
                    supportingText = { Text(T("没安装的应用也可以先加上")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {}),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = pkg.trim()
                    if (!packageName.matches(v)) {
                        toast(T("包名格式不对，例如 com.example.app"))
                    } else {
                        picked = picked + v
                        manual = false
                    }
                }) { Text(T("添加")) }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text(T("取消")) } },
        )
    }
}

/** Picking IP regions: a search (name, code or pinyin initials) over every region. */
@Composable
fun RegionPicker(initial: Set<String>, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val c = Nb.c
    var picked by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    PickerPage(T("选择地区"), picked.size, onDone = { onDone(picked) }, onDismiss = onDismiss) {
        OutlinedTextField(
            query, { query = it }, singleLine = true,
            placeholder = { Text(T("搜索名称、代码或简拼，例如 rb")) },
            leadingIcon = { Icon(NbIcons.Search, null, tint = c.muted) },
            shape = RoundedCornerShape(22.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )
        val list = remember(query, dev.efah.nextbox.ui.Lang.current) { Regions.search(query) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            items(list.size, key = { list[it].code }) { i ->
                val r = list[i]
                val on = r.code in picked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { picked = if (on) picked - r.code else picked + r.code }.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(flagged(r.name, r.code.uppercase()), style = NbType.body, color = c.text, modifier = Modifier.weight(1f))
                    Text(r.code, style = NbType.small, color = c.muted, modifier = Modifier.padding(end = 8.dp))
                    Checkbox(
                        checked = on, onCheckedChange = { picked = if (on) picked - r.code else picked + r.code },
                        colors = CheckboxDefaults.colors(checkedColor = c.accentStrong),
                    )
                }
            }
        }
    }
}

/** Picking common services, by topic, two to a row. */
@Composable
fun ServicePicker(initial: Set<String>, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val c = Nb.c
    var picked by remember { mutableStateOf(initial) }
    PickerPage(T("常用服务"), picked.size, onDone = { onDone(picked) }, onDismiss = onDismiss) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            item(key = "hint") {
                Text(T("选中的服务会整组加入，包含它们的全部域名"), style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
            RuleUi.serviceGroups.forEach { (topic, ids) ->
                item(key = "t:$topic") {
                    Text(T(topic), style = NbType.bodyBold.copy(fontSize = 13.sp), color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp))
                }
                val list = ids.mapNotNull { RuleUi.service(it) }
                items(list.chunked(2).size, key = { "r:$topic:$it" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.chunked(2)[row].forEach { s ->
                            val on = s.id in picked
                            Row(
                                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (on) c.accentSoft else c.card)
                                    .border(1.dp, if (on) c.accentInk.copy(alpha = 0.45f) else c.cardBorder, RoundedCornerShape(14.dp))
                                    .clickable { picked = if (on) picked - s.id else picked + s.id }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    T(s.label), style = NbType.body.copy(fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                                    color = if (on) c.accentInk else c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                )
                                Box(
                                    Modifier.size(20.dp).clip(CircleShape).background(if (on) c.accentStrong else androidx.compose.ui.graphics.Color.Transparent)
                                        .border(1.5.dp, if (on) c.accentStrong else c.line, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { if (on) Icon(NbIcons.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(12.dp)) }
                            }
                        }
                        if (list.chunked(2)[row].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The chosen common services as chips (tap one to drop it), and the button that opens the picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServicesField(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RuleUi.services.filter { it.id in selected }.forEach { s -> Chip(T(s.label) + "  ✕", true, onClick = { onChange(selected - s.id) }) }
        Chip(if (selected.isEmpty()) T("选择常用服务") else T("＋ 服务"), false, onClick = { open = true })
    }
    if (open) ServicePicker(selected, onDone = { onChange(it); open = false }, onDismiss = { open = false })
}
