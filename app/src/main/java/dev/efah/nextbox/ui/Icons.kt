package dev.efah.nextbox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.ImageVector
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
    val Search = stroke("search", "M11 4.5a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13", "M16 16l4 4")
    val Trash = stroke("trash", "M5 7h14", "M10 11v6M14 11v6", "M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12", "M9 7V4h6v3")
    val Scan = stroke("scan", "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10")
    val Plus = stroke("plus", "M12 5v14M5 12h14", width = 2.2f)
    val Check = stroke("check", "m5 12.5 4.5 4.5L19 7.5", width = 2.4f)
    val Back = stroke("back", "M19 12H5M11 6l-6 6 6 6", width = 2f)
    val Refresh = stroke("refresh", "M20 12a8 8 0 1 1-2.4-5.7M20 4v5h-5")
    val Chevron = stroke("chevron", "m9 6 6 6-6 6", width = 2f)
    val Clipboard = stroke("clipboard", "M8 4.5H6.5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-12a2 2 0 0 0-2-2H16", "M9 3.5h6v3H9z")
    val Rss = stroke("rss", "M5 12a7 7 0 0 1 7 7", "M5 5a14 14 0 0 1 14 14", "M5.5 18.5h.01", width = 2f)
    val Folder = stroke("folder", "M3.5 7a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    val Close = stroke("close", "M6 6l12 12M18 6 6 18", width = 2f)
    val Shield = stroke("shield", "M12 3l7 3v5c0 4.5-3 8.3-7 10-4-1.7-7-5.5-7-10V6z")
    val Power = stroke("power", "M12 3v8", "M7.5 6.5a7 7 0 1 0 9 0")
    val Swap = stroke("swap", "M4 8h13l-3.5-3.5", "M20 16H7l3.5 3.5")
    val Route = stroke("route", "M6 21a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z", "M18 8a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z", "M8.5 18.5H13a3 3 0 0 0 3-3V11")
    val Apps = stroke("apps", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z")
    val Bell = stroke("bell", "M6 16v-5a6 6 0 1 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val Palette = stroke(
        "palette",
        "M12 3a9 9 0 1 0 0 18c1.1 0 1.5-.8 1.5-1.6 0-1.2-1-1.4-1-2.4 0-.8.6-1.5 1.5-1.5H16a5 5 0 0 0 5-5C21 6.4 17 3 12 3z",
        "M7.5 11h.01", "M10 7.5h.01", "M15 7.5h.01",
        width = 1.8f,
    )
    val Sparkle = stroke("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z", "M19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z")
    val Archive = stroke("archive", "M4 8h16v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z", "M3 4h18v4H3z", "M10 12h4")
    val Info = stroke("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 11v5", "M12 8h.01")
    val Help = stroke("help", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M9.6 9.4a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1.1.9-1.1 1.6v.5", "M12 16.9h.01", width = 1.7f)
    val Edit = stroke("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Qr = stroke("qr", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h2v2h-2z", "M18 18h2v2h-2z", "M14 18h2")
    val Chain = stroke("chain", "M5 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", "M12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", "M19 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", "M7 12h3M14 12h3")
    val CheckBox = stroke("checkbox", "M4 8a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z", "m8 12 3 3 5-6")
    val Sort = stroke("sort", "M7 4v16M3.5 16.5 7 20l3.5-3.5", "M13 6h8M13 11h6M13 16h4")
    val Copy = stroke("copy", "M9 9h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V10a1 1 0 0 1 1-1z", "M16 9V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3")
    val Layers = stroke("layers", "M12 4l8 4-8 4-8-4z", "M4 12l8 4 8-4", "M4 16l8 4 8-4")
    val Link = stroke("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val Phone = stroke("phone", "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M11 18h2")
    val Grip = fill(
        "grip",
        "M9 4.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3z", "M15 4.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3z",
        "M9 10.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3z", "M15 10.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3z",
        "M9 16.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3z", "M15 16.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3z",
    )
    val More = fill(
        "more",
        "M12 3.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
        "M12 10.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
        "M12 17.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
    )
}
