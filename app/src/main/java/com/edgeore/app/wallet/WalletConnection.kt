package com.edgeore.app.wallet

import com.edgeore.app.WalletState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * Owns the displayed wallet connection state for one process. A connect attempt publishes exactly
 * one outcome: the account the wallet returned, or a persistent [ConnectFailure]. Nothing outside an
 * attempt (activity resume, balance refresh, a late result from an older attempt) can replace it.
 * The authorized account is held in memory only; no auth token or key is persisted or logged.
 */
class WalletConnection(
    private val log: (String) -> Unit = {},
    private val now: () -> Instant = Instant::now,
) {
    private val _state = MutableStateFlow(WalletState())
    val state: StateFlow<WalletState> = _state.asStateFlow()
    private var attempt = 0L

    /** Runs one connect attempt. Returns false without calling [attemptBody] if one is already running. */
    suspend fun connect(attemptBody: suspend () -> WalletCoordinator.ConnectResult): Boolean {
        val id: Long
        synchronized(this) {
            if (_state.value.busy) return false
            id = ++attempt
            _state.update { it.copy(busy = true, status = "Waiting for wallet…", error = null) }
        }
        val result = try {
            attemptBody()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The caller's scope was cancelled (e.g. ViewModel cleared): leave no "Waiting…" behind.
            publish(id, WalletState(status = "Not connected"))
            throw e
        } catch (e: Exception) {
            WalletCoordinator.ConnectResult.Failed(WalletAuthorization.classifyThrowable(e, sentAuthToken = false, now = now()))
        }
        val next = when (result) {
            is WalletCoordinator.ConnectResult.Authorized -> {
                log("wallet_connect_authorized at=${now()} accounts_returned=${result.accountsReturned}")
                WalletState(result.publicKey, result.label, "Authorized on devnet")
            }
            is WalletCoordinator.ConnectResult.Failed -> {
                log(result.failure.logLine())
                WalletState(status = "Connection failed", error = result.failure)
            }
        }
        publish(id, next)
        return true
    }

    /** Clears the connection locally after a disconnect request; [walletConfirmed] reflects the SDK result. */
    fun disconnected(walletConfirmed: Boolean) {
        synchronized(this) {
            attempt++
            log("wallet_disconnect at=${now()} wallet_confirmed=$walletConfirmed")
            _state.value = WalletState(
                status = if (walletConfirmed) "Disconnected. The wallet confirmed deauthorization." else "Disconnected in EdgeORE. The wallet did not confirm deauthorization.",
            )
        }
    }

    private fun publish(id: Long, next: WalletState) = synchronized(this) {
        if (id == attempt) _state.value = next
        else log("wallet_connect_stale_result_discarded at=${now()}")
    }
}
