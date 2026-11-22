package dev.efah.nextbox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.delay

// The app's own components. Pages use these instead of Material's defaults,
// so that dialogs, menus, messages, buttons and fields look the same
// everywhere.

// ---------------------------------------------------------------- messages

enum class ToastKind { Info, Ok, Error }

class ToastData(val text: String, val kind: ToastKind, val action: String?, val onAction: (() -> Unit)?)

/**
 * Shows a short message above the bottom bar. Called like a function for a
 * plain message (`toast("…")`); [ok] and [error] add an icon, and [ok] can
 * carry one action ("查看").
 */
class Toaster(private val post: (ToastData) -> Unit) : (String) -> Unit {
    override fun invoke(text: String) = post(ToastData(text, ToastKind.Info, null, null))
    fun ok(text: String, action: String? = null, onAction: (() -> Unit)? = null) = post(ToastData(text, ToastKind.Ok, action, onAction))
    fun error(text: String) = post(ToastData(text, ToastKind.Error, null, null))
}

val LocalToast = staticCompositionLocalOf { Toaster {} }

/** Holds the message on screen; one at a time, a new one replaces it. */
class ToastState {
    var current by mutableStateOf<ToastData?>(null)
        private set
    val toaster = Toaster { current = it }
    fun dismiss() { current = null }
}

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val c = Nb.c
    var shown by remember { mutableStateOf<ToastData?>(null) }
    val data = state.current
    LaunchedEffect(data) {
        if (data != null) {
            shown = data
            delay(if (data.action != null) 4000 else 2600)
            if (state.current === data) state.dismiss()
        }
    }
    AnimatedVisibility(
        visible = data != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val d = shown ?: return@AnimatedVisibility
        val bg = if (c.dark) Color(0xF2323B4E) else Color(0xEB1D2A3B)
        Row(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 520.dp).clip(RoundedCornerShape(50))
                .background(bg).padding(start = if (d.kind == ToastKind.Info) 18.dp else 8.dp, end = if (d.action != null) 6.dp else 18.dp)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (d.kind != ToastKind.Info) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (d.kind == ToastKind.Ok) Color(0xFF34D399) else Color(0xFFF87171)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (d.kind == ToastKind.Ok) NbIcons.Check else NbIcons.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                d.text, color = Color.White, fontSize = 13.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 10.dp).weight(1f, fill = false),
            )
            if (d.action != null) {
                Text(
                    d.action, color = lerpInk(c.accent), fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50))
                        .clickable { state.dismiss(); d.onAction?.invoke() }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** A light shade of the accent, readable on the dark message. */
private fun lerpInk(accent: Color) = androidx.compose.ui.graphics.lerp(accent, Color.White, 0.45f)

// ---------------------------------------------------------------- buttons

enum class BtnKind { Primary, Secondary, Danger, Text }

/** The app's button: 44dp high, rounded, filled by [kind]. */
@Composable
fun NbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Nb.c
    val (bg, fg) = when (kind) {
        BtnKind.Primary -> c.accentStrong to Color.White
        BtnKind.Secondary -> c.accentSoft to c.accentInk
        BtnKind.Danger -> c.bad.copy(alpha = if (c.dark) 0.2f else 0.1f) to c.bad
        BtnKind.Text -> Color.Transparent to c.muted
    }
    Row(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.45f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) fg else fg.copy(alpha = 0.5f)
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = tint, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ---------------------------------------------------------------- dialogs

/**
 * A dialog: title, content, and its buttons side by side at the bottom
 * (each as wide as the other; pass [NbButton]s). With glass effects on, the
 * background is blurred.
 */
