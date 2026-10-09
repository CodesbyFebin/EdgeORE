package com.edgeore.app

import com.edgeore.app.io.AtomicFiles
import com.edgeore.app.solana.ChainStatus
import com.edgeore.app.solana.OpState
import com.edgeore.app.solana.OperationStore
import com.edgeore.app.solana.OperationStoreUnavailable
import com.edgeore.app.solana.PendingOperation
import com.edgeore.app.solana.TransferCoordinator
import com.edgeore.app.solana.TransferCoordinator.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Persistence failure at every transition (memory == disk, latch, no send), strict status
 * handling in observe, and the block-height gate before the wallet opens.
 */
class StoreFailureAndSigningGateTest {
    @get:Rule val tmp = TemporaryFolder()
    private val fx = SignedFixture()
    private val limit = 50_000_000L
    private val clock = { Instant.parse("2026-10-09T10:00:00Z") }
    private fun file() = File(tmp.root, "operations/operations.json")

    /** Writer that fails once [failNext] is set. */
    private var failNext = false
    private var writes = 0
    private fun store() = OperationStore(file(), clock) { f, b ->
        if (failNext) throw IOException("disk full (injected)")
        writes++; AtomicFiles.write(f, b)
    }

    private fun reserved(s: OperationStore): PendingOperation =
        (s.reserve("devnet", fx.signer, fx.recipient, fx.lamports, fx.message, fx.messageSha256, "bh", 200, limit) as OperationStore.ReserveResult.Reserved).op

    private fun signed(s: OperationStore, c: TransferCoordinator): PendingOperation {
        val op = reserved(s)
        assertTrue(c.beginSigning(op.id) is Outcome.Done)
        return c.recordSigned(op.id, fx.signature, fx.signedTx)!!
    }

    /** Memory equals what a restarted process would read, and the store refuses further financial writes. */
    private fun assertMemoryEqualsDiskAndLatched(s: OperationStore) {
        assertEquals(OperationStore(file(), clock).all(), s.all())
        assertNotNull(s.unavailableReason)
        assertTrue(s.unavailableReason!!.contains("storage write failed"))
        val again = s.reserve("devnet", fx.signer, fx.recipient, 1, fx.message, fx.messageSha256, "bh", 200, limit)
        assertTrue(again is OperationStore.ReserveResult.Refused)
        assertTrue((again as OperationStore.ReserveResult.Refused).reason.contains("storage write failed"))
    }

    @Test fun reserveWriteFailureRefusesAndKeepsMemoryEqualToDisk() {
        val s = store()
        val before = reserved(s)              // a REVIEWED draft that the next reserve would abandon
        failNext = true
        val r = s.reserve("devnet", fx.signer, fx.recipient, fx.lamports, fx.message, fx.messageSha256, "bh2", 200, limit)
        assertTrue(r is OperationStore.ReserveResult.Refused)
        assertEquals("abandonment was not published", OpState.REVIEWED, s.get(before.id)!!.state)
        assertEquals(1, s.all().size)
        assertMemoryEqualsDiskAndLatched(s)
    }

    @Test fun refusedReserveThatAbandonsDraftsAlsoRollsBackOnWriteFailure() {
        val s = store()
        val before = reserved(s)
        failNext = true
        val r = s.reserve("devnet", fx.signer, fx.recipient, limit, fx.message, fx.messageSha256, "bh2", 200, limit)
        assertTrue(r is OperationStore.ReserveResult.Refused)
        assertEquals(OpState.REVIEWED, s.get(before.id)!!.state)
        assertMemoryEqualsDiskAndLatched(s)
    }

    @Test fun beginSigningWriteFailureRefusesWithoutOpeningAnything() = runBlocking {
        val gw = FakeGateway(); val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = reserved(s)
        failNext = true
        val out = c.beginSigningChecked(op.id)
        assertTrue(out is Outcome.NotAllowed)
        assertEquals(OpState.REVIEWED, s.get(op.id)!!.state)
        assertTrue("no network call after a failed write", gw.calls.isEmpty())
        assertMemoryEqualsDiskAndLatched(s)
    }

