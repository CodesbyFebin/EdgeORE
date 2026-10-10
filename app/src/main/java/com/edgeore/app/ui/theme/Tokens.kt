package com.edgeore.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Spacing, radius and type scales for EdgeORE screens.
 *
 * The structure (one object per scale, screens use tokens instead of raw numbers, a "display" style with tight
 * tracking for large numbers and a monospace style for ids and hashes) is adapted from OptimAI Agentic for Android,
 * app/src/main/java/com/test/agenttrade/ui/theme/DS.kt (MIT License, Copyright (c) 2026 OptimAI Agentic contributors;
 * see NOTICE). The values are EdgeORE's own, from design.md section 2: 4 dp spacing steps, 16 dp cards, 50 dp badges,
 * and no metadata text below 12 sp. Colours stay in EdgeColors (Theme.kt).
 */

/** 4 dp steps (design.md §2): 4, 8, 12, 16, 24, 32. */
object EdgeSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object EdgeRadius {
    val tile = 12.dp
    val card = 16.dp
    val sheet = 24.dp
    val pill = 50.dp
}

/** Minimum sizes from design.md §2. */
object EdgeTouch {
    val min = 48.dp
    val primary = 56.dp
}

object EdgeType {
    /** Large numbers (balances, counts) with tight tracking. */
    fun display(size: Int, weight: FontWeight = FontWeight.SemiBold) =
        TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = (-0.02).em, lineHeight = (size * 1.2f).sp)

    /** Hashes, addresses, byte counts and codes only (design.md §2). */
    fun mono(size: Int = 12, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = size.coerceAtLeast(12).sp, fontWeight = weight, fontFamily = FontFamily.Monospace, lineHeight = (size.coerceAtLeast(12) * 1.34f).sp)

    /** Small uppercase-free caption used above a value. Never below 12 sp. */
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
}
