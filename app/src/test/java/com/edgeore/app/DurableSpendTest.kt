package com.edgeore.app

import com.edgeore.app.solana.OpState
import com.edgeore.app.solana.OperationStore
import com.edgeore.app.solana.TransferCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * P1 durable spend reservations (replaces the feature branch's in-memory SpendLedger).
 * Policy: per signer and cluster, per UTC calendar day of review; each operation counted once.
 */
class DurableSpendTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = Instant.parse("2026-10-09T10:00:00Z")
    private val file get() = File(tmp.root, "ops.json")
    private fun store() = OperationStore(file, { now })

    private fun reserve(s: OperationStore, signer: String, lamports: Long, limit: Long = 100, cluster: String = "devnet", external: Long = 0) =
        s.reserve(cluster, signer, "to", lamports, ByteArray(4) { lamports.toByte() }, "m$lamports", "bh", 10, limit, external)

    private fun id(r: OperationStore.ReserveResult) = (r as OperationStore.ReserveResult.Reserved).op.id

    @Test fun exposureSurvivesProcessDeath() {
        val s = store()
        id(reserve(s, "A", 60))
        assertEquals(60L, store().exposure("A", "devnet"))
        assertTrue(reserve(store(), "B", 50).let { it is OperationStore.ReserveResult.Reserved })
    }

    @Test fun secondDraftOverLimitRefusedButReplacesUnsignedFirst() {
        val s = store()
        val a = id(reserve(s, "A", 60))
        val c = TransferCoordinator(s, FakeGateway(), { now })
        c.beginSigning(a)                                  // A is now SIGNING: still held
        assertTrue(reserve(s, "A", 50) is OperationStore.ReserveResult.Refused)
        assertEquals(60L, s.exposure("A", "devnet"))
    }

    @Test fun newReviewAbandonsTheOldUnsignedDraft() {
        val s = store()
        val first = id(reserve(s, "A", 60))
        val second = id(reserve(s, "A", 70))               // allowed: the unsigned 60 is abandoned, not double counted
        assertEquals(OpState.ABANDONED, s.get(first)!!.state)
        assertEquals(OpState.REVIEWED, s.get(second)!!.state)
        assertEquals(70L, s.exposure("A", "devnet"))
    }

    @Test fun accountsAndClustersArePartitioned() {
        val s = store()
        id(reserve(s, "A", 90))
        assertTrue(reserve(s, "B", 90) is OperationStore.ReserveResult.Reserved)
        assertTrue(reserve(s, "A", 90, cluster = "testnet") is OperationStore.ReserveResult.Reserved)
        assertEquals(90L, s.exposure("A", "devnet"))
        assertEquals(90L, s.exposure("B", "devnet"))
    }

    @Test fun dayRolloverStartsAFreshUtcDay() {
        val s = store()
        val a = id(reserve(s, "A", 90))
        TransferCoordinator(s, FakeGateway(), { now }).beginSigning(a)
        now = Instant.parse("2026-10-10T00:00:01Z")
        assertEquals(0L, s.exposure("A", "devnet"))
        assertTrue(reserve(s, "A", 90) is OperationStore.ReserveResult.Reserved)
    }

    @Test fun legacyReceiptSpendIsCountedOnce() {
        val s = store()
        assertTrue(reserve(s, "A", 60, external = 50) is OperationStore.ReserveResult.Refused)
        assertTrue(reserve(s, "A", 50, external = 50) is OperationStore.ReserveResult.Reserved)
    }

    @Test fun overflowAndInvalidInputsRefused() {
        val s = store()
        assertTrue(s.reserve("devnet", "A", "to", Long.MAX_VALUE, ByteArray(1), "m", "bh", 1, Long.MAX_VALUE, externalLamports = 1) is OperationStore.ReserveResult.Refused)
        assertTrue(reserve(s, "A", 0) is OperationStore.ReserveResult.Refused)
        assertTrue(reserve(s, "A", 10, limit = -1) is OperationStore.ReserveResult.Refused)
        assertTrue(reserve(s, "A", 10, external = -5) is OperationStore.ReserveResult.Refused)
    }

    @Test fun concurrentReservationsNeverExceedTheLimit() = runBlocking {
        val s = store()
        // 40 different signers' worth of threads racing on one signer: the unsigned-draft replacement
        // rule means at most one REVIEWED draft remains, and exposure can never exceed the limit.
        val results = (1..40).map { i -> async(Dispatchers.Default) { reserve(s, "A", 30 + (i % 3).toLong()) } }.awaitAll()
        assertTrue(results.any { it is OperationStore.ReserveResult.Reserved })
        assertTrue(s.exposure("A", "devnet") <= 100)
        assertEquals(1, s.all().count { it.state == OpState.REVIEWED })
        assertEquals(s.all().size, OperationStore(file, { now }).all().size) // all persisted
    }
}
