package com.edgeore.app

import com.edgeore.app.crypto.Base58
import com.edgeore.app.solana.RpcObservation
import com.edgeore.app.wallet.AccountCandidate
import com.edgeore.app.wallet.ConnectFailure
import com.edgeore.app.wallet.ConnectFailureCode
import com.edgeore.app.wallet.ConnectStage
import com.edgeore.app.wallet.WalletAuthorization
import com.edgeore.app.wallet.WalletConnection
import com.edgeore.app.wallet.WalletCoordinator.ConnectResult
import com.edgeore.app.wallet.WalletDisplay
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/**
 * JVM regression tests for wallet-authorization handling. The wallet is a test double (a lambda
 * returning a ConnectResult, or SDK result/exception objects). These do not exercise a real wallet,
 * Android activity results or the MWA transport, and are not evidence that the device flow works.
 */
class WalletAuthorizationTest {
    private val t0: Instant = Instant.parse("2026-10-10T03:00:00Z")
    private val key = ByteArray(32) { (it + 1).toByte() }
    private fun conn(logs: MutableList<String> = mutableListOf()) = WalletConnection(log = { logs += it }, now = { t0 })
    private fun failure(code: ConnectFailureCode) = ConnectFailure(code, t0.toString(), "test.Double")

    // ---- valid authorization reaches connected state ----
    @Test fun validAuthorizationReachesConnectedStateWithReturnedAccount() = runTest {
        val c = conn()
        assertTrue(c.connect { ConnectResult.Authorized(key, "Mock", 1) })
        val s = c.state.value
        assertArrayEquals(key, s.publicKey)
        assertEquals(Base58.encode(key), s.address)
        assertEquals("Authorized on devnet", WalletDisplay.statusLine(s))
        assertNull(s.error); assertFalse(s.busy)
    }

    @Test fun accountValidationUsesReturnedAccountNotAHardCodedAddress() {
        val other = ByteArray(32) { 7 }
        val v = WalletAuthorization.validateAccounts(listOf(AccountCandidate(other, null), AccountCandidate(key, null)))
        assertTrue(v is WalletAuthorization.AccountCheck.Valid)
        assertArrayEquals(other, (v as WalletAuthorization.AccountCheck.Valid).publicKey)
    }

    // ---- invalid / empty response produces a visible error ----
    @Test fun emptyOrMalformedAccountsAreVisibleErrors() {
        assertEquals(ConnectFailureCode.INVALID_AUTHORIZATION_RESPONSE,
            (WalletAuthorization.validateAccounts(emptyList()) as WalletAuthorization.AccountCheck.Invalid).code)
        assertEquals(ConnectFailureCode.INVALID_AUTHORIZATION_RESPONSE,
            (WalletAuthorization.validateAccounts(null) as WalletAuthorization.AccountCheck.Invalid).code)
        for (bad in listOf(null, ByteArray(31), ByteArray(33), ByteArray(32))) {
            val r = WalletAuthorization.validateAccounts(listOf(AccountCandidate(bad, "x")))
            assertEquals(ConnectFailureCode.ACCOUNT_VALIDATION_FAILED, (r as WalletAuthorization.AccountCheck.Invalid).code)
        }
    }

    @Test fun failedConnectIsNeverShownAsNeutralNotConnected() = runTest {
        val c = conn()
        c.connect { ConnectResult.Failed(failure(ConnectFailureCode.INVALID_AUTHORIZATION_RESPONSE)) }
        val s = c.state.value
        assertNull(s.address)
        assertEquals("Connection failed", WalletDisplay.statusLine(s))
        assertNotEquals("Not connected", WalletDisplay.statusLine(s))
        val lines = WalletDisplay.errorLines(s.error!!, showDiagnostics = false)
        assertTrue(lines.any { "INVALID_AUTHORIZATION_RESPONSE" in it })
        assertTrue(lines.none { it.startsWith("Debug:") })
        assertTrue(WalletDisplay.errorLines(s.error!!, showDiagnostics = true).any { it.startsWith("Debug: test.Double") })
    }

