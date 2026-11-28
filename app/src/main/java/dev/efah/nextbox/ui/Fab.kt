package dev.efah.nextbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.efah.nextbox.ui.theme.Nb
import kotlinx.coroutines.launch

/** How long a press must last to be a long press of [NbFab]. */
private const val HOLD_MS = 500

/**
 * A round floating button with an icon: a tap is [onClick]; held for half a
 * second (a ring fills around it meanwhile) it is [onLongPress]. While
 * [busy], a ring turns around it. [surface] draws the button itself (the
 * glass of the page), [ring] is the colour of the rings.
 */
@Composable
fun NbFab(
    icon: ImageVector,
    label: String,
    longLabel: String,
    busy: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    ring: Color = Nb.c.accentStrong,
    surface: @Composable Modifier.() -> Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val hold = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongPress)
    val spin = rememberInfiniteTransition(label = "fab").animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin")

    Box(
        modifier
            .size(68.dp)
            .drawBehind {
                val w = 3.dp.toPx()
                val inset = w / 2 + 1.dp.toPx()
                val arc = Size(size.width - inset * 2, size.height - inset * 2)
                val at = Offset(inset, inset)
                if (busy) {
                    drawArc(ring.copy(alpha = 0.18f), 0f, 360f, false, at, arc, style = Stroke(w))
                    drawArc(ring, spin.value - 90f, 100f, false, at, arc, style = Stroke(w, cap = StrokeCap.Round))
                }
                if (hold.value > 0f) {
                    drawArc(ring, -90f, 360f * hold.value, false, at, arc, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                onClick(label) { click(); true }
                customActions = listOf(CustomAccessibilityAction(longLabel) { long(); true })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    scope.launch { press.animateTo(0.93f, tween(90)) }
                    val fill = scope.launch { hold.animateTo(1f, tween(HOLD_MS, easing = LinearEasing)) }
                    // released before the ring is full: a tap
                    val up = withTimeoutOrNull(HOLD_MS.toLong()) {
                        while (true) {
                            val e = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!e.pressed) {
                                e.consume()
                                break
                            }
                        }
                        true
                    }
                    fill.cancel()
                    if (up != null) {
                        click()
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        long()
                        // the rest of the press is part of it
                        while (true) {
                            val e = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            e.consume()
                            if (!e.pressed) break
                        }
                    }
                    scope.launch { hold.animateTo(0f, tween(180)) }
                    scope.launch { press.animateTo(1f, tween(140)) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.padding(6.dp).size(56.dp).graphicsLayer { scaleX = press.value; scaleY = press.value }.surface(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
