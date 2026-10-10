package com.edgeore.app.wallet

import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/** Where in Connect → association → authorization → account validation a connect attempt stopped. */
enum class ConnectStage { ASSOCIATION, SESSION, AUTHORIZATION, ACCOUNT_VALIDATION, INTERNAL }

/**
 * Connect failure categories. Each one maps to an outcome the MWA clientlib-ktx 2.0.3 SDK actually
 * produces (see [WalletAuthorization.classify]); nothing here is inferred beyond what the SDK reports.
 */
enum class ConnectFailureCode(val stage: ConnectStage, val message: String, val action: String) {
    NO_COMPATIBLE_WALLET(ConnectStage.ASSOCIATION, "No compatible wallet app answered the connect request.",
        "Install or enable a Mobile Wallet Adapter wallet on this device, then tap Connect wallet again."),
    ASSOCIATION_FAILED(ConnectStage.ASSOCIATION, "The wallet app could not be opened for this connect request.",
        "Close the wallet app, return to EdgeORE and tap Connect wallet again."),
    SESSION_TIMEOUT(ConnectStage.SESSION, "The wallet session timed out before authorization finished.",
        "Open the wallet app, complete its own sign-in first, then tap Connect wallet again."),
    SESSION_INTERRUPTED(ConnectStage.SESSION, "The wallet closed before the session finished.",
        "Return from the wallet only after it shows the result, then tap Connect wallet again."),
    SESSION_IO(ConnectStage.SESSION, "The local wallet session dropped while authorizing.",
        "Tap Connect wallet again. If it repeats, restart the wallet app."),
    WALLET_DECLINED(ConnectStage.AUTHORIZATION, "The wallet declined authorization.",
        "If you tapped Connect in the wallet, it may have failed its own user check. Unlock the wallet first, then retry."),
    AUTH_TOKEN_REJECTED(ConnectStage.AUTHORIZATION, "The wallet rejected the previous authorization.",
        "Tap Connect wallet again to request a fresh authorization."),
    CHAIN_NOT_SUPPORTED(ConnectStage.AUTHORIZATION, "The wallet does not support Solana devnet.",
        "Use a wallet that supports devnet."),
    WALLET_ERROR(ConnectStage.AUTHORIZATION, "The wallet reported an error while authorizing.",
        "Tap Connect wallet again. If it repeats, check the wallet app."),
    INVALID_AUTHORIZATION_RESPONSE(ConnectStage.AUTHORIZATION, "The wallet's authorization response could not be used.",
        "Update the wallet app, then retry."),
    ACCOUNT_VALIDATION_FAILED(ConnectStage.ACCOUNT_VALIDATION, "The wallet did not return a usable Solana account.",
        "Select or create an account in the wallet, then retry."),
    INTERNAL_ERROR(ConnectStage.INTERNAL, "EdgeORE hit an unexpected error while connecting.",
        "Tap Connect wallet again. Nothing was signed or sent."),
}

/**
 * Sanitized failure record. Holds no auth token, key material, wallet payload or wallet-supplied
 * message text: only a code, stage, UTC time, exception class names and an optional JSON-RPC code.
 */
data class ConnectFailure(
    val code: ConnectFailureCode,
    val atUtc: String,
    val exceptionType: String?,
    val causeType: String? = null,
    val rpcCode: Int? = null,
) {
    val stage: ConnectStage get() = code.stage

    /** One log line, safe for logcat and evidence files. */
    fun logLine(): String = buildString {
        append("wallet_connect_failed code=").append(code.name).append(" stage=").append(stage.name)
        append(" at=").append(atUtc)
        append(" exception=").append(exceptionType ?: "none")
        if (causeType != null) append(" cause=").append(causeType)
        if (rpcCode != null) append(" rpc_code=").append(rpcCode)
    }
}

/** One wallet-returned account, reduced to what EdgeORE validates. */
data class AccountCandidate(val publicKey: ByteArray?, val label: String?)

object WalletAuthorization {
    const val ED25519_PUBLIC_KEY_BYTES = 32
    /** MWA ProtocolContract.ERROR_AUTHORIZATION_FAILED (walletlib sends it for a declined authorize). */
    const val ERROR_AUTHORIZATION_FAILED = -1
    /** MWA ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED. */
    const val ERROR_CLUSTER_NOT_SUPPORTED = -7