    // ---- cancellation / failure does not report success ----
    @Test fun sdkResultsMapToDistinctCategoriesAndNeverSuccess() {
        fun f(e: Exception, token: Boolean = false) = WalletAuthorization.classify(TransactionResult.Failure<Unit>("m", e), token, t0)!!.code
        // clientlib-ktx 2.0.3 wraps authorize errors as ExecutionException(JsonRpc20RemoteException).
        val declined = ExecutionException(JsonRpc20Client.JsonRpc20RemoteException(-1, "authorization request failed", null))
        assertEquals(ConnectFailureCode.WALLET_DECLINED, f(declined))
        assertEquals(ConnectFailureCode.AUTH_TOKEN_REJECTED, f(declined, token = true))
        assertEquals(ConnectFailureCode.CHAIN_NOT_SUPPORTED, f(ExecutionException(JsonRpc20Client.JsonRpc20RemoteException(-7, "x", null))))
        assertEquals(ConnectFailureCode.WALLET_ERROR, f(ExecutionException(JsonRpc20Client.JsonRpc20RemoteException(-32603, "x", null))))
        assertEquals(ConnectFailureCode.INVALID_AUTHORIZATION_RESPONSE, f(ExecutionException(JsonRpc20Client.JsonRpc20InvalidResponseException("bad json"))))
        assertEquals(ConnectFailureCode.SESSION_TIMEOUT, f(TimeoutException()))
        assertEquals(ConnectFailureCode.SESSION_INTERRUPTED, f(InterruptedException()))
        assertEquals(ConnectFailureCode.SESSION_INTERRUPTED, f(java.util.concurrent.CancellationException()))
        assertEquals(ConnectFailureCode.SESSION_IO, f(ExecutionException(IOException())))
        assertEquals(ConnectFailureCode.ASSOCIATION_FAILED, f(ExecutionException(RuntimeException())))
        assertEquals(ConnectFailureCode.ASSOCIATION_FAILED, f(IllegalStateException("Received an activity start request while another is pending")))
        assertEquals(ConnectFailureCode.INTERNAL_ERROR, f(RuntimeException()))
        assertEquals(ConnectFailureCode.NO_COMPATIBLE_WALLET, WalletAuthorization.classify(TransactionResult.NoWalletFound<Unit>("none"), false, t0)!!.code)
        val d = WalletAuthorization.classifyThrowable(declined, false, t0)
        assertEquals(ConnectStage.AUTHORIZATION, d.stage); assertEquals(-1, d.rpcCode)
        assertEquals(ExecutionException::class.java.name, d.exceptionType)
        assertEquals(JsonRpc20Client.JsonRpc20RemoteException::class.java.name, d.causeType)
    }

    @Test fun thrownSdkExceptionBecomesVisibleFailureNotSuccess() = runTest {
        // MWA 2.0.3 can throw InterruptedException out of connect() when the wallet activity
        // returns RESULT_CANCELED before the session completes.
        val c = conn()
        c.connect { throw InterruptedException() }
        assertNull(c.state.value.address)
        assertEquals(ConnectFailureCode.SESSION_INTERRUPTED, c.state.value.error?.code)
        assertFalse(c.state.value.busy)
    }

    @Test fun declineThenRetryClearsErrorOnlyWhenNextAttemptStarts() = runTest {
        val c = conn()
        c.connect { ConnectResult.Failed(failure(ConnectFailureCode.WALLET_DECLINED)) }
        assertEquals(ConnectFailureCode.WALLET_DECLINED, c.state.value.error?.code)
        val gate = CompletableDeferred<ConnectResult>()
        val job = async { c.connect { gate.await() } }
        yield()
        assertTrue(c.state.value.busy); assertNull(c.state.value.error); assertNull(c.state.value.address)
        gate.complete(ConnectResult.Authorized(key, null))
        job.await()
        assertEquals(Base58.encode(key), c.state.value.address)
    }

