package dev.efah.nextbox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.ui.theme.Nb

/**
 * A number setting in one line: − value ＋. The buttons step through
 * [steps] (from the value, to the next one below or above); a tap on the
 * value lets any number in [range] be typed. [label] formats the value,
 * e.g. "10 分钟".
 */
@Composable
fun NbStepper(
    value: Int,
    steps: List<Int>,
    range: IntRange,
    label: (Int) -> String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    inputTitle: String = "",
) {
    val c = Nb.c
    var typing by remember { mutableStateOf(false) }
    val below = steps.lastOrNull { it < value }
    val above = steps.firstOrNull { it > value }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(42.dp).clip(shape).background(c.card).border(1.dp, c.line, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(NbIcons.Minus, T("减少"), below != null) { below?.let(onChange) }
        Box(
            Modifier.fillMaxHeight().widthIn(min = 88.dp).background(c.line.copy(alpha = 0.25f)).clickable { typing = true },
            contentAlignment = Alignment.Center,
        ) {
            // the new value slides in from the side it came from
            AnimatedContent(
                value,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(180)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(180)))
                        .togetherWith(slideOutVertically(tween(180)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(120)))
                },
                label = "stepper",
            ) { v ->
                Text(label(v), color = c.text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        StepButton(NbIcons.Plus, T("增加"), above != null) { above?.let(onChange) }
    }

    if (typing) {
        var text by remember { mutableStateOf(value.toString()) }
        val n = text.toIntOrNull()
        val ok = n != null && n in range
        NbAlertDialog(
            onDismissRequest = { typing = false },
            title = { Text(inputTitle) },
            text = {
                NbField(
                    text, { v -> text = v.filter(Char::isDigit).take(6) }, singleLine = true,
                    supportingText = { Text(T("%d 到 %d", range.first, range.last)) },
                    isError = text.isNotEmpty() && !ok,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { NbButton(T("确定"), { if (ok) { onChange(n); typing = false } }, enabled = ok) },
            dismissButton = { NbButton(T("取消"), { typing = false }, kind = BtnKind.Secondary) },
        )
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Nb.c
    Box(
        Modifier.width(46.dp).fillMaxHeight().clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) c.accentInk else c.muted.copy(alpha = 0.4f), modifier = androidx.compose.ui.Modifier.width(18.dp).height(18.dp))
    }
}
