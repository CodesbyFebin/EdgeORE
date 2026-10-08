package com.edgeore.app.solana

import com.edgeore.app.EdgeOreCore
import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.crypto.Sha256

/**
 * Exact-message review policy for the one supported action (devnet System Program transfer).
 * Approval is only possible when the decoded bytes are a supported transfer that matches the
 * requested inputs and fits the daily budget. Wallet-returned bytes must match the reviewed message.
 */
object TransferReview {

    data class Draft(
        val message: ByteArray,
        val messageSha256: String,
        val decoded: SolanaMessage.Decoded,
        val lamports: Long,
        val fromAddress: String,
        val toAddress: String,
        val blockhash: String,
        val refusal: String?,
    ) {
        val approvable: Boolean get() = refusal == null && decoded is SolanaMessage.Decoded.SupportedTransfer
    }

    fun prepare(
        from: ByteArray,
        toAddress: String,
        amountSol: String,
        recentBlockhash: ByteArray,
        spentTodayLamports: Long,
        dailyLimitLamports: Long,
    ): Draft {
        val to = try { Base58.decodePublicKey(toAddress.trim()) } catch (_: Exception) { return refusedDraft("Destination is not a valid Solana address") }
        val lamports = try { EdgeOreCore.lamports(amountSol.trim()) } catch (_: Exception) { return refusedDraft("Amount must be a positive SOL value with at most 9 decimals") }
        if (lamports <= 0) return refusedDraft("Amount must be greater than zero")
        val message = SolanaMessage.buildTransfer(from, to, lamports, recentBlockhash)
        val decoded = SolanaMessage.decode(message)
        var refusal: String? = null
        if (decoded !is SolanaMessage.Decoded.SupportedTransfer) {
            refusal = (decoded as SolanaMessage.Decoded.Unsupported).reason
        } else {
            val t = decoded.transfer
            if (!t.from.contentEquals(from) || !t.to.contentEquals(to) || t.lamports != lamports) refusal = "Decoded message does not match requested transfer"
        }
        if (refusal == null && !EdgeOreCore.eligible(lamports, spentTodayLamports, dailyLimitLamports)) refusal = "Daily budget would be exceeded"
        return Draft(message, Sha256.hex(message), decoded, lamports, Base58.encode(from), Base58.encode(to), Base58.encode(recentBlockhash), refusal)
    }

    private fun refusedDraft(reason: String) = Draft(ByteArray(0), "", SolanaMessage.Decoded.Unsupported(reason), 0, "", "", "", reason)

    sealed interface WalletReturn {
        data class Verified(val signedTransaction: ByteArray, val signature: String) : WalletReturn
        data class Refused(val reason: String) : WalletReturn
    }

    /** Checks wallet-returned bytes: same message (exact bytes) and a valid signature by the fee payer. */
    fun verifyWalletReturn(reviewedMessage: ByteArray, signer: ByteArray, signedTransaction: ByteArray): WalletReturn {
        val split = try { SolanaMessage.splitTransaction(signedTransaction) } catch (_: Exception) { return WalletReturn.Refused("Wallet returned malformed transaction bytes") }
        if (!EdgeOreCore.sameMessage(reviewedMessage, split.message)) return WalletReturn.Refused("Wallet returned a different message than the one reviewed")
        if (split.signatures.size != 1) return WalletReturn.Refused("Unexpected signature count")
        val sig = split.signatures[0]
        if (!Ed25519.verify(signer, split.message, sig)) return WalletReturn.Refused("Wallet signature does not verify for the connected account")
        return WalletReturn.Verified(signedTransaction, Base58.encode(sig))
    }
}
