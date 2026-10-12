package com.edgeore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.solana.AssetBalance

@Composable
fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).border(1.dp, EdgeColors.border, ControlShape)
        .clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = EdgeColors.textMuted, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun TokenBalanceCard(asset: AssetBalance, modifier: Modifier = Modifier, loading: Boolean = false) {
    EdgeCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(asset.symbol, style = MaterialTheme.typography.labelLarge, color = EdgeColors.textMuted)
            // Neutral local outline until official token brand assets are available; never a counterfeit brand mark.
            Icon(if (asset.symbol == "SOL") EdgeIcons.Wallet else EdgeIcons.Coins, null, tint = EdgeColors.mint, modifier = Modifier.size(20.dp))
        }
        Text(asset.amount ?: if (loading) "Reading…" else "Unavailable", style = if (asset.amount != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(if (loading) "Loading" else asset.status, style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        asset.observedAt?.let { Text(com.edgeore.app.ui.Format.time(it), style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted) }
        asset.slot?.let { Text("Slot $it", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted) }
    }
}

@Composable
fun WorkerStatusRow(icon: ImageVector, title: String, status: String, detail: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = EdgeColors.mint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = EdgeColors.textMuted)
        }
        Text(status, style = MaterialTheme.typography.labelSmall, color = EdgeColors.warning)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, detail: String) {
    EdgeCard {
        IconTile(icon)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
    }
}

@Composable
fun LoadingState(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = EdgeColors.mint, strokeWidth = 2.dp)
        Text(text, color = EdgeColors.textMuted)
    }
}
