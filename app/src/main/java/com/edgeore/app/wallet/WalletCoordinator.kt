package com.edgeore.app.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult

/**
 * Mobile Wallet Adapter coordinator. Private keys never leave the wallet app; this class only
 * receives the authorized public key and wallet-signed bytes, which are verified by the caller.
 */
class WalletCoordinator {
    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://edgeore.ai"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "EdgeORE",
        ),
    ).apply { blockchain = Solana.Devnet }

    sealed interface ConnectResult {
        data class Authorized(val publicKey: ByteArray, val label: String?) : ConnectResult
        data class Refused(val reason: String) : ConnectResult
    }

    sealed interface SignResult {
        data class Signed(val signedTransaction: ByteArray) : SignResult
        data class Refused(val reason: String) : SignResult
    }

    suspend fun connect(sender: ActivityResultSender): ConnectResult = when (val result = adapter.connect(sender)) {
        is TransactionResult.Success -> {
            val account = result.authResult.accounts.firstOrNull()
            val key = account?.publicKey
            if (key == null || key.size != 32) ConnectResult.Refused("Wallet returned an invalid account")
            else ConnectResult.Authorized(key.copyOf(), account.accountLabel)
        }
        is TransactionResult.NoWalletFound -> ConnectResult.Refused("No compatible wallet installed")
        is TransactionResult.Failure -> ConnectResult.Refused("Authorization failed: ${result.message}")
    }

    suspend fun disconnect(sender: ActivityResultSender) {
        adapter.disconnect(sender)
    }

    /** Asks the wallet to sign (not send) one transaction. The caller must verify returned bytes. */
    suspend fun signTransaction(sender: ActivityResultSender, unsignedTransaction: ByteArray): SignResult {
        val result = adapter.transact(sender) { _ -> signTransactions(arrayOf(unsignedTransaction)) }
        return when (result) {
            is TransactionResult.Success -> {
                val payloads = result.payload.signedPayloads
                if (payloads.size != 1) SignResult.Refused("Wallet returned ${payloads.size} payloads; expected 1")
                else SignResult.Signed(payloads[0].copyOf())
            }
            is TransactionResult.NoWalletFound -> SignResult.Refused("No compatible wallet installed")
            is TransactionResult.Failure -> SignResult.Refused("Wallet declined or failed: ${result.message}")
        }
    }
}
