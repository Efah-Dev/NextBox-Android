package dev.efah.nextbox.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.drawscope.clipPath
import dev.efah.nextbox.data.Profile

/** The flag emoji of a region code ("JP" → 🇯🇵); "" when the code is not a country. */
fun flagOf(region: String): String {
    val code = region.trim().uppercase().let { if (it == "UK") "GB" else it }
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
    return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/** Whether a name already carries a flag (subscriptions often put one in front). */
private fun hasFlag(name: String): Boolean {
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        if (cp in 0x1F1E6..0x1F1FF) return true
        i += Character.charCount(cp)
    }
    return false
}

/**
 * The first flag in a name and the name without it ("🇭🇰 HK 01" → "🇭🇰", "HK 01");
 * "" and the name when it has none.
 */
fun splitFlag(name: String): Pair<String, String> {
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        val n = Character.charCount(cp)
        if (cp in 0x1F1E6..0x1F1FF && i + n < name.length) {
            val cp2 = name.codePointAt(i + n)
            if (cp2 in 0x1F1E6..0x1F1FF) {
                val end = i + n + Character.charCount(cp2)
                val rest = (name.substring(0, i) + " " + name.substring(end)).replace(Regex("\\s+"), " ").trim()
                return name.substring(i, end) to rest.ifEmpty { name }
            }
        }
        i += n
    }
    return "" to name
}

/** A node name with the flag of its region in front, unless the name has one already. */
fun flagged(name: String, region: String): String {
    val flag = flagOf(region)
    return if (flag.isEmpty() || hasFlag(name)) name else "$flag $name"
}

/** The node's name as lists show it: with the flag of its region. */
val Profile.label: String get() = flagged(name, region)

private const val TW_FLAG = "🇹🇼"

/** Whether the system font draws the Taiwan flag: some phones sold in mainland China leave it out (it shows blank). */
private val twFlagMissing: Boolean by lazy { !android.graphics.Paint().hasGlyph(TW_FLAG) }

/**
 * Text that may hold flags: where the system font has no Taiwan flag, it is
 * drawn instead (an inline picture the size of an emoji). Otherwise plain
 * [androidx.compose.material3.Text].
 */
@androidx.compose.runtime.Composable
fun FlagText(
    text: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
) {
    if (!twFlagMissing || TW_FLAG !in text) {
        androidx.compose.material3.Text(text = text, modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, textDecoration = textDecoration, style = style, maxLines = maxLines, overflow = overflow)
        return
    }
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        text.split(TW_FLAG).forEachIndexed { i, part ->
            if (i > 0) appendInlineContent("tw", TW_FLAG)
            append(part)
        }
    }
    val inline = mapOf(
        "tw" to androidx.compose.foundation.text.InlineTextContent(
            androidx.compose.ui.text.Placeholder(
                1.3f.em, 1f.em, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
            ),
        ) { TaiwanFlag(androidx.compose.ui.Modifier.fillMaxSize()) },
    )
    androidx.compose.material3.Text(
        text = annotated, modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, textDecoration = textDecoration,
        style = style, maxLines = maxLines, overflow = overflow, inlineContent = inline,
    )
}

private val Float.em get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)

/** The Taiwan flag (3:2, rounded like an emoji flag): red, a blue canton, a white sun of twelve rays. */
@androidx.compose.runtime.Composable
private fun TaiwanFlag(modifier: androidx.compose.ui.Modifier) = androidx.compose.foundation.Canvas(modifier) {
    val w = minOf(size.width, size.height * 1.5f) * 0.94f
    val h = w / 1.5f
    val left = (size.width - w) / 2
    val top = (size.height - h) / 2
    val blue = androidx.compose.ui.graphics.Color(0xFF000095)
    val white = androidx.compose.ui.graphics.Color.White
    val outline = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, top + h, androidx.compose.ui.geometry.CornerRadius(h * 0.1f)))
    }
    clipPath(outline) {
        drawRect(androidx.compose.ui.graphics.Color(0xFFFE0000), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(w, h))
        drawRect(blue, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(w / 2, h / 2))
        val cx = left + w / 4
        val cy = top + h / 4
        val r = h / 2 * 0.38f
        val sun = androidx.compose.ui.graphics.Path()
        for (k in 0 until 24) {
            val a = Math.PI * k / 12 - Math.PI / 2
            val rr = if (k % 2 == 0) r else r * 0.56f
            val x = cx + (rr * kotlin.math.cos(a)).toFloat()
            val y = cy + (rr * kotlin.math.sin(a)).toFloat()
            if (k == 0) sun.moveTo(x, y) else sun.lineTo(x, y)
        }
        sun.close()
        drawPath(sun, white)
        drawCircle(blue, r * 0.53f, androidx.compose.ui.geometry.Offset(cx, cy))
        drawCircle(white, r * 0.45f, androidx.compose.ui.geometry.Offset(cx, cy))
    }
}
