package com.edgeore.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** MWA uses Android's compatible-wallet routing. Listed apps are discovered, not assumed compatible or tested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletConnectSheet(onDismiss: () -> Unit, onConnect: () -> Unit) {
    val context = LocalContext.current
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("solana-wallet:/v1/associate/local"))
    val discovered = context.packageManager.queryIntentActivities(intent, 0).map { it.loadLabel(context.packageManager).toString() }.distinct()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(EdgeIcons.Wallet)
            Text("Connect a Solana wallet", style = MaterialTheme.typography.headlineSmall)
            Text("Authorize your Solana account through Mobile Wallet Adapter. Your keys stay in your wallet. Connection does not approve a transfer or start a paid service.")
            Text(if (discovered.isEmpty()) "No compatible wallet app was discovered. Install an MWA-compatible Solana wallet, then try again." else "Discovered wallet apps: ${discovered.joinToString()}. Capability depends on the wallet and device.")
            StatusPill("Devnet authorization", tone = PillTone.MINT, icon = EdgeIcons.Globe)
            PrimaryAction("Continue to wallet", icon = EdgeIcons.Wallet, onClick = onConnect)
            Text("Android will route the connection to an installed compatible wallet. Cancellation returns here without signing.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
