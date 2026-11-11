package dev.efah.nextbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.efah.nextbox.ui.theme.Nb

/**
 * A screen whose header is a glass bar: the content scrolls underneath it.
 * content receives the padding that keeps its first and last items clear of
 * the header and of the floating navigation bar.
 */
@Composable
fun GlassScreen(
    header: @Composable ColumnScope.() -> Unit,
    floating: @Composable BoxScope.(Backdrop) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Nb.c
    val backdrop = rememberBackdrop()
    val density = LocalDensity.current
    var headerPx by remember { mutableIntStateOf(0) }
    val top = with(density) { headerPx.toDp() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            content(PaddingValues(top = top, bottom = LocalBottomInset.current))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height }
                .glass(
                    backdrop, radius = 0.dp, refraction = 0.dp, rim = false,
                    tint = c.bg.copy(alpha = if (c.dark) 0.62f else 0.66f),
                    solid = c.bg,
                ),
        ) {
            header()
            if (LocalGlass.current) HorizontalDivider(color = c.line.copy(alpha = 0.6f), thickness = 0.5.dp)
        }
        floating(backdrop)
    }
}

/** AlertDialog with the app's surface color and, with glass effects on, a blurred background. */
@Composable
fun NbAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val c = Nb.c
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = {
            WindowBlurBehind()
            text?.invoke()
        },
        containerColor = if (LocalGlass.current) c.card.copy(alpha = if (c.dark) 0.86f else 0.9f) else c.card,
        titleContentColor = c.text,
        textContentColor = c.text,
        tonalElevation = 0.dp,
    )
}
