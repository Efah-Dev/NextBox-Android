package dev.efah.nextbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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

private val rowShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

private val settle = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * The dragged row's look (lifted, following the finger), the sliding of rows
 * that make way, and the row size that decides when to swap. Put it on the row.
 */
@Composable
fun Modifier.dragRow(
    state: DragState,
    key: Any,
    horizontal: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = rowShape,
): Modifier {
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
        .onSizeChanged {
            if (dragging || state.key == null) state.rowHeight = (if (horizontal) it.width else it.height).coerceAtLeast(1).toFloat()
        }
        // the dropped row stays on top while it settles
        .zIndex(if (dragging || lift > 0.01f) 1f else 0f)
        .graphicsLayer {
            val moved = if (dragging) state.offset else slide.value
            if (horizontal) translationX = moved else translationY = moved
            val s = 1f + 0.03f * lift
            scaleX = s
            scaleY = s
            // the shadow follows the rounded card (a plain one is a dark rectangle around it)
            shadowElevation = 18f * lift
            this.shape = shape
            clip = false
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

/**
 * Keeps a row above its neighbours while a part of it (the card with
 * dragRow) is dragged or settles, when dragRow is not on the row itself.
 */
@Composable
fun Modifier.dragAbove(state: DragState, key: Any): Modifier {
    val dragging = state.key == key
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "above")
    return zIndex(if (dragging || lift > 0.01f) 1f else 0f)
}

/**
 * Long press and drag a row of a lazy list into another place, as
 * [dragHandle] does with a handle: the whole row is the handle. Held without
 * moving and released, it calls [onHold] instead (the row's menu). The list
 * scrolls when the row is dragged to its top or bottom edge, and keeps its
 * position while rows swap (a lazy list otherwise follows its first row).
 * keys is the list in its current order; onDrop is called once a drag ends.
 * horizontal is for a row of items of different widths (a LazyRow).
 */
@Composable
fun Modifier.longPressDrag(
    state: DragState,
    list: androidx.compose.foundation.lazy.LazyListState,
    key: Any,
    keys: List<Any>,
    gap: Dp,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onDrop: () -> Unit,
    onHold: () -> Unit,
    horizontal: Boolean = false,
): Modifier {
    val order by rememberUpdatedState(keys)
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val hold by rememberUpdatedState(onHold)
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gapPx = with(density) { gap.toPx() }
    val edge = with(density) { 64.dp.toPx() }

    fun swapIfNeeded() {
        val i = order.indexOf(key)
        val h = state.rowHeight + gapPx
        val first = list.firstVisibleItemIndex
        val firstOffset = list.firstVisibleItemScrollOffset
        // items of a row differ in size: the neighbour passed decides when to
        // swap and how far the dragged one's own place moves
        fun span(k: Any): Float =
            if (!horizontal) h else (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }?.size?.toFloat() ?: state.rowHeight) + gapPx
        val to = when {
            i in 0 until order.size - 1 && state.offset > span(order[i + 1]) / 2 -> i + 1
            i > 0 && state.offset < -span(order[i - 1]) / 2 -> i - 1
            else -> return
        }
        val passed = span(order[to])
        state.shift(order[to], if (to > i) h else -h)
        move(i, to)
        state.offset += if (to > i) -passed else passed
        list.requestScrollToItem(first, firstOffset)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // near an edge the list scrolls, and the row stays under the finger
    val dragging = state.key == key
    LaunchedEffect(dragging) {
        while (dragging && state.key == key) {
            androidx.compose.runtime.withFrameNanos { }
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
            val top = item.offset + state.offset
            val bottom = top + item.size
            val end = info.viewportEndOffset - info.afterContentPadding
            val speed = when {
                top < edge -> -((edge - top) / edge).coerceIn(0f, 1f) * 22f
                bottom > end - edge -> ((bottom - (end - edge)) / edge).coerceIn(0f, 1f) * 22f
                else -> 0f
            }
            if (speed != 0f) {
                val done = list.scrollBy(speed)
                state.offset += done
                swapIfNeeded()
            }
        }
    }

    return this.pointerInput(key, enabled) {
        if (!enabled) return@pointerInput
        // events are read before the row's own click handling (Initial pass):
        // once the drag runs, the moves are consumed here, so the row neither
        // clicks nor treats them as its own long press, and the list does not
        // scroll by them
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // a long press is a finger held still: moving past the touch slop
            // first (the start of a scroll) or lifting it is not one
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                true
            }
            if (ended != null) return@awaitEachGesture
            state.shifts.remove(key)
            state.key = key
            state.offset = 0f
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            var travel = 0f
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dy = if (horizontal) change.position.x - change.previousPosition.x else change.position.y - change.previousPosition.y
                travel += kotlin.math.abs(dy)
                state.offset += dy
                if (travel > viewConfiguration.touchSlop) change.consume()
                swapIfNeeded()
            }
            val dragged = travel > viewConfiguration.touchSlop
            if (state.offset != 0f) state.shift(key, state.offset)
            state.key = null
            state.offset = 0f
            if (dragged) drop() else hold()
        }
    }
}