@Composable
fun NbAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val c = Nb.c
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Dialog(onDismissRequest = onDismissRequest) {
        WindowBlurBehind()
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = if (LocalGlass.current) c.card.copy(alpha = if (c.dark) 0.9f else 0.94f) else c.card,
            border = BorderStroke(1.dp, c.cardBorder),
            modifier = Modifier.fillMaxWidth().heightIn(max = maxH),
        ) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)) {
                if (title != null) {
                    ProvideTextStyle(NbType.big.copy(color = c.text)) { title() }
                    Spacer(Modifier.height(10.dp))
                }
                if (text != null) {
                    Box(Modifier.weight(1f, fill = false)) {
                        ProvideTextStyle(NbType.body.copy(color = c.text)) { text() }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dismissButton != null) Box(Modifier.weight(1f), propagateMinConstraints = true) { dismissButton() }
                    Box(Modifier.weight(1f), propagateMinConstraints = true) { confirmButton() }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- menus

/** A pop-up menu: rounded, with icons; [NbMenuItem]s and [NbMenuDivider]s inside. */
@Composable
fun NbMenu(expanded: Boolean, onDismiss: () -> Unit, offset: DpOffset = DpOffset(0.dp, 0.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = Nb.c
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss, offset = offset,
        shape = RoundedCornerShape(18.dp), containerColor = c.card, tonalElevation = 0.dp, shadowElevation = 4.dp,
        border = BorderStroke(1.dp, c.cardBorder),
    ) {
        Column(Modifier.padding(horizontal = 6.dp).widthIn(min = 196.dp), content = content)
    }
}

@Composable
fun NbMenuItem(text: String, icon: ImageVector?, onClick: () -> Unit, sub: String? = null, danger: Boolean = false) {
    val c = Nb.c
    val fg = if (danger) c.bad else c.text
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (danger) c.bad else c.accentInk, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(text, color = fg, fontSize = 14.5.sp, maxLines = 1)
            if (sub != null) Text(sub, style = NbType.small.copy(fontSize = 11.5.sp), color = c.muted, maxLines = 1)
        }
    }
}

@Composable
fun NbMenuDivider() = HorizontalDivider(color = Nb.c.line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))

// ---------------------------------------------------------------- choices

/**
 * The radio circle in one line with the title, centred on it, and the
 * description below, starting where the title starts. [extra] follows the
 * title.
 */
@Composable
fun RadioTitle(selected: Boolean, title: String, desc: String, extra: @Composable () -> Unit = {}) {
    val c = Nb.c
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // without the 48dp touch area: the whole card is the touch target
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                RadioButton(selected, null, Modifier.size(20.dp), colors = RadioButtonDefaults.colors(selectedColor = c.accentStrong))
            }
            Text(title, style = NbType.bodyBold, color = c.text, modifier = Modifier.padding(start = 10.dp))
            extra()
        }
        if (desc.isNotEmpty()) Text(desc, style = NbType.small, color = c.muted, modifier = Modifier.padding(start = 30.dp, top = 2.dp))
    }
}

/** One option as a card: highlighted when chosen. */
@Composable
fun ChoiceCard(selected: Boolean, title: String, desc: String, onClick: () -> Unit, extra: @Composable () -> Unit = {}) {
    val c = Nb.c
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) c.accentStrong else c.line, RoundedCornerShape(14.dp))
            .background(if (selected) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        RadioTitle(selected, title, desc, extra)
    }
}

class ChoiceOption<V>(val value: V, val title: String, val desc: String = "")

/** Picks one of [options] in a bottom sheet; a tap chooses and closes it. */
@Composable
fun <V> ChoiceSheet(title: String, options: List<ChoiceOption<V>>, current: V, onDismiss: () -> Unit, sub: String? = null, onPick: (V) -> Unit) {
    val c = Nb.c
    dev.efah.nextbox.ui.screens.NbSheet(onDismiss = onDismiss) {
        Text(title, style = NbType.big, color = c.text)
        if (sub != null) Text(sub, style = NbType.small, color = c.muted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> ChoiceCard(o.value == current, o.title, o.desc, onClick = { onPick(o.value) }) }
        }
    }
}

// ---------------------------------------------------------------- fields

