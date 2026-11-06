package dev.efah.nextbox.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Design tokens of the NextBox screens (see the design canvas). */
@Immutable
data class NbColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardBorder: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val accentStrong: Color,
    val good: Color,
    val mid: Color,
    val slow: Color,
    val bad: Color,
)

private fun tokens(dark: Boolean, accent: Color): NbColors = if (!dark) {
    NbColors(
        dark = false,
        bg = Color(0xFFF6F3F8),
        card = Color.White,
        cardBorder = Color(0x12133C82),
        line = Color(0xFFEFE8F2),
        text = Color(0xFF1D2A3B),
        muted = Color(0xFF5B6678),
        accent = accent,
        accentSoft = lerp(accent, Color.White, 0.84f),
        accentInk = lerp(accent, Color.Black, 0.45f),
        accentStrong = lerp(accent, Color.Black, 0.3f),
        good = Color(0xFF0F7A43),
        mid = Color(0xFF8A6100),
        slow = Color(0xFFB3470A),
        bad = Color(0xFFC2333A),
    )
} else {
    NbColors(
        dark = true,
        bg = Color(0xFF121722),
        card = Color(0xFF1B2230),
        cardBorder = Color(0x0FFFFFFF),
        line = Color(0xFF2A3344),
        text = Color(0xFFE3E9F3),
        muted = Color(0xFF9AA6B8),
        accent = accent,
        accentSoft = lerp(accent, Color(0xFF1B2230), 0.78f),
        accentInk = lerp(accent, Color.White, 0.45f),
        accentStrong = lerp(accent, Color.Black, 0.2f),
        good = Color(0xFF4ADE80),
        mid = Color(0xFFF5C451),
        slow = Color(0xFFFB923C),
        bad = Color(0xFFF87171),
    )
}

val LocalNb = staticCompositionLocalOf { tokens(false, Color(0xFFF472B6)) }

object Nb {
    val c: NbColors
        @Composable get() = LocalNb.current
}

fun parseHex(hex: String?, fallback: Color = Color(0xFFF472B6)): Color = try {
    val h = hex.orEmpty().removePrefix("#")
    if (h.length == 6) Color(0xFF000000 or h.toLong(16)) else fallback
} catch (_: NumberFormatException) {
    fallback
}

/** theme: "auto" | "light" | "dark" (shared with the Windows settings). */
@Composable
fun NextBoxTheme(theme: String, accentHex: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accent by animateColorAsState(parseHex(accentHex), tween(400), label = "accent")
    val nb = tokens(dark, accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = nb.accent,
            onPrimary = Color(0xFF2A1020),
            primaryContainer = nb.accentSoft,
            onPrimaryContainer = nb.accentInk,
            secondaryContainer = nb.accentSoft,
            onSecondaryContainer = nb.accentInk,
            background = nb.bg,
            surface = nb.card,
            surfaceContainer = nb.card,
            surfaceContainerHigh = nb.card,
            surfaceContainerLow = nb.card,
            onSurface = nb.text,
            onSurfaceVariant = nb.muted,
            outline = Color(0xFF3A4458),
            outlineVariant = nb.line,
        )
    } else {
        lightColorScheme(
            primary = nb.accentStrong,
            onPrimary = Color.White,
            primaryContainer = nb.accentSoft,
            onPrimaryContainer = nb.accentInk,
            secondaryContainer = nb.accentSoft,
            onSecondaryContainer = nb.accentInk,
            background = nb.bg,
            surface = nb.card,
            surfaceContainer = nb.card,
            surfaceContainerHigh = nb.card,
            surfaceContainerLow = nb.card,
            onSurface = nb.text,
            onSurfaceVariant = nb.muted,
            outline = Color(0xFF8D97A8),
            outlineVariant = nb.line,
        )
    }
    CompositionLocalProvider(LocalNb provides nb) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object NbType {
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val section = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 15.sp)
    val bodyBold = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val small = TextStyle(fontSize = 12.sp)
    val big = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
}