    sealed interface AccountCheck {
        data class Valid(val publicKey: ByteArray, val label: String?) : AccountCheck
        data class Invalid(val code: ConnectFailureCode) : AccountCheck
    }

    /**
     * Uses the first account the wallet returned, as MWA 2.0.3 itself does (AuthorizationResult.publicKey
     * is accounts[0]). No address is compared against a hard-coded value.
     */
    fun validateAccounts(accounts: List<AccountCandidate>?): AccountCheck {
        if (accounts.isNullOrEmpty()) return AccountCheck.Invalid(ConnectFailureCode.INVALID_AUTHORIZATION_RESPONSE)
        val first = accounts.first()
        val key = first.publicKey ?: return AccountCheck.Invalid(ConnectFailureCode.ACCOUNT_VALIDATION_FAILED)
        if (key.size != ED25519_PUBLIC_KEY_BYTES || key.all { it == 0.toByte() }) {
            return AccountCheck.Invalid(ConnectFailureCode.ACCOUNT_VALIDATION_FAILED)
        }
        return AccountCheck.Valid(key.copyOf(), first.label)
    }

    /**
     * Maps a non-success [TransactionResult] to a category. clientlib-ktx 2.0.3 wraps an authorize
     * failure as Failure("Failed establishing local association with wallet", ExecutionException(cause)),
     * so the cause is inspected rather than the SDK message text.
     * [sentAuthToken] distinguishes a first authorize (JSON-RPC -1 = declined) from a reauthorize
     * (-1 = token rejected); the SDK reports both as ERROR_AUTHORIZATION_FAILED.
     */
    fun classify(result: TransactionResult<*>, sentAuthToken: Boolean, now: Instant = Instant.now()): ConnectFailure? = when (result) {
        is TransactionResult.Success -> null
        is TransactionResult.NoWalletFound -> ConnectFailure(ConnectFailureCode.NO_COMPATIBLE_WALLET, now.toString(), null)
        is TransactionResult.Failure -> classifyThrowable(result.e, sentAuthToken, now)
    }

    /** Maps an exception (returned inside a Failure, or thrown out of the SDK call) to a category. */
    fun classifyThrowable(e: Throwable, sentAuthToken: Boolean, now: Instant = Instant.now()): ConnectFailure {
        val cause = if (e is ExecutionException) e.cause ?: e else e
        val code = when (cause) {
            is JsonRpc20Client.JsonRpc20RemoteException -> when (cause.code) {
                ERROR_AUTHORIZATION_FAILED ->
                    if (sentAuthToken) ConnectFailureCode.AUTH_TOKEN_REJECTED else ConnectFailureCode.WALLET_DECLINED
                ERROR_CLUSTER_NOT_SUPPORTED -> ConnectFailureCode.CHAIN_NOT_SUPPORTED
                else -> ConnectFailureCode.WALLET_ERROR
            }
            is MobileWalletAdapterClient.InsecureWalletEndpointUriException -> ConnectFailureCode.INVALID_AUTHORIZATION_RESPONSE
            is JsonRpc20Client.JsonRpc20Exception -> ConnectFailureCode.INVALID_AUTHORIZATION_RESPONSE
            // Order matters: kotlinx TimeoutCancellationException (intent not sent in time) is a CancellationException.
            is kotlinx.coroutines.TimeoutCancellationException -> ConnectFailureCode.ASSOCIATION_FAILED
            is TimeoutException -> ConnectFailureCode.SESSION_TIMEOUT
            is InterruptedException -> ConnectFailureCode.SESSION_INTERRUPTED
            is java.util.concurrent.CancellationException -> ConnectFailureCode.SESSION_INTERRUPTED
            is IOException -> ConnectFailureCode.SESSION_IO
            is ExecutionException -> ConnectFailureCode.ASSOCIATION_FAILED
            is IllegalStateException -> ConnectFailureCode.ASSOCIATION_FAILED
            // The SDK reports an ExecutionException it cannot attribute as a failed local association.
            else -> if (e is ExecutionException) ConnectFailureCode.ASSOCIATION_FAILED else ConnectFailureCode.INTERNAL_ERROR
        }
        return ConnectFailure(
            code = code,
            atUtc = now.toString(),
            exceptionType = e.javaClass.name,
            causeType = if (cause !== e) cause.javaClass.name else null,
            rpcCode = (cause as? JsonRpc20Client.JsonRpc20RemoteException)?.code,
        )
    }
}
