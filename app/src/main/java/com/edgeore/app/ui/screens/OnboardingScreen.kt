package com.edgeore.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.EdgeOreHeader
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.theme.EdgeColors

/**
 * First-run introduction: what EdgeORE does, what it does not do, devnet-only and wallet-required.
 * Shown once before the tabs; reopen it from About. No claim here goes beyond what the build implements.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EdgeOreHeader()
            Text("Welcome", style = MaterialTheme.typography.headlineMedium, color = EdgeColors.textPrimary, modifier = Modifier.semantics { heading() })
            Text("A one-minute read before you start. You can open it again from About.", style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)

            Section(EdgeIcons.Check, EdgeColors.mint, "What EdgeORE does", listOf(
                "Builds one action, a Solana devnet SOL transfer, and shows you the exact bytes your wallet will sign, in plain words too.",
                "Keeps signed receipts of reviews, refusals and outcomes. Anyone can check them offline with the receipt checker.",
                "Encrypts files in a vault on this phone. Vault keys stay on this device by design, so uninstalling makes the vault unrecoverable.",
                "Runs AI on this phone or on a host you own. Cloud AI is off.",
            ))
            Section(EdgeIcons.Cross, EdgeColors.danger, "What it does not do", listOf(
                "No mining income, ORE rewards, SKR payments or token. \$EdgeORE is a product name, not a token.",
                "No mainnet and no real money. Nothing here is a store-ready or production release.",
                "It never sees your wallet keys and never sends a transaction you have not reviewed and approved.",
            ))
            Section(EdgeIcons.Pulse, EdgeColors.copper, "Devnet only", listOf(
                "Every transfer goes to Solana devnet. Devnet SOL is free test currency with no market value.",
            ))
            Section(EdgeIcons.Wallet, EdgeColors.copper, "A wallet is required to sign", listOf(
                "Install a Mobile Wallet Adapter wallet set to devnet. EdgeORE asks it to connect and to sign; the keys stay in the wallet.",
                "Everything else (vault, AI, receipts) works without a wallet.",
            ))
            PrimaryAction("I understand · continue", icon = EdgeIcons.Check, onClick = onDone)
            Text("Built by CodesbyFebin", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun Section(icon: ImageVector, tint: Color, title: String, lines: List<String>) {
    EdgeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = EdgeColors.textPrimary, modifier = Modifier.semantics { heading() })
        }
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textPrimary, modifier = Modifier.fillMaxWidth()) }
    }
}
