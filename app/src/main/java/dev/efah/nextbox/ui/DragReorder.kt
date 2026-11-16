package dev.efah.nextbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Reordering a list by dragging a handle: the dragged row follows the finger
 * and swaps places with a neighbour once it has moved past half of that row.
 * The neighbour slides into the free place and the dropped row settles into
 * its own, instead of jumping there.
 */
@Stable
class DragState {
    var key by mutableStateOf<Any?>(null)
        internal set
    var offset by mutableFloatStateOf(0f)
        internal set
    internal var rowHeight = 1f

    /** Rows that just changed place, with where they were drawn (px from their new place). */
    internal val shifts = mutableStateMapOf<Any, Shift>()
    private var serial = 0

    internal fun shift(key: Any, from: Float) {
        shifts[key] = Shift(from, ++serial)
    }

    internal class Shift(val from: Float, val serial: Int)
}

@Composable
fun rememberDragState(): DragState = remember { DragState() }

private val settle = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * The dragged row's look (lifted, following the finger), the sliding of rows
 * that make way, and the row size that decides when to swap. Put it on the row.
 */
@Composable
fun Modifier.dragRow(state: DragState, key: Any): Modifier {
    val dragging = state.key == key
    val slide = remember(key) { Animatable(0f) }
    val shift = state.shifts[key]
    LaunchedEffect(shift?.serial) {
        if (shift != null) {
            slide.snapTo(shift.from)
            slide.animateTo(0f, settle)
        }
    }
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "lift")
    return this
        .onSizeChanged { if (dragging || state.key == null) state.rowHeight = it.height.coerceAtLeast(1).toFloat() }
        // the dropped row stays on top while it settles
        .zIndex(if (dragging || lift > 0.01f) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.offset else slide.value
            val s = 1f + 0.03f * lift
            scaleX = s
            scaleY = s
            shadowElevation = 18f * lift
        }
}

/**
 * The handle that starts the drag. keys is the list in its current order
 * (read on every move); gap is the space between rows; onMove(from, to)
 * moves one row by one place.
 */
@Composable
fun Modifier.dragHandle(state: DragState, key: Any, keys: List<Any>, gap: Dp = 0.dp, onMove: (Int, Int) -> Unit): Modifier {
    val order by rememberUpdatedState(keys)
    val move by rememberUpdatedState(onMove)
    val haptic = LocalHapticFeedback.current
    return this.pointerInput(key) {
        fun end() {
            // the row settles from where the finger left it
            if (state.key == key && state.offset != 0f) state.shift(key, state.offset)
            state.key = null
            state.offset = 0f
        }
        detectDragGestures(
            onDragStart = {
                state.shifts.remove(key)
                state.key = key
                state.offset = 0f
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, amount ->
                change.consume()
                state.offset += amount.y
                val i = order.indexOf(key)
                val h = state.rowHeight + gap.toPx() // a swap moves the row by its height plus the gap
                if (state.offset > h / 2 && i in 0 until order.size - 1) {
                    // the row below moves up into the free place
                    state.shift(order[i + 1], h)
                    move(i, i + 1)
                    state.offset -= h
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } else if (state.offset < -h / 2 && i > 0) {
                    state.shift(order[i - 1], -h)
                    move(i, i - 1)
                    state.offset += h
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onDragEnd = { end() },
            onDragCancel = { end() },
        )
    }
}
