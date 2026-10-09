package com.edgeore.verifier

import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.receipts.Evidence
import com.edgeore.app.receipts.KeyRegistry
import com.edgeore.app.receipts.ReceiptDraft
import com.edgeore.app.receipts.ReceiptKind
import com.edgeore.app.receipts.ReceiptLog
import com.edgeore.app.receipts.SoftwareReceiptSigner
import com.edgeore.app.receipts.WalletEvidence
import com.edgeore.app.solana.SolanaMessage
import java.io.File
import java.util.Base64

/**
 * Builds a receipt export through the app's real code path: ReceiptLog.append + ReceiptLog.export, a software
 * receipt key, and a devnet-shaped System Program transfer signed by a real (never funded) Ed25519 test key.
 * This is a generated fixture, not a receipt from a real transfer.
 */
object Fixtures {
    class Export(val json: String, val signer: SoftwareReceiptSigner, val walletSignature: String)

    fun export(dir: File, receiptKey: SoftwareReceiptSigner = SoftwareReceiptSigner()): Export {
        dir.mkdirs()
        val payer = Ed25519.KeyPair(ByteArray(32) { 7 })
        val to = Ed25519.KeyPair(ByteArray(32) { 3 }).publicKey
        val lamports = 1_000_000L
        val message = SolanaMessage.buildTransfer(payer.publicKey, to, lamports, ByteArray(32) { 9 })
        val sig = Base58.encode(payer.sign(message))
        val log = ReceiptLog(File(dir, "receipts.jsonl"), receiptKey, keys = KeyRegistry(File(dir, "keys.json")))
        log.append(ReceiptDraft(ReceiptKind.NODE, "PAIRED", "Node paired", "READ_NODE", "https://127.0.0.1:9843",
            localObservation = Evidence("CHECKED", "pinned TLS certificate SHA-256")))
        log.append(ReceiptDraft(ReceiptKind.REVIEW, "SIGNED_NOT_BROADCAST", "Signed transfer", "0.001 SOL to a test address", "MWA", "devnet",
            operationId = "op-fixture-1", solanaSignature = sig, digests = mapOf("messageSha256" to Sha256.hex(message)), lamports = lamports,
            wallet = WalletEvidence(Base64.getEncoder().encodeToString(message), Base58.encode(payer.publicKey), sig)))
        return Export(log.export(), receiptKey, sig)
    }
}
