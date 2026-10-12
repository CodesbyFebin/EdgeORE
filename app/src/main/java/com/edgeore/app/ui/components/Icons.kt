package com.edgeore.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** One outline icon family, ~2dp strokes, as the spec requires. */
object EdgeIcons {
    private fun outline(name: String, vararg paths: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

    val Mine = outline("mine", "M14 4c3 0 5.5 1.5 7 3.5", "M14 4c-3 0-5.5 1.5-7 3.5", "M14 4L4 20")
    val Brain = outline("brain", "M9 4a3 3 0 0 0-3 3v1a3 3 0 0 0-2 5 3 3 0 0 0 3 5 3 3 0 0 0 5 1V5a2 2 0 0 0-3-1z", "M15 4a3 3 0 0 1 3 3v1a3 3 0 0 1 2 5 3 3 0 0 1-3 5 3 3 0 0 1-5 1")
    val Nodes = outline("nodes", "M8 3h8v6H8z M2 15h7v6H2z M15 15h7v6h-7z", "M12 9v3 M5 15v-3h14v3")
    val Receipts = outline("receipts", "M6 3h8l4 4v14H6z", "M14 3v4h4", "M9 14l2 2 4-5")
    val Pause = outline("pause", "M9 6v12", "M15 6v12")
    val Play = outline("play", "M8 5l11 7-11 7z")
    val Chevron = outline("chevron", "M9 6l6 6-6 6")
    val Back = outline("back", "M15 6l-6 6 6 6")
    val Shield = outline("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M9 12l2 2 4-4")
    val Wallet = outline("wallet", "M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M3 7l12-4v4", "M17 14h.01")
    val Pulse = outline("pulse", "M3 12h4l3-7 4 14 3-7h4")
    val Coins = outline("coins", "M12 5c4 0 7 1.3 7 3s-3 3-7 3-7-1.3-7-3 3-3 7-3z", "M5 8v8c0 1.7 3 3 7 3s7-1.3 7-3V8", "M5 12c0 1.7 3 3 7 3s7-1.3 7-3")
    val Bolt = outline("bolt", "M13 3L5 14h6l-1 7 8-11h-6z")
    val Thermo = outline("thermo", "M10 14V5a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0z")
    val Battery = outline("battery", "M3 8h15v8H3z", "M21 11v2")
    val Cpu = outline("cpu", "M7 7h10v10H7z", "M10 3v4", "M14 3v4", "M10 17v4", "M14 17v4", "M3 10h4", "M3 14h4", "M17 10h4", "M17 14h4")
    val Link = outline("link", "M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1", "M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1")
    val Doc = outline("doc", "M6 3h8l4 4v14H6z", "M14 3v4h4", "M9 13h6", "M9 17h6")
    val Lock = outline("lock", "M6 11h12v10H6z", "M8 11V7a4 4 0 0 1 8 0v4")
    val Send = outline("send", "M4 12l16-8-6 16-2-6z")
    val Export = outline("export", "M12 3v12", "M7 8l5-5 5 5", "M5 15v5h14v-5")
    val Info = outline("info", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M12 11v5", "M12 8h.01")
    val Storage = outline("storage", "M12 4c4.4 0 8 1.3 8 3s-3.6 3-8 3-8-1.3-8-3 3.6-3 8-3z", "M4 7v10c0 1.7 3.6 3 8 3s8-1.3 8-3V7")
    val Wifi = outline("wifi", "M2 9a15 15 0 0 1 20 0", "M5 13a10 10 0 0 1 14 0", "M8.5 16.5a5 5 0 0 1 7 0", "M12 20h.01")
    val Check = outline("check", "M5 12l5 5 9-10")
    val Cross = outline("cross", "M6 6l12 12", "M18 6L6 18")
    val Ore = outline("ore", "M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16z", "M5 19L19 5")
    val Clock = outline("clock", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M12 7v5l3 2")
    val Ai get() = Bot
    val Settings = outline("settings", "M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z", "M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8z")
    val Review = outline("review", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M7 12l3 3 7-7")
    val Memory = outline("memory", "M3 7h18v10H3z", "M7 10v4 M12 10v4 M17 10v4", "M6 17v3 M10 17v3 M14 17v3 M18 17v3")
    val Globe = outline("globe", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M3 12h18", "M12 3c-5 5-5 13 0 18 5-5 5-13 0-18z")
    val Bell = outline("bell", "M6 16V9a6 6 0 0 1 12 0v7l2 2H4z", "M10 21h4")
    val Bot = outline("bot", "M5 4h14a2 2 0 0 1 2 2v11H9l-6 4V6a2 2 0 0 1 2-2z", "M12 7l1 3 3 1-3 1-1 3-1-3-3-1 3-1z")
    val Cloud = outline("cloud", "M6 18a4 4 0 0 1-1-8 7 7 0 0 1 13-1 4.5 4.5 0 0 1 0 9z")
    val Calendar = outline("calendar", "M4 5h16v16H4z", "M8 3v4 M16 3v4 M4 10h16", "M8 15l2 2 5-5")
    val Gift = outline("gift", "M3 8h18v4H3z M5 12v9h14v-9 M12 8v13", "M12 8C4 8 5 1 9 4z M12 8c8 0 7-7 3-4z")
    val Stop = outline("stop", "M6 6h12v12H6z")
    val Refresh = outline("refresh", "M20 10a8 8 0 1 0-1 7 M20 4v6h-6")
    val Copy = outline("copy", "M8 8h13v13H8z M16 4H4v12")
    val External = outline("external", "M14 3h7v7 M21 3l-12 12 M10 3H3v18h18v-7")
    val Download = outline("download", "M12 3v12 M7 10l5 5 5-5 M4 17v4h16v-4")
    val Upload = Export
    val Warning = outline("warning", "M12 3l10 18H2z M12 9v5 M12 17h.01")
    val Plus = outline("plus", "M12 5v14 M5 12h14")
    val Minus = outline("minus", "M5 12h14")
    val Search = outline("search", "M10 3a7 7 0 1 1 0 14 7 7 0 0 1 0-14z M15 15l6 6")
    val Filter = outline("filter", "M3 7h18 M3 17h18 M8 4v6 M16 14v6")
    val Sun = outline("sun", "M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8z M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1 1 M18 18l1 1 M5 19l1-1 M18 6l1-1")
    val Moon = outline("moon", "M20 14A9 9 0 0 1 10 3a9 9 0 1 0 10 11z")
    val Location = outline("location", "M12 22C4 14 4 11 4 8a8 8 0 0 1 16 0c0 3 0 6-8 14z", "M12 5a3 3 0 1 1 0 6 3 3 0 0 1 0-6z")
    val Compass = outline("compass", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z M16 8l-3 5-5 3 3-5z")
    val Social = outline("social", "M8 3a4 4 0 1 1 0 8 4 4 0 0 1 0-8z M2 21v-3a6 6 0 0 1 12 0v3 M17 4a4 4 0 0 1 0 8 M17 15a5 5 0 0 1 5 5v1")
}
