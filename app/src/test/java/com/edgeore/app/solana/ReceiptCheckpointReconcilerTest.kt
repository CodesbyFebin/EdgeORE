package com.edgeore.app.solana

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptCheckpointReconcilerTest {
    private fun op(
        id: String,
        s: OpState,
    ) = PendingOperation(
        id = id,
        cluster = "devnet",
        signer = "s",
        recipient = "r",
        lamports = 1,
        messageSha256 = "00",
        messageBase64 = "",
        blockhash = "b",
        lastValidBlockHeight = 10,
        budgetDay = "2026-10-12",
        reviewedAt = "t",
        state = s,
    )

    private class Ledger(
        vararg ops: PendingOperation,
    ) {
        val map = linkedMapOf(*ops.map { it.id to it }.toTypedArray())
        val polled = mutableListOf<String>()
        val receipts = mutableListOf<Pair<OpState, OpState>>()
        var chain: (PendingOperation) -> OpState = { it.state }

        fun reconciler() =
            ReceiptCheckpointReconciler(
                { map.values.toList() },
                { id ->
                    polled += id
                    map[id] = map[id]!!.copy(state = chain(map[id]!!))
                },
                { map[it] },
                { b, a ->
                    receipts +=
                        b.state to a.state
                },
            )
    }

    @Test fun unknownBlocksWithoutUserRepollAndNothingIsPolledOrWritten() =
        runBlocking {
            val l = Ledger(op("a", OpState.OUTCOME_UNKNOWN), op("b", OpState.FINALIZED))
            val r = l.reconciler().prepare(userRequestedRepoll = false)
            assertTrue(r is ReceiptCheckpointReconciler.Result.Blocked)
            assertEquals(listOf("a"), (r as ReceiptCheckpointReconciler.Result.Blocked).uncertainIds)
            assertTrue(l.polled.isEmpty() && l.receipts.isEmpty())
            assertEquals(OpState.OUTCOME_UNKNOWN, l.map["a"]!!.state)
        }

    @Test fun repollThatStillFindsNothingKeepsUnknownAndBlocks() =
        runBlocking {
            val l = Ledger(op("a", OpState.OUTCOME_UNKNOWN))
            val r = l.reconciler().prepare(userRequestedRepoll = true)
            assertTrue(r is ReceiptCheckpointReconciler.Result.Blocked)
            assertEquals(listOf("a"), l.polled)
            assertTrue(l.receipts.isEmpty())
            assertEquals(OpState.OUTCOME_UNKNOWN, l.map["a"]!!.state)
        }

    @Test fun repollThatObservesLandingUpdatesLedgerThenEmitsReceiptThenReady() =
        runBlocking {
            val l = Ledger(op("a", OpState.OUTCOME_UNKNOWN), op("b", OpState.SUBMITTED))
            l.chain = { if (it.id == "a") OpState.FINALIZED else OpState.FAILED }
            assertEquals(ReceiptCheckpointReconciler.Result.Ready, l.reconciler().prepare(userRequestedRepoll = true))
            val expected = listOf(OpState.OUTCOME_UNKNOWN to OpState.FINALIZED, OpState.SUBMITTED to OpState.FAILED)
            assertEquals(expected, l.receipts)
        }

    @Test fun nothingUncertainIsReadyWithoutPolling() =
        runBlocking {
            val l = Ledger(op("a", OpState.FINALIZED), op("b", OpState.EXPIRED))
            assertEquals(ReceiptCheckpointReconciler.Result.Ready, l.reconciler().prepare(false))
            assertTrue(l.polled.isEmpty())
        }
}
