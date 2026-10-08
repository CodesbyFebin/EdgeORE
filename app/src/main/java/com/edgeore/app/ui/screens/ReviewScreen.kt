package com.edgeore.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.ReviewPhase
import com.edgeore.app.solana.SolanaMessage
import com.edgeore.app.solana.SolanaRpc
import com.edgeore.app.ui.Format
import com.edgeore.app.ui.components.Capability
import com.edgeore.app.ui.components.CapabilityBadge
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.KeyValue
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.SectionTitle
import com.edgeore.app.ui.theme.EdgeColors

/** Dedicated review route: the only path to a wallet signature. */
@Composable
fun ReviewScreen(vm: EdgeOreViewModel, onSign: () -> Unit, onConnect: () -> Unit) {
    val st by vm.review.collectAsStateWithLifecycle()
    val wallet by vm.walletState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val editing = st.phase == ReviewPhase.EDITING || st.phase == ReviewPhase.PREPARING

    SectionTitle("Review action", "Supported: System Program transfer on Solana devnet.")
    EdgeCard {
        KeyValue("Network", "Solana ${SolanaRpc.CLUSTER} (${SolanaRpc.DEVNET})")
        KeyValue("Account", wallet.address ?: "Not connected", mono = true)
        KeyValue("Daily budget", "${Format.sol(settings.dailyLimitLamports)} · spent today ${Format.sol(vm.spentTodayLamports())}")
        if (wallet.address == null) SecondaryAction("Connect wallet", onClick = onConnect)
    }

    if (editing) EdgeCard {
        val shape = RoundedCornerShape(24.dp)
        OutlinedTextField(st.destination, { vm.editReview(destination = it) }, label = { Text("Destination address") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
        if (wallet.address != null && st.destination.isBlank()) SecondaryAction("Use my own address (self-transfer)") { vm.editReview(destination = wallet.address) }
        OutlinedTextField(st.amount, { vm.editReview(amount = it) }, label = { Text("Amount (SOL, ≤ 9 decimals)") }, singleLine = true, shape = shape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        PrimaryAction("Prepare exact-message review", icon = EdgeIcons.Shield, enabled = wallet.address != null, loading = st.phase == ReviewPhase.PREPARING) { vm.prepareReview() }
    }

    st.draft?.let { d ->
        EdgeCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Decoded from the exact bytes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                CapabilityBadge(if (d.approvable) Capability.AVAILABLE else Capability.UNAVAILABLE, if (d.approvable) "Supported" else "Approval disabled")
            }
            when (val dec = d.decoded) {
                is SolanaMessage.Decoded.SupportedTransfer -> {
                    KeyValue("Action", "System Program · Transfer")
                    KeyValue("Program", "11111111111111111111111111111111", mono = true)
                    KeyValue("From (fee payer)", d.fromAddress, mono = true)
                    KeyValue("To", d.toAddress + if (d.fromAddress == d.toAddress) "  (self-transfer)" else "", mono = true)
                    KeyValue("Amount", "${Format.sol(dec.transfer.lamports)} (${dec.transfer.lamports} lamports)")
                    KeyValue("Network fee", if (st.feeKnown) "${st.feeLamports} lamports (RPC getFeeForMessage)" else "Unknown · RPC could not price this message")
                    KeyValue("Recent blockhash", d.blockhash, mono = true)
                }
                is SolanaMessage.Decoded.Unsupported -> Notice("Unsupported: ${dec.reason}", error = true)
            }
            if (d.message.isNotEmpty()) {
                KeyValue("Message SHA-256 (${d.message.size} bytes)", d.messageSha256, mono = true)
                Text("Binding: the wallet must return these exact bytes, signed by the account above, or the result is refused. A new blockhash needs a new review.",
                    style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
            }
        }
    }

    st.message?.let { Notice(it, error = st.phase == ReviewPhase.REFUSED || st.phase == ReviewPhase.EDITING) }

    when (st.phase) {
        ReviewPhase.READY, ReviewPhase.SIGNING -> {
            PrimaryAction("Approve & sign in wallet", icon = EdgeIcons.Wallet, enabled = st.draft?.approvable == true, loading = st.phase == ReviewPhase.SIGNING, onClick = onSign)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction("Refuse", Modifier.weight(1f), danger = true) { vm.refuseReview() }
                SecondaryAction("New review", Modifier.weight(1f)) { vm.resetReview() }
            }
        }
        ReviewPhase.SIGNED, ReviewPhase.SUBMITTING -> {
            EdgeCard {
                CapabilityBadge(Capability.IMPLEMENTED, "Signature verified over reviewed bytes")
                KeyValue("Signature", st.verified?.signature ?: "", mono = true)
                Text("Signed but not broadcast. Submitting spends devnet SOL only.", style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
            }
            PrimaryAction("Submit to devnet", icon = EdgeIcons.Send, loading = st.phase == ReviewPhase.SUBMITTING) { vm.submitSigned() }
            SecondaryAction("Keep unsent · new review", Modifier.fillMaxWidth()) { vm.resetReview() }
        }
        ReviewPhase.SUBMITTED -> {
            EdgeCard {
                KeyValue("Submitted signature", st.submittedSignature ?: "", mono = true)
                KeyValue("Confirmation", st.confirmation ?: "Not observed yet")
            }
            PrimaryAction("Check devnet status", icon = EdgeIcons.Pulse) { vm.checkConfirmation() }
            SecondaryAction("New review", Modifier.fillMaxWidth()) { vm.resetReview() }
        }
        ReviewPhase.REFUSED -> SecondaryAction("Start a new review", Modifier.fillMaxWidth()) { vm.resetReview() }
        else -> Unit
    }
    Notice("Signing is only reachable from this route. Receipts record the outcome either way.")
}

@Composable
fun ConceptPreviewScreen() {
    com.edgeore.app.ui.components.ConceptBadge()
    SectionTitle("Preview session", "Illustrative only. Nothing here is measured or earned.")
    EdgeCard(inset = true) {
        Text("Illustrative workload · last 60 minutes", style = MaterialTheme.typography.titleMedium)
        com.edgeore.app.ui.components.LineChart(
            listOf(
                listOf(3f, 5f, 4f, 6f, 5f, 7f, 6f, 8f, 6f, 7f, 5f, 6f) to EdgeColors.copper,
                listOf(2f, 3f, 3f, 4f, 3f, 4f, 5f, 4f, 5f, 4f, 4f, 5f) to EdgeColors.mint,
            ),
            Modifier.fillMaxWidth().height(140.dp),
        )
        Text("Copper: AI inference (sample) · Mint: model processing (sample). No NPU path is measured.", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
    }
    EdgeCard {
        Text("What a real session would require", style = MaterialTheme.typography.titleMedium)
        listOf(
            "A qualified workload adapter with pinned versions",
            "Measured device telemetry and enforced resource limits",
            "A selected, qualified ORE protocol flow with exact-message review",
            "Independent verification before any reward claim",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

