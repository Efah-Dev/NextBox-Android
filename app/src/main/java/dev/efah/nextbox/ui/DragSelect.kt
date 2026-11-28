package dev.efah.nextbox.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** A drag selection in progress (see [dragSelect]). */
@Stable
class DragSelectState {
    /** Whether a finger is selecting rows now. */
    var active by mutableStateOf(false)
        internal set

    // the finger, in list coordinates (as LazyListItemInfo.offset)
    internal var y by mutableFloatStateOf(0f)
    internal var anchor = -1
    internal var current = -1
    internal var select = true
    internal var before: Set<Any> = emptySet()
}

@Composable
fun rememberDragSelectState(): DragSelectState = remember { DragSelectState() }

/**
 * Picking rows of a lazy list by long pressing one and dragging over the
 * others, as in a phone's photo gallery: the pressed row flips, every row
 * the finger passes takes the same state, and rows passed back over return
 * to how they were. The list scrolls when the finger nears its top or
 * bottom. Put it on the LazyColumn; [keys] are the item keys that can be
 * picked, in the order shown; [selected] what is picked now; [onChange]
 * gets the new selection. A tap stays the row's own click.
 */
@Composable
fun <K : Any> Modifier.dragSelect(
    state: DragSelectState,
    list: LazyListState,
    keys: List<K>,
    selected: Set<K>,
    enabled: Boolean = true,
    onChange: (Set<K>) -> Unit,
): Modifier {
    val order by rememberUpdatedState(keys)
    val picked by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 56.dp.toPx() }

    /** The index (in keys) of the row at list position y, or -1. */
    fun rowAt(y: Float): Int {
        val items = list.layoutInfo.visibleItemsInfo.filter { it.key in order }
        val hit = items.firstOrNull { y >= it.offset && y < it.offset + it.size }
            // between rows or past the ends: the nearest one
            ?: items.minByOrNull { minOf(kotlin.math.abs(y - it.offset), kotlin.math.abs(y - (it.offset + it.size))) }
            ?: return -1
        return order.indexOf(hit.key)
    }

    fun apply(to: Int) {
        if (to < 0 || to == state.current) return
        state.current = to
        val range = minOf(state.anchor, to)..maxOf(state.anchor, to)
        // before holds the keys of this list (set below from picked)
        @Suppress("UNCHECKED_CAST")
        val next = (state.before as Set<K>).toMutableSet()
        range.forEach { i -> order.getOrNull(i)?.let { if (state.select) next += it else next -= it } }
        change(next)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // near an edge the list scrolls under the still finger
    LaunchedEffect(state.active) {
        while (state.active) {
            withFrameNanos { }
            val info = list.layoutInfo
            val top = info.viewportStartOffset + info.beforeContentPadding.toFloat()
            val bottom = info.viewportEndOffset - info.afterContentPadding.toFloat()
            val speed = when {
                state.y < top + edge -> -((top + edge - state.y) / edge).coerceIn(0f, 1f) * 24f
                state.y > bottom - edge -> ((state.y - (bottom - edge)) / edge).coerceIn(0f, 1f) * 24f
                else -> 0f
            }
            if (speed != 0f && list.scrollBy(speed) != 0f) apply(rowAt(state.y))
        }
    }

    return this.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        // read before the rows (Initial pass): once selecting, the moves and
        // the lift are ours, so the list does not scroll by them and no row
        // takes them as a click
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed || c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                true
            }
            if (ended != null) return@awaitEachGesture
            val y0 = down.position.y + list.layoutInfo.viewportStartOffset
            val at = rowAt(y0)
            if (at < 0 || list.layoutInfo.visibleItemsInfo.none { it.key == order[at] && y0 >= it.offset && y0 < it.offset + it.size }) {
                return@awaitEachGesture // not on a row that can be picked
            }
            state.before = picked
            state.select = order[at] !in picked
            state.anchor = at
            state.current = -1
            state.y = y0
            state.active = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            apply(at)
            try {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    c.consume()
                    if (!c.pressed) break
                    state.y = c.position.y + list.layoutInfo.viewportStartOffset
                    apply(rowAt(state.y))
                }
            } finally {
                state.active = false
            }
        }
    }
}
