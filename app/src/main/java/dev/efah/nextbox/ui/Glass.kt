package dev.efah.nextbox.ui

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.efah.nextbox.ui.theme.Nb

/**
 * "Liquid glass" surfaces: a component shows a blurred, slightly refracted
 * copy of what is behind it.
 *
 * Compose cannot read back pixels, so the content that may sit behind glass is
 * recorded into a [Backdrop] layer ([backdropSource]) and a glass component
 * draws that layer again, clipped to itself, through a blur (Android 12+) and
 * a refraction shader (Android 13+). Older systems, power saving and the
 * "glass effects" switch fall back to a plain surface.
 */
class Backdrop(val layer: GraphicsLayer) {
    /** Top-left of the recorded content in root coordinates. */
    var origin by mutableStateOf(Offset.Zero)
}

/** Whether glass effects are on (setting, system version and power saving considered). */
val LocalGlass = staticCompositionLocalOf { false }

/** Space that floating bars at the bottom cover; scrolling content pads by it. */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Records this element's content so glass components can show it behind them. */
@Composable
fun Modifier.backdropSource(backdrop: Backdrop): Modifier {
    if (!LocalGlass.current) return this
    return this
        .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }
}

/**
 * A glass surface with rounded corners.
 *
 * @param refraction how far content bends at the rim; 0 for a flat blur (bars that touch the screen edge)
 * @param tint drawn over the blurred backdrop
 * @param solid the surface color when glass effects are off
 */
@Composable
fun Modifier.glass(
    backdrop: Backdrop?,
    radius: Dp,
    tint: Color = Nb.c.card.copy(alpha = if (Nb.c.dark) 0.5f else 0.55f),
    solid: Color = Nb.c.card,
    blur: Dp = 18.dp,
    refraction: Dp = 12.dp,
    rim: Boolean = true,
): Modifier {
    val c = Nb.c
    val shape = RoundedCornerShape(radius)
    if (!LocalGlass.current || backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this.clip(shape).background(solid).then(if (rim) Modifier.border(1.dp, c.cardBorder, shape) else Modifier)
    }
    val layer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawWithCache {
            val r = minOf(radius.toPx(), size.minDimension / 2f)
            layer.renderEffect = glassEffect(size, r, blur.toPx(), refraction.toPx(), minOf(r, 20.dp.toPx()))
            layer.setRoundRectOutline(Offset.Zero, size, r)
            layer.clip = true
            val stroke = 1.dp.toPx()
            val rimBrush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (c.dark) 0.30f else 0.90f),
                    Color.White.copy(alpha = if (c.dark) 0.05f else 0.30f),
                ),
            )
            onDrawBehind {
                layer.record {
                    translate(backdrop.origin.x - pos.x, backdrop.origin.y - pos.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(layer)
                drawRoundRect(tint, cornerRadius = CornerRadius(r))
                if (rim) {
                    drawRoundRect(
                        rimBrush,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(r - stroke / 2),
                        style = Stroke(stroke),
                    )
                }
            }
        }
        .clip(shape)
}

private fun glassEffect(size: Size, radius: Float, blur: Float, refraction: Float, edge: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || size.width <= 0f || size.height <= 0f) return null
    val blurEffect = android.graphics.RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && refraction > 0f) {
        return Refraction.chain(blurEffect, size, radius, refraction, edge).asComposeRenderEffect()
    }
    return blurEffect.asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Refraction {
    // A rounded rectangle lens: towards the rim, content is sampled from
    // further inside (it bends), and the rim catches light from the top left.
    private const val SRC = """
        uniform shader content;
        uniform float2 size;
        uniform float radius;
        uniform float edge;
        uniform float strength;

        half4 main(float2 xy) {
            float2 h = size * 0.5;
            float2 p = xy - h;
            float2 q = abs(p) - (h - float2(radius));
            float d = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
            float2 g;
            if (q.x > 0.0 && q.y > 0.0) {
                g = normalize(q) * sign(p);
            } else if (q.x > q.y) {
                g = float2(sign(p.x), 0.0);
            } else {
                g = float2(0.0, sign(p.y));
            }
            float t = clamp(1.0 + d / edge, 0.0, 1.0);
            float bend = t * t * t;
            half4 c = content.eval(xy - g * bend * strength);
            float light = bend * (0.5 + 0.5 * dot(g, float2(-0.7071, -0.7071)));
            c.rgb += half3(light * 0.22);
            return c;
        }
    """

    fun chain(inner: android.graphics.RenderEffect, size: Size, radius: Float, strength: Float, edge: Float): android.graphics.RenderEffect {
        val shader = RuntimeShader(SRC)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("edge", maxOf(edge, 1f))
        shader.setFloatUniform("strength", strength)
        return android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"),
            inner,
        )
    }
}

/** Soft accent-colored light behind the content, so glass has something to show. */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, strength: Float = 1f) {
    val c = Nb.c
    val second = lerp(c.accent, Color(0xFF7C9BFF), 0.7f)
    Canvas(modifier.fillMaxSize().background(c.bg)) {
        fun blob(color: Color, alpha: Float, center: Offset, radius: Float) {
            drawRect(
                Brush.radialGradient(listOf(color.copy(alpha = alpha * strength), Color.Transparent), center, radius),
            )
        }
        val a = if (c.dark) 0.20f else 0.30f
        blob(c.accent, a, Offset(size.width * 0.05f, size.height * 0.02f), size.width * 0.95f)
        blob(second, a * 0.85f, Offset(size.width * 1.05f, size.height * 0.38f), size.width * 0.85f)
        blob(c.accent, a * 0.7f, Offset(size.width * 0.15f, size.height * 0.98f), size.width * 0.9f)
    }
}

/**
 * Blurs what is behind the dialog (or bottom sheet) window this is composed
 * in. Android 12+; elsewhere the dialog just dims as usual.
 */
@Composable
fun WindowBlurBehind() {
    if (!LocalGlass.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    DisposableEffect(view) {
        var v: Any? = view
        var window: android.view.Window? = null
        while (v is View && window == null) {
            window = (v as? DialogWindowProvider)?.window
            v = v.parent
        }
        window?.let {
            it.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            it.attributes = it.attributes.apply { blurBehindRadius = 48 }
            it.setDimAmount(0.22f)
        }
        onDispose {}
    }
}
