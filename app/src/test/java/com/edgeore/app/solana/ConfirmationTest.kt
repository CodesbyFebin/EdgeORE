package com.edgeore.app.solana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the owner sees after the single submit, from durable operation state only. */
class ConfirmationTest {
    private val sig = "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQUW"

    private fun op(
        state: OpState,
        obs: String? = null,
        cluster: String = Cluster.MAINNET.id,
    ) = PendingOperation(
        id = "op1",
        cluster = cluster,
        signer = "S",
        recipient = "R",
        lamports = 10_000_000,
        messageSha256 = "00",
        messageBase64 = "",
        blockhash = "B",
        lastValidBlockHeight = 100,
        budgetDay = "2026-10-13",
        reviewedAt = "t",
        signature = sig,
        state = state,
        lastObservation = obs,
    )

    @Test fun confirmedAndFinalizedAreConfirmed() {
        assertEquals(ConfirmationView.Confirmed(false), Confirmation.view(op(OpState.CONFIRMED)))
        assertEquals(ConfirmationView.Confirmed(true), Confirmation.view(op(OpState.FINALIZED)))
        assertEquals("Confirmed", Confirmation.label(Confirmation.view(op(OpState.CONFIRMED))))
        assertTrue(Confirmation.done(op(OpState.CONFIRMED)))
    }

    @Test fun submittedOrProcessedKeepsWaitingAndPolling() {
        val p = op(OpState.SUBMITTED, "PROCESSED (not yet confirmed) · block height 90, last valid 100")
        assertEquals(ConfirmationView.Waiting, Confirmation.view(p))
        assertFalse(Confirmation.done(p))
        assertFalse(Confirmation.done(op(OpState.OUTCOME_UNKNOWN, "Status unavailable: timeout")))
    }

    @Test fun expiredBlockhashOrSpentBudgetIsNotConfirmed() {
        val e = op(OpState.OUTCOME_UNKNOWN, "NOT_FOUND at block height 101 > last valid 100")
        assertTrue(Confirmation.done(e))
        assertEquals(ConfirmationView.NotConfirmed, Confirmation.view(e))
        assertEquals("Not confirmed: check Solscan before retrying", Confirmation.label(Confirmation.view(e)))
        assertEquals(ConfirmationView.NotConfirmed, Confirmation.view(op(OpState.SUBMITTED), Confirmation.MAX_POLLS))
    }

    @Test fun chainErrorIsFailed() {
        val v = Confirmation.view(op(OpState.FAILED, "FAILED: {\"InstructionError\":[0,{\"Custom\":1}]}"))
        assertTrue(v is ConfirmationView.Failed)
        assertTrue(Confirmation.done(op(OpState.FAILED)))
    }

    @Test fun solscanLinkFollowsTheOperationCluster() {
        assertEquals("https://solscan.io/tx/$sig", Confirmation.solscanUrl(sig, Cluster.MAINNET.id))
        assertEquals("https://solscan.io/tx/$sig?cluster=devnet", Confirmation.solscanUrl(sig, Cluster.DEVNET.id))
        assertThrows(IllegalArgumentException::class.java) { Confirmation.solscanUrl("bad/sig", Cluster.MAINNET.id) }
        assertThrows(IllegalArgumentException::class.java) { Confirmation.solscanUrl(sig, "localnet") }
    }

    @Test fun pollBudgetOutlivesABlockhash() {
        assertTrue(Confirmation.POLL_INTERVAL_MS * Confirmation.MAX_POLLS >= 150_000)
    }

    @Test fun sendNeverAsksTheRpcToRebroadcast() {
        val src = java.io.File("src/main/java/com/edgeore/app/solana/SolanaRpc.kt").readText()
        assertTrue(src.contains(".put(\"maxRetries\", 0)"))
    }
}