    @Test fun callerCancellationPropagatesAndLeavesNoWaitingState() = runTest {
        val c = conn()
        try { c.connect { throw CancellationException("scope cleared") }; fail("expected cancellation") } catch (_: CancellationException) {}
        assertFalse(c.state.value.busy); assertNull(c.state.value.address)
    }

    @Test fun secondConnectWhileBusyIsRejectedWithoutCallingWallet() = runTest {
        val c = conn()
        val gate = CompletableDeferred<ConnectResult>()
        val first = async { c.connect { gate.await() } }
        yield()
        var called = false
        assertFalse(c.connect { called = true; ConnectResult.Authorized(key, null) })
        assertFalse(called)
        gate.complete(ConnectResult.Authorized(key, null)); first.await()
    }

    // ---- RPC failure leaves an authorized connection intact ----
    @Test fun balanceFailureShowsUnavailableAndKeepsAuthorization() = runTest {
        val c = conn()
        c.connect { ConnectResult.Authorized(key, null) }
        val line = WalletDisplay.balanceLine(RpcObservation.Unavailable("HTTP 503")) { "fresh" }
        assertEquals("Balance unavailable", line)
        assertFalse(line.contains("0"))
        assertEquals(Base58.encode(key), c.state.value.address)
        assertEquals("Authorized on devnet", WalletDisplay.statusLine(c.state.value))
    }

    // ---- returning from the wallet does not clear a successful state ----
    @Test fun lateResultFromAnOlderAttemptCannotOverwriteCurrentState() = runTest {
        val logs = mutableListOf<String>()
        val c = conn(logs)
        val stale = CompletableDeferred<ConnectResult>()
        val old = async { c.connect { stale.await() } }
        yield()
        c.disconnected(walletConfirmed = false) // user gave up on that attempt
        c.connect { ConnectResult.Authorized(key, null) }
        stale.complete(ConnectResult.Failed(failure(ConnectFailureCode.SESSION_INTERRUPTED)))
        old.await()
        assertEquals(Base58.encode(key), c.state.value.address)
        assertNull(c.state.value.error)
        assertTrue(logs.any { it.startsWith("wallet_connect_stale_result_discarded") })
    }

    @Test fun disconnectIsReportedSeparatelyFromConnect() = runTest {
        val c = conn()
        c.connect { ConnectResult.Authorized(key, null) }
        c.disconnected(walletConfirmed = false)
        assertNull(c.state.value.address)
        assertTrue(c.state.value.status.contains("did not confirm"))
    }

    // Appetize phase 4 showed a bare "Disconnected" that did not say whether the wallet confirmed it.
    @Test fun confirmedDisconnectSaysTheWalletConfirmed() = runTest {
        val c = conn()
        c.connect { ConnectResult.Authorized(key, null) }
        c.disconnected(walletConfirmed = true)
        assertNull(c.state.value.address)
        assertNull(c.state.value.error)
        assertEquals("Disconnected. The wallet confirmed deauthorization.", c.state.value.status)
        assertEquals(c.state.value.status, WalletDisplay.statusLine(c.state.value))
    }

    // ---- sanitized diagnostics ----
    @Test fun logLineCarriesCodeStageUtcAndTypeButNoWalletText() = runTest {
        val logs = mutableListOf<String>()
        val c = conn(logs)
        val secretish = "auth_token=SECRET-TOKEN payload=deadbeef"
        c.connect { throw ExecutionException(JsonRpc20Client.JsonRpc20RemoteException(-1, secretish, secretish)) }
        val line = logs.single { it.startsWith("wallet_connect_failed") }
        assertTrue(line.contains("code=WALLET_DECLINED")); assertTrue(line.contains("stage=AUTHORIZATION"))
        assertTrue(line.contains("at=2026-10-10T03:00:00Z")); assertTrue(line.contains("exception=java.util.concurrent.ExecutionException"))
        assertFalse(line.contains("SECRET")); assertFalse(line.contains("deadbeef"))
        val shown = WalletDisplay.errorLines(c.state.value.error!!, showDiagnostics = true).joinToString("\n")
        assertFalse(shown.contains("SECRET")); assertFalse(shown.contains("deadbeef"))
    }
}