    @Test fun recordSignedWriteFailureKeepsSigningOnDiskAndInMemory() {
        val s = store(); val c = TransferCoordinator(s, FakeGateway(), clock)
        val op = reserved(s); assertTrue(c.beginSigning(op.id) is Outcome.Done)
        failNext = true
        assertNull(c.recordSigned(op.id, fx.signature, fx.signedTx))
        assertEquals(OpState.SIGNING, s.get(op.id)!!.state)
        assertNull(s.get(op.id)!!.signature)
        assertMemoryEqualsDiskAndLatched(s)
    }

    @Test fun recordRefusedAndDiscardWriteFailuresKeepState() {
        val s = store(); val c = TransferCoordinator(s, FakeGateway(), clock)
        val op = signed(s, c)
        failNext = true
        assertNull(c.discardSigned(op.id))
        assertEquals(OpState.SIGNED, s.get(op.id)!!.state)
        assertNull(c.recordRefused(op.id, "x"))
        assertMemoryEqualsDiskAndLatched(s)
    }

    @Test fun submitAttemptWriteFailureNeverSends() = runBlocking {
        val gw = FakeGateway(); val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c)
        failNext = true
        val out = c.submit(op.id)
        assertTrue(out is Outcome.NotAllowed)
        assertTrue((out as Outcome.NotAllowed).reason.contains("Not sent"))
        assertEquals("no send after failed attempt persistence", 0, gw.sendCount)
        assertEquals(OpState.SIGNED, s.get(op.id)!!.state)
        assertMemoryEqualsDiskAndLatched(s)
        // Latched: a second tap also refuses without sending.
        assertTrue(c.submit(op.id) is Outcome.NotAllowed)
        assertEquals(0, gw.sendCount)
    }

    @Test fun postSendWriteFailureLeavesSubmitAttemptedWhichRestartTreatsAsUnknown() = runBlocking {
        val gw = FakeGateway(); val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c)
        val inner = gw.sendBehavior
        gw.sendBehavior = { tx -> failNext = true; inner(tx) }   // the write AFTER the send fails
        val out = c.submit(op.id)
        assertTrue(out is Outcome.NotAllowed)
        assertTrue((out as Outcome.NotAllowed).reason.contains("Outcome unknown"))
        assertEquals(1, gw.sendCount)
        assertEquals(OpState.SUBMIT_ATTEMPTED, s.get(op.id)!!.state)
        assertMemoryEqualsDiskAndLatched(s)
        assertEquals("reservation still held", fx.lamports, s.exposure(fx.signer, "devnet"))
        // Restart with a healthy disk: recovery reads SUBMIT_ATTEMPTED and marks it unknown, never resends.
        failNext = false
        val s2 = store(); TransferCoordinator(s2, gw, clock).recoverAfterRestart()
        assertEquals(OpState.OUTCOME_UNKNOWN, s2.get(op.id)!!.state)
        assertEquals(1, gw.sendCount)
    }

    @Test fun observeWriteFailureKeepsStateAndLatches() = runBlocking {
        val gw = FakeGateway(); val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c); c.submit(op.id)
        gw.statusBehavior = { ChainStatus.Finalized }
        failNext = true
        assertTrue(c.observe(op.id) is Outcome.NotAllowed)
        assertEquals(OpState.SUBMITTED, s.get(op.id)!!.state)
        assertMemoryEqualsDiskAndLatched(s)
    }

    @Test fun markReceiptedWriteFailureKeepsUnreceiptedAndLatches() {
        val s = store()
        val op = reserved(s)
        failNext = true
        try { s.markReceipted(op.id, OpState.REVIEWED); fail("expected OperationStoreUnavailable") } catch (_: OperationStoreUnavailable) {}
        assertNull(s.get(op.id)!!.receiptedState)
        assertMemoryEqualsDiskAndLatched(s)
    }

    @Test fun latchedStoreRefusesEvenAfterTheDiskRecovers() {
        val s = store(); val op = reserved(s)
        failNext = true
        try { s.markReceipted(op.id, OpState.REVIEWED) } catch (_: OperationStoreUnavailable) {}
        failNext = false
        val w = writes
        try { s.transition(op.id, setOf(OpState.REVIEWED), OpState.ABANDONED); fail("expected latch") } catch (_: OperationStoreUnavailable) {}
        assertEquals("no write attempted while latched", w, writes)
    }

    // ---------- strict status in observe ----------
    @Test fun unavailableStatusNeverExpiresOrReleases() = runBlocking {
        val gw = FakeGateway(); val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c); c.submit(op.id)
        gw.height = 10_000   // far past lastValid
        gw.statusBehavior = { ChainStatus.Unavailable("Malformed status response: expected 1 status, got 0") }
        c.observe(op.id)
        val after = s.get(op.id)!!
        assertEquals(OpState.SUBMITTED, after.state)
        assertTrue(after.lastObservation!!.contains("Malformed"))
        assertEquals(fx.lamports, s.exposure(fx.signer, "devnet"))
    }

    // ---------- block-height gate before the wallet opens ----------
    @Test fun unavailableHeightRefusesBeforeWallet() = runBlocking {
        val gw = FakeGateway(); gw.heightFails = true
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = reserved(s)
        val out = c.beginSigningChecked(op.id)
        assertTrue(out is Outcome.NotAllowed)
        assertTrue((out as Outcome.NotAllowed).reason.contains("Block height unavailable"))
        val after = s.get(op.id)!!
        assertEquals(OpState.REFUSED, after.state)
        assertEquals("message never mutated", op.messageBase64, after.messageBase64)
        assertEquals(0L, s.exposure(fx.signer, "devnet"))
    }

    @Test fun expiredOrNearlyExpiredHeightNeedsFreshReview() = runBlocking {
        for (h in listOf(201L, 200L, 200L - TransferCoordinator.SIGNING_MARGIN_BLOCKS + 1)) {
            val gw = FakeGateway(); gw.height = h
            File(tmp.root, "operations").deleteRecursively()
            val s = store(); val c = TransferCoordinator(s, gw, clock)
            val op = reserved(s)
            val out = c.beginSigningChecked(op.id)
            assertTrue("height $h", out is Outcome.NotAllowed)
            assertTrue((out as Outcome.NotAllowed).reason.contains("Review again"))
            val after = s.get(op.id)!!
            assertEquals(OpState.EXPIRED, after.state)
            assertEquals(op.messageBase64, after.messageBase64)
            assertEquals(op.messageSha256, after.messageSha256)
            assertEquals(op.blockhash, after.blockhash)
            assertNull(after.signature)
            assertEquals(0, gw.sendCount)
        }
    }

    @Test fun validHeightProceedsToSigning() = runBlocking {
        val gw = FakeGateway(); gw.height = 200 - TransferCoordinator.SIGNING_MARGIN_BLOCKS
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = reserved(s)
        assertTrue(c.beginSigningChecked(op.id) is Outcome.Done)
        assertEquals(OpState.SIGNING, s.get(op.id)!!.state)
    }

    @Test fun concurrentTapsOpenOneSigningFlightAndReadHeightOnce() = runBlocking {
        val gw = FakeGateway()
        val gate = CompletableDeferred<Unit>()
        val s = store()
        val slow = object : com.edgeore.app.solana.ChainGateway by gw {
            override suspend fun blockHeight(): Long { gate.await(); return gw.blockHeight() }
        }
        val c = TransferCoordinator(s, slow, clock)
        val op = reserved(s)
        val taps = (1..8).map { async(Dispatchers.Default) { c.beginSigningChecked(op.id) } }
        kotlinx.coroutines.delay(200); gate.complete(Unit)
        val outs = taps.awaitAll()
        assertEquals(1, outs.count { it is Outcome.Done })
        assertTrue(outs.filter { it !is Outcome.Done }.all { it is Outcome.Busy })
        assertEquals(1, gw.calls.count { it == "height" })
        assertEquals(OpState.SIGNING, s.get(op.id)!!.state)
    }
}
