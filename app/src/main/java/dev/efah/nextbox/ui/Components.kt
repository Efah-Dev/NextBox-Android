package dev.efah.nextbox.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType

@Composable
fun NbCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    radius: Dp = 20.dp,
    color: Color = Nb.c.card,
    border: Color = Nb.c.cardBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val m = modifier
        .clip(shape)
        .background(color)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Surface(
        modifier = m,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun TopBar(title: String, modifier: Modifier = Modifier, leading: @Composable (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(start = if (leading == null) 20.dp else 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(title, style = NbType.title, color = Nb.c.text, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun BarIcon(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Nb.c.text) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = NbType.section, color = Nb.c.muted, modifier = modifier.padding(start = 6.dp, bottom = 6.dp))
}

/** A row of mutually exclusive options with a sliding highlight. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val c = Nb.c
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected.coerceAtLeast(0), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        if (selected >= 0) {
            Box(
                Modifier
                    .offset(x = x)
                    .width(w)
                    .height(height)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.accentSoft),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                val color by animateColorAsState(if (sel) c.accentInk else c.muted, label = "segc")
                Box(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/** A settings group: title plus a card of rows separated by hairlines. */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title)
        NbCard(padding = PaddingValues(0.dp)) { content() }
    }
}

@Composable
fun RowDivider() = HorizontalDivider(color = Nb.c.line, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))

@Composable
fun SwitchRow(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Nb.c
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = NbType.body, color = if (enabled) c.text else c.muted)
            if (!sub.isNullOrEmpty()) Text(sub, style = NbType.small, color = c.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accentStrong,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = c.card,
                uncheckedBorderColor = c.muted,
                uncheckedThumbColor = c.muted,
            ),
        )
    }
}

@Composable
fun NavRow(label: String, sub: String? = null, value: String = "", onClick: () -> Unit) {
    val c = Nb.c
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = NbType.body, color = c.text)
            if (!sub.isNullOrEmpty()) Text(sub, style = NbType.small, color = c.muted)
        }
        if (value.isNotEmpty()) {
            Text(value, style = NbType.small.copy(fontSize = 13.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
        Icon(NbIcons.Chevron, null, tint = c.muted, modifier = Modifier.padding(start = 4.dp).size(18.dp))
    }
}

/** A thin horizontal bar, e.g. subscription usage or a node's share of traffic. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Nb.c.accent, height: Dp = 6.dp) {
    val f by androidx.compose.animation.core.animateFloatAsState(fraction.coerceIn(0f, 1f), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Nb.c.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val c = Nb.c
    val bg by animateColorAsState(if (selected) c.accentSoft else Color.Transparent, label = "chip")
    val fg by animateColorAsState(if (selected) c.accentInk else c.muted, label = "chipf")
    val borderColor by animateColorAsState(if (selected) Color.Transparent else c.line, label = "chipb")
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .let {
                if (onLongClick != null) {
                    it.combinedClickableCompat(onClick, onLongClick)
                } else {
                    it.clickable(onClick = onClick)
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))

@Composable
fun EmptyHint(title: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = NbType.bodyBold, color = Nb.c.text)
        Text(sub, style = NbType.small, color = Nb.c.muted)
    }
}
