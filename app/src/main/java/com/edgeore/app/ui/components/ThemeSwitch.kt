package com.edgeore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeShapes
import com.edgeore.app.ui.theme.ThemeMode

/** Settings: Dark | Light | System. The choice is persisted by the caller (ThemePrefs). */
@Composable
fun ThemeSwitch(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("theme-switch")) {
        Text("Theme", style = MaterialTheme.typography.titleSmall, color = EdgeColors.textPrimary)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).border(1.dp, EdgeColors.border, EdgeShapes.pill).padding(3.dp)) {
            ThemeMode.entries.forEach { m ->
                val on = m == mode
                Box(Modifier.weight(1f).heightIn(min = 44.dp).background(if (on) EdgeColors.surfacePressed else Color.Transparent, EdgeShapes.pill)
                    .then(if (on) Modifier.border(1.dp, EdgeColors.mint, EdgeShapes.pill) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(m) }.semantics { selected = on }.testTag("theme-${m.name.lowercase()}"),
                    contentAlignment = Alignment.Center) {
                    Text(m.label, color = if (on) EdgeColors.textPrimary else EdgeColors.textMuted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
