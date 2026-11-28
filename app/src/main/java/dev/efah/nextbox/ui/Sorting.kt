package dev.efah.nextbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.ui.theme.Nb

/**
 * Sorting nodes, as on NextBox for Windows (store.SortGroup): a key, and a
 * direction: "" (the normal order: the subscription's), "asc" (low to high)
 * or "desc" (high to low). One tap moves to the next direction.
 */
object Sorting {
    fun next(dir: String): String = when (dir) {
        "" -> "asc"
        "asc" -> "desc"
        else -> ""
    }

    fun icon(dir: String): ImageVector = when (dir) {
        "asc" -> NbIcons.SortAsc
        "desc" -> NbIcons.SortDesc
        else -> NbIcons.SortNone
    }

    fun dirLabel(dir: String): String = when (dir) {
        "asc" -> T("从低到高")
        "desc" -> T("从高到低")
        else -> T("正常顺序")
    }

    /** The name of a sort key (store.SortBy*). */
    fun keyLabel(key: String): String = when (key) {
        "latency" -> T("延迟")
        "name" -> T("名称")
        "type" -> T("协议")
        "server" -> T("服务器")
        "region" -> T("地区")
        else -> T("默认排序")
    }

    /** Sorted by latency in [dir]; untested and failed ones stay last, both ways. */
    fun <T> byLatency(list: List<T>, dir: String, latency: (T) -> Int): List<T> {
        if (dir.isEmpty()) return list
        val (tested, rest) = list.partition { latency(it) > 0 }
        val sorted = if (dir == "desc") tested.sortedByDescending(latency) else tested.sortedBy(latency)
        return sorted + rest
    }
}

/**
 * A sort control: the direction's icon, with [label] next to it when given.
 * Tinted while it sorts (dir not "").
 */
@Composable
fun SortChip(dir: String, label: String?, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Nb.c
    val on = dir.isNotEmpty()
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(11.dp))
            .background(if (on) c.accentSoft else Color.Transparent)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = if (label != null) 10.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Sorting.icon(dir), description, tint = if (on) c.accentInk else c.muted, modifier = Modifier.size(18.dp))
        if (label != null) Text(label, color = if (on) c.accentInk else c.muted, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}
