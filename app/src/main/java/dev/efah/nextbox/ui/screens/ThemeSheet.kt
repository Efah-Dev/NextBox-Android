package dev.efah.nextbox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.theme.parseHex
import kotlin.math.roundToInt

/** The preset accent colours, the same as NextBox for Windows. */
val accentPresets = listOf("#F472B6", "#1370F3", "#0EA5E9", "#10B981", "#8B5CF6", "#F97316", "#E5484D", "#475569")

private fun isPreset(hex: String) = accentPresets.any { it.equals(hex, ignoreCase = true) }

private fun hexOf(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

private fun hsvOf(hex: String): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(parseHex(hex).toArgb(), it) }

private fun colorOf(hsv: FloatArray): Color = Color(android.graphics.Color.HSVToColor(hsv))

/** Contrast of white text on the colour (buttons, badges); about 3 is needed. */
private fun whiteContrast(c: Color) = 1.05f / (c.luminance() + 0.05f)

/**
 * 主题: light / dark / system and the accent colour: presets, the custom
 * colours used lately (recentColors, as on Windows, so backups carry them
 * both ways) and a colour panel of its own. onChange(theme, accent, recent)
 * saves; the panel previews without saving until 完成.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSheet(theme: String, accent: String, recent: List<String>, onDismiss: () -> Unit, onChange: (String, String, List<String>) -> Unit) {
    val c = Nb.c
    val themes = listOf("auto", "light", "dark")
    var custom by remember { mutableStateOf(false) }
    var hsv by remember { mutableStateOf(hsvOf(accent)) }
    var hexText by remember { mutableStateOf(accent.uppercase()) }
    val current = colorOf(hsv)

    fun choose(hex: String) {
        custom = false
        onChange(theme, hex.uppercase(), recent)
    }

    fun finishCustom() {
        val hex = hexOf(current)
        val next = if (isPreset(hex)) recent else (listOf(hex) + recent.filterNot { it.equals(hex, ignoreCase = true) }).take(4)
        custom = false
        onChange(theme, hex, next)
    }

    NbSheet(onDismiss) {
        Text(T("主题"), style = NbType.big, color = c.text, modifier = Modifier.padding(bottom = 12.dp))
        Segmented(listOf(T("跟随系统"), T("浅色"), T("深色")), themes.indexOf(theme).coerceAtLeast(0), onSelect = { onChange(themes[it], accent, recent) })
        Text(T("主题色"), style = NbType.section, color = c.muted, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            accentPresets.forEach { Swatch(it, it.equals(accent, ignoreCase = true) && !custom) { choose(it) } }
            if (recent.isNotEmpty()) {
                Box(Modifier.width(1.dp).height(22.dp).background(c.line))
                recent.forEach { Swatch(it, it.equals(accent, ignoreCase = true) && !custom) { choose(it) } }
            }
            // a colour wheel chip opens the panel
            val customOn = custom || (!isPreset(accent) && recent.none { it.equals(accent, ignoreCase = true) })
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                    .border(1.dp, if (customOn) c.accent else c.line, RoundedCornerShape(18.dp))
                    .clickable {
                        if (custom) {
                            custom = false
                        } else {
                            hsv = hsvOf(accent)
                            hexText = accent.uppercase()
                            custom = true
                        }
                    }
                    .padding(start = 5.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(
                        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                    ),
                )
                Text(T("自定义"), color = if (customOn) c.accentInk else c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }

        AnimatedVisibility(custom) {
            Column(Modifier.padding(top = 16.dp)) {
                SaturationValue(hsv) { s, v ->
                    hsv = floatArrayOf(hsv[0], s, v)
                    hexText = hexOf(colorOf(hsv))
                }
                Spacer(Modifier.height(14.dp))
                HueBar(hsv[0]) { h ->
                    hsv = floatArrayOf(h, hsv[1], hsv[2])
                    hexText = hexOf(colorOf(hsv))
                }
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(current))
                    OutlinedTextField(
                        hexText,
                        { t ->
                            hexText = t.uppercase().take(7)
                            Regex("^#?([0-9A-F]{6})$").find(hexText)?.let { hsv = hsvOf("#" + it.groupValues[1]) }
                        },
                        singleLine = true, label = { Text("HEX") },
                        textStyle = NbType.body.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                }
                // how the colour looks on the app's controls
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.line.copy(alpha = 0.35f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        T("连接"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(current).padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                    Text(
                        "VLESS", color = current, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(current.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(40.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(current), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.padding(end = 3.dp).size(16.dp).clip(CircleShape).background(Color.White))
                    }
                }
                if (whiteContrast(current) < 3f) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(T("颜色偏浅，按钮上的白字会看不清"), style = NbType.small, color = c.mid, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            // darker (and a little more saturated) until white text reads
                            var v = hsv[2]
                            val s = maxOf(hsv[1], 0.55f)
                            while (v > 0.2f && whiteContrast(colorOf(floatArrayOf(hsv[0], s, v))) < 3.2f) v -= 0.02f
                            hsv = floatArrayOf(hsv[0], s, v)
                            hexText = hexOf(colorOf(hsv))
                        }) { Text(T("自动加深"), color = c.accentInk) }
                    }
                } else {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(NbIcons.Check, null, tint = c.good, modifier = Modifier.size(14.dp))
                        Text(T("文字清晰可读"), style = NbType.small, color = c.good, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { custom = false }, modifier = Modifier.weight(1f).height(48.dp)) { Text(T("取消")) }
                    Button(
                        onClick = ::finishCustom,
                        colors = ButtonDefaults.buttonColors(containerColor = current),
                        modifier = Modifier.weight(2f).height(48.dp),
                    ) { Text(T("使用这个颜色"), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val c = Nb.c
    Box(
        Modifier.size(36.dp).border(2.dp, if (selected) c.text else Color.Transparent, CircleShape).padding(4.dp)
            .clip(CircleShape).background(parseHex(hex)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(NbIcons.Check, T("已选中"), tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/** The saturation (across) / brightness (down) square of the current hue. */
