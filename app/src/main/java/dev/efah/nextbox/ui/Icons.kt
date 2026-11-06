package dev.efah.nextbox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons drawn on a 24×24 grid (no icon library, to keep the APK small). */
object NbIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun fill(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        }.build()

    val Home = stroke("home", "M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z")
    val Globe = stroke(
        "globe",
        "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17",
        "M3.5 12h17",
        "M12 3.5c2.5 2.6 3.6 5.4 3.6 8.5s-1.1 5.9-3.6 8.5",
        "M12 3.5C9.5 6.1 8.4 8.9 8.4 12s1.1 5.9 3.6 8.5",
    )
    val Chart = stroke("chart", "M5 20v-8", "M10 20V5", "M15 20v-6", "M20 20V9", width = 2.2f)
    val Activity = stroke("activity", "M3 12h4l3-7 4 14 3-7h4")
    val Settings = stroke(
        "settings",
        "M4 7h9M17 7h3M4 12h3M11 12h9M4 17h11M19 17h1",
        "M17 7a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
        "M11 12a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
        "M19 17a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
    )
    val Bolt = stroke("bolt", "M13 3 5 13.5h6L10 21l8-10.5h-6z")
    val Plus = stroke("plus", "M12 5v14M5 12h14", width = 2.2f)
    val Check = stroke("check", "m5 12.5 4.5 4.5L19 7.5", width = 2.4f)
    val Back = stroke("back", "M19 12H5M11 6l-6 6 6 6", width = 2f)
    val Refresh = stroke("refresh", "M20 12a8 8 0 1 1-2.4-5.7M20 4v5h-5")
    val Chevron = stroke("chevron", "m9 6 6 6-6 6", width = 2f)
    val Clipboard = stroke("clipboard", "M8 4.5H6.5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-12a2 2 0 0 0-2-2H16", "M9 3.5h6v3H9z")
    val Rss = stroke("rss", "M5 12a7 7 0 0 1 7 7", "M5 5a14 14 0 0 1 14 14", "M5.5 18.5h.01", width = 2f)
    val Pen = stroke("pen", "M4 20h4L19 9l-4-4L4 16z")
    val Folder = stroke("folder", "M3.5 7a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    val Close = stroke("close", "M6 6l12 12M18 6 6 18", width = 2f)
    val More = fill(
        "more",
        "M12 3.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
        "M12 10.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
        "M12 17.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
    )
}
