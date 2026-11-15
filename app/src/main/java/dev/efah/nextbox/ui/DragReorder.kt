package dev.efah.nextbox.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Reordering a list by dragging a handle: the dragged row follows the finger
 * and swaps places with a neighbour once it has moved past half of that row.
 */
@Stable
class DragState {
    var key by mutableStateOf<Any?>(null)
        internal set
    var offset by mutableFloatStateOf(0f)
        internal set
    internal var rowHeight = 1f
}

@Composable
fun rememberDragState(): DragState = remember { DragState() }

/**
 * The dragged row's look (lifted, following the finger) plus its size, which
 * decides when it swaps. Put it on the row.
 */
fun Modifier.dragRow(state: DragState, key: Any): Modifier = this
    .onSizeChanged { if (state.key == key || state.key == null) state.rowHeight = it.height.coerceAtLeast(1).toFloat() }
    .zIndex(if (state.key == key) 1f else 0f)
    .graphicsLayer {
        val dragging = state.key == key
        translationY = if (dragging) state.offset else 0f
        scaleX = if (dragging) 1.02f else 1f
        scaleY = if (dragging) 1.02f else 1f
        shadowElevation = if (dragging) 16f else 0f
    }

/**
 * The handle that starts the drag. index and count are read on every move;
 * gap is the space between rows; onMove(from, to) moves one row by one place.
 */
@Composable
fun Modifier.dragHandle(state: DragState, key: Any, index: Int, count: Int, gap: Dp = 0.dp, onMove: (Int, Int) -> Unit): Modifier {
    val i by rememberUpdatedState(index)
    val n by rememberUpdatedState(count)
    val move by rememberUpdatedState(onMove)
    return this.pointerInput(key) {
        detectDragGestures(
            onDragStart = { state.key = key; state.offset = 0f },
            onDrag = { change, amount ->
                change.consume()
                state.offset += amount.y
                val h = state.rowHeight + gap.toPx() // a swap moves the row by its height plus the gap
                if (state.offset > h / 2 && i < n - 1) {
                    move(i, i + 1)
                    state.offset -= h
                } else if (state.offset < -h / 2 && i > 0) {
                    move(i, i - 1)
                    state.offset += h
                }
            },
            onDragEnd = { state.key = null; state.offset = 0f },
            onDragCancel = { state.key = null; state.offset = 0f },
        )
    }
}