@Composable
private fun SaturationValue(hsv: FloatArray, onChange: (Float, Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val hue = colorOf(floatArrayOf(hsv[0], 1f, 1f))
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, hue)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun at(x: Float, y: Float) = change((x / w).coerceIn(0f, 1f), 1f - (y / h).coerceIn(0f, 1f))
        Box(
            Modifier.matchParentSize()
                .pointerInput(Unit) { detectTapGestures { at(it.x, it.y) } }
                .pointerInput(Unit) { detectDragGestures(onDragStart = { at(it.x, it.y) }) { change, _ -> change.consume(); at(change.position.x, change.position.y) } },
        )
        Knob(colorOf(hsv), IntOffset((hsv[1] * w).roundToInt(), ((1 - hsv[2]) * h).roundToInt()))
    }
}

/** The hue bar. */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val hues = (0..6).map { colorOf(floatArrayOf(it * 60f % 360f, 1f, 1f)) }.let { it.dropLast(1) + Color.Red }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(hues)),
    ) {
        val w = constraints.maxWidth.toFloat()
        fun at(x: Float) = change((x / w).coerceIn(0f, 0.9999f) * 360f)
        Box(
            Modifier.matchParentSize()
                .pointerInput(Unit) { detectTapGestures { at(it.x) } }
                .pointerInput(Unit) { detectDragGestures(onDragStart = { at(it.x) }) { change, _ -> change.consume(); at(change.position.x) } },
        )
        Knob(colorOf(floatArrayOf(hue, 1f, 1f)), IntOffset((hue / 360f * w).roundToInt(), constraints.maxHeight / 2))
    }
}

/** A white-ringed dot centred on center (px). */
@Composable
private fun Knob(color: Color, center: IntOffset) {
    val r = with(LocalDensity.current) { 11.dp.roundToPx() }
    Box(
        Modifier.offset { IntOffset(center.x - r, center.y - r) }.size(22.dp)
            .border(3.dp, Color.White, CircleShape).padding(3.dp).clip(CircleShape).background(color),
    )
}
