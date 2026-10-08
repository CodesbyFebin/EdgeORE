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
    val Ai = outline("ai", "M9 4a3 3 0 0 0-3 3v1a3 3 0 0 0-2 5 3 3 0 0 0 3 5 3 3 0 0 0 5 1V5a2 2 0 0 0-3-1z", "M15 4a3 3 0 0 1 3 3v1a3 3 0 0 1 2 5 3 3 0 0 1-3 5 3 3 0 0 1-5 1")
    val Nodes = outline("nodes", "M4 4h16v6H4z", "M4 14h16v6H4z", "M8 7h.01", "M8 17h.01")
    val Receipts = outline("receipts", "M6 3h12v18l-3-2-3 2-3-2-3 2z", "M9 8h6", "M9 12h6")
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
    val Clock = outline("clock", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M12 7v5l3 2")
}