/** A text field: light, rounded, outlined in the accent while focused. */
@Composable
fun NbField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = NbType.body,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
) {
    val c = Nb.c
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
        textStyle = textStyle.copy(color = c.text), label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, supportingText = supportingText, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accentStrong,
            unfocusedBorderColor = c.line,
            disabledBorderColor = c.line,
            focusedContainerColor = c.card,
            unfocusedContainerColor = if (c.dark) c.bg.copy(alpha = 0.5f) else Color(0xFFFCFBFD),
            focusedLabelColor = c.accentInk,
            unfocusedLabelColor = c.muted,
            unfocusedPlaceholderColor = c.muted.copy(alpha = 0.7f),
            focusedPlaceholderColor = c.muted.copy(alpha = 0.7f),
            cursorColor = c.accentStrong,
        ),
    )
}

// ---------------------------------------------------------------- explanations

/** A small "?" that opens [text] in a sheet: for explanations too long for a row. */
@Composable
fun InfoIcon(title: String, text: String, modifier: Modifier = Modifier) {
    val c = Nb.c
    var open by remember { mutableStateOf(false) }
    // drawn, not a "?" character: a glyph sits off the centre of the circle
    Icon(
        NbIcons.Help, T("说明"), tint = c.muted,
        modifier = modifier.padding(start = 4.dp).size(22.dp).clip(CircleShape).clickable { open = true }.padding(1.dp),
    )
    if (open) InfoSheet(title, text) { open = false }
}

@Composable
fun InfoSheet(title: String, text: String, onDismiss: () -> Unit) {
    val c = Nb.c
    dev.efah.nextbox.ui.screens.NbSheet(onDismiss = onDismiss) {
        Text(title, style = NbType.big, color = c.text)
        Spacer(Modifier.height(10.dp))
        text.split("\n\n").forEach {
            Text(it, style = NbType.body.copy(lineHeight = 23.sp), color = c.text, modifier = Modifier.padding(bottom = 10.dp))
        }
        NbButton(T("知道了"), onDismiss, Modifier.fillMaxWidth().padding(top = 6.dp), kind = BtnKind.Secondary)
    }
}


// ---------------------------------------------------------------- tags

/**
 * Values as removable tags, with a field to add more: Enter or "+" adds
 * what was typed, and a pasted list is split right away. [parse] turns the
 * text into values and the parts it cannot use ([onBad] tells about those,
 * which stay in the field). [splitOn] are the characters that end a value
 * while typing (none for regular expressions).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TagInput(
    values: List<String>,
    onChange: (List<String>) -> Unit,
    placeholder: String,
    parse: (String) -> Pair<List<String>, List<String>>,
    onBad: (List<String>) -> Unit,
    draft: String,
    onDraft: (String) -> Unit,
    splitOn: String = "\n,，; ",
    keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    label: (String) -> String = { it },
) {
    val c = Nb.c
    fun commit(text: String) {
        if (text.isBlank()) return
        val (ok, bad) = parse(text)
        if (ok.isNotEmpty()) onChange((values + ok).distinct())
        onDraft(bad.joinToString(" "))
        if (bad.isNotEmpty()) onBad(bad)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (values.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEach { v ->
                    Row(
                        Modifier.clip(RoundedCornerShape(9.dp)).background(c.line).padding(start = 10.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label(v), style = NbType.small.copy(fontSize = 12.5.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                        Box(Modifier.size(28.dp).clip(CircleShape).clickable { onChange(values - v) }, contentAlignment = Alignment.Center) {
                            Icon(NbIcons.Close, T("删除"), tint = c.muted, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NbField(
                draft,
                { t ->
                    // a separator typed or pasted ends the values before it
                    val last = t.lastOrNull()
                    if (splitOn.isNotEmpty() && t.length > draft.length && (last != null && last in splitOn || t.any { it == '\n' })) commit(t) else onDraft(t)
                },
                placeholder = { Text(placeholder, maxLines = 1) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(draft) }),
                textStyle = NbType.body.copy(fontSize = 14.sp),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.padding(start = 8.dp).size(44.dp).clip(RoundedCornerShape(13.dp)).background(c.accentSoft)
                    .clickable(enabled = draft.isNotBlank()) { commit(draft) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(NbIcons.Plus, T("添加"), tint = c.accentInk, modifier = Modifier.size(20.dp))
            }
        }
    }
}
