package com.edgeore.app.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant

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
        data class Authorized(val publicKey: ByteArray, val label: String?, val accountsReturned: Int = 1) : ConnectResult
        data class Failed(val failure: ConnectFailure) : ConnectResult
    }

    sealed interface SignResult {
        data class Signed(val signedTransaction: ByteArray) : SignResult
        data class Refused(val reason: String) : SignResult
    }

    /**
     * One authorize round trip. Every TransactionResult type and every exception the SDK can throw
     * (e.g. InterruptedException when the wallet activity returns RESULT_CANCELED before the session
     * completes) becomes a classified result; only cancellation of the caller's own coroutine propagates.
     */
    suspend fun connect(sender: ActivityResultSender): ConnectResult {
        val sentAuthToken = adapter.authToken != null
        val result = try {
            adapter.connect(sender)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            return ConnectResult.Failed(WalletAuthorization.classifyThrowable(e, sentAuthToken))
        } catch (e: Exception) {
            return ConnectResult.Failed(WalletAuthorization.classifyThrowable(e, sentAuthToken))
        }
        if (result !is TransactionResult.Success) {
            return ConnectResult.Failed(WalletAuthorization.classify(result, sentAuthToken)!!)
        }
        val accounts = try {
            result.authResult.accounts?.map { AccountCandidate(it.publicKey, it.accountLabel) }
        } catch (e: IllegalStateException) {
            null // Success without an AuthorizationResult (SDK accessor throws)
        }
        return when (val check = WalletAuthorization.validateAccounts(accounts)) {
            is WalletAuthorization.AccountCheck.Valid -> ConnectResult.Authorized(check.publicKey, check.label, accounts?.size ?: 0)
            is WalletAuthorization.AccountCheck.Invalid -> ConnectResult.Failed(ConnectFailure(check.code, Instant.now().toString(), null))
        }
    }

    /** Returns true only when the SDK reported a successful deauthorize session. */
    suspend fun disconnect(sender: ActivityResultSender): Boolean = try {
        adapter.disconnect(sender) is TransactionResult.Success
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive(); false
    } catch (e: Exception) {
        false
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
