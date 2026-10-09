package com.edgeore.app

import com.edgeore.app.solana.ChainStatus
import com.edgeore.app.solana.OpState
import com.edgeore.app.solana.OperationStore
import com.edgeore.app.solana.OperationStoreCorrupt
import com.edgeore.app.solana.PendingOperation
import com.edgeore.app.solana.TransferCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * P0 acceptance: durable operation state, single flight, unknown-outcome recovery.
 * "Process death" is simulated by discarding the store/coordinator objects and constructing new
 * ones from the bytes on disk, exactly as a restarted app process would.
 */
class DurableOperationTest {
    @get:Rule val tmp = TemporaryFolder()
    private val fx = SignedFixture()
    private val limit = 50_000_000L
    private var now = Instant.parse("2026-10-09T10:00:00Z")
    private val clock = { now }

    private fun file() = File(tmp.root, "operations/operations.json")
    private fun store() = OperationStore(file(), clock)

    private fun reserved(store: OperationStore, f: SignedFixture = fx, lastValid: Long = 200): PendingOperation =
        (store.reserve("devnet", f.signer, f.recipient, f.lamports, f.message, f.messageSha256, "bh", lastValid, limit) as OperationStore.ReserveResult.Reserved).op

    /** REVIEWED -> SIGNING -> SIGNED with real wallet bytes. */
    private fun signed(store: OperationStore, c: TransferCoordinator, f: SignedFixture = fx, lastValid: Long = 200): PendingOperation {
        val op = reserved(store, f, lastValid)
        assertTrue(c.beginSigning(op.id) is TransferCoordinator.Outcome.Done)
        return c.recordSigned(op.id, f.signature, f.signedTx)!!
    }

    @Test fun reviewedOperationRecordsEveryRequiredField() {
        val s = store()
        val op = reserved(s)
        val o = JSONObject(file().readText()).getJSONArray("operations").getJSONObject(0)
        for (k in listOf("id", "cluster", "signer", "recipient", "lamports", "messageSha256", "messageBase64", "blockhash", "lastValidBlockHeight", "reviewedAt", "state", "budgetDay", "schemaVersion"))
            assertTrue("missing $k", o.has(k) && !o.isNull(k))
        assertEquals(op.id, o.getString("id"))
        assertEquals(200L, o.getLong("lastValidBlockHeight"))
        assertEquals(fx.messageSha256, o.getString("messageSha256"))
    }

    @Test fun deathBeforeSendKeepsSignedBytesAndNeverSendsOnRestart() = runBlocking {
        val gw = FakeGateway()
        val s1 = store(); val c1 = TransferCoordinator(s1, gw, clock)
        val op = signed(s1, c1)
        // --- process dies here (after signing, before the user tapped Submit) ---
        val s2 = store(); val c2 = TransferCoordinator(s2, gw, clock)
        c2.recoverAfterRestart()
        val back = s2.get(op.id)!!
        assertEquals(OpState.SIGNED, back.state)
        assertEquals(fx.signature, back.signature)
        assertTrue(back.signedTransaction!!.contentEquals(fx.signedTx))
        c2.reconcileAll()
        assertEquals(0, gw.sendCount)
        assertEquals(fx.lamports, s2.exposure(fx.signer, "devnet"))
    }

    @Test fun submitAttemptIsDurableBeforeTheNetworkCall() = runBlocking {
        val gw = FakeGateway()
        val s1 = store(); val c1 = TransferCoordinator(s1, gw, clock)
        val op = signed(s1, c1)
        var onDiskDuringSend: String? = null
        gw.sendBehavior = { onDiskDuringSend = file().readText(); throw java.io.IOException("process killed mid-send") }
        c1.submit(op.id)
        // What a new process would load if the old one died during the send:
        val snapshot = File(tmp.root, "snap.json").apply { writeText(onDiskDuringSend!!) }
        val crashed = OperationStore(snapshot, clock)
        val atSend = crashed.get(op.id)!!
        assertEquals(OpState.SUBMIT_ATTEMPTED, atSend.state)
        assertEquals(fx.signature, atSend.signature)
        assertEquals(1, atSend.submitAttempts)
        assertNotNull(atSend.submitAttemptedAt)
        // Restart from that snapshot: conservative UNKNOWN, no resend, signature still observable.
        val gw2 = FakeGateway()
        TransferCoordinator(crashed, gw2, clock).recoverAfterRestart()
        assertEquals(OpState.OUTCOME_UNKNOWN, crashed.get(op.id)!!.state)
        assertEquals(0, gw2.sendCount)
        assertEquals(fx.lamports, crashed.exposure(fx.signer, "devnet"))
    }

    @Test fun deathAfterRpcAcceptanceBeforeReceiptLeavesUnreceiptedSubmitted() = runBlocking {
        val gw = FakeGateway()
        val s1 = store(); val c1 = TransferCoordinator(s1, gw, clock)
        val op = signed(s1, c1)
        s1.markReceipted(op.id, OpState.SIGNED)
        val out = c1.submit(op.id) as TransferCoordinator.Outcome.Done
        assertEquals(OpState.SUBMITTED, out.op.state)
        // --- dies before the SUBMITTED receipt is appended ---
        val s2 = store()
        TransferCoordinator(s2, gw, clock).recoverAfterRestart()
        assertEquals(OpState.SUBMITTED, s2.get(op.id)!!.state)
        assertEquals(listOf(op.id), s2.unreceipted().map { it.id })
        s2.markReceipted(op.id, OpState.SUBMITTED)
        assertTrue(store().unreceipted().isEmpty())
        assertEquals(1, gw.sendCount)
    }

    @Test fun timeoutIsUnknownAndIsNeverRetransmitted() = runBlocking {
        val gw = FakeGateway()
        gw.sendBehavior = { throw java.net.SocketTimeoutException("Read timed out") }
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c)
        val out = c.submit(op.id) as TransferCoordinator.Outcome.Done
        assertEquals(OpState.OUTCOME_UNKNOWN, out.op.state)
        assertTrue(out.op.reason!!.contains("timed out"))
        assertEquals(fx.signature, out.op.signature)
        // A second tap cannot resend unknown bytes.
        assertTrue(c.submit(op.id) is TransferCoordinator.Outcome.NotAllowed)
        assertEquals(1, gw.sendCount)
        assertEquals(fx.lamports, s.exposure(fx.signer, "devnet"))
    }

    @Test fun rpcSignatureMismatchIsUnknownNotSuccess() = runBlocking {
        val gw = FakeGateway(); gw.sendBehavior = { "1111111111111111111111111111111111111111111111111111111111111111" }
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c)
        assertEquals(OpState.OUTCOME_UNKNOWN, (c.submit(op.id) as TransferCoordinator.Outcome.Done).op.state)
        assertEquals(fx.signature, s.get(op.id)!!.signature)
    }

    @Test fun cancellationDuringSendIsUnknown() = runBlocking {
        val gw = FakeGateway(); gw.blockSends()
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c)
        val job = launch(Dispatchers.Default) { c.submit(op.id) }
        withTimeout(5_000) { while (gw.sendCount == 0) delay(5) }
        job.cancelAndJoin()
        assertEquals(OpState.OUTCOME_UNKNOWN, s.get(op.id)!!.state)
    }

    @Test fun repeatedSubmitTapsProduceExactlyOneSend() = runBlocking {
        val gw = FakeGateway(); val gate = gw.blockSends()
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c)
        val taps = (1..25).map { async(Dispatchers.Default) { c.submit(op.id) } }
        withTimeout(5_000) { while (gw.sendCount == 0) delay(5) }
        delay(50)
        gate.complete(Unit)
        val results = taps.awaitAll()
        assertEquals(1, gw.sendCount)
        assertEquals(1, results.count { it is TransferCoordinator.Outcome.Done })
        assertTrue(results.all { it is TransferCoordinator.Outcome.Done || it is TransferCoordinator.Outcome.Busy || it is TransferCoordinator.Outcome.NotAllowed })
        assertEquals(1, s.get(op.id)!!.submitAttempts)
        assertEquals(OpState.SUBMITTED, s.get(op.id)!!.state)
    }

    @Test fun repeatedApproveTapsOpenOneSigningSession() = runBlocking {
        val s = store(); val c = TransferCoordinator(s, FakeGateway(), clock)
        val op = reserved(s)
        val results = (1..25).map { async(Dispatchers.Default) { c.beginSigning(op.id) } }.awaitAll()
        assertEquals(1, results.count { it is TransferCoordinator.Outcome.Done })
        assertEquals(OpState.SIGNING, s.get(op.id)!!.state)
    }

    @Test fun restartObservesTheKnownSignatureWithoutResending() = runBlocking {
        val gw = FakeGateway(); gw.sendBehavior = { throw java.net.SocketTimeoutException("lost response") }
        val s1 = store(); val c1 = TransferCoordinator(s1, gw, clock)
        val op = signed(s1, c1)
        c1.submit(op.id)
        // --- restart ---
        val gw2 = FakeGateway(); gw2.statusBehavior = { ChainStatus.Finalized }
        val s2 = store(); val c2 = TransferCoordinator(s2, gw2, clock)
        c2.recoverAfterRestart()
        c2.reconcileAll()
        val after = s2.get(op.id)!!
        assertEquals(OpState.FINALIZED, after.state)
        assertTrue(gw2.calls.contains("status:${fx.signature}"))
        assertEquals(0, gw2.sendCount)
        // Finalized is spent: counted exactly once.
        assertEquals(fx.lamports, s2.exposure(fx.signer, "devnet"))
    }

    @Test fun confirmedThenFinalizedCountsOnce() = runBlocking {
        val gw = FakeGateway()
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c); c.submit(op.id)
        gw.statusBehavior = { ChainStatus.Confirmed }; c.observe(op.id)
        assertEquals(OpState.CONFIRMED, s.get(op.id)!!.state)
        assertEquals(fx.lamports, s.exposure(fx.signer, "devnet"))
        gw.statusBehavior = { ChainStatus.Finalized }; c.observe(op.id)
        assertEquals(OpState.FINALIZED, s.get(op.id)!!.state)
        assertEquals(fx.lamports, s.exposure(fx.signer, "devnet"))
    }

    @Test fun notFoundBeforeExpiryStaysUnknownAfterExpiryNeedsFreshReview() = runBlocking {
        val gw = FakeGateway(); gw.sendBehavior = { throw java.io.IOException("reset") }
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c, lastValid = 150); c.submit(op.id)
        gw.height = 150
        c.observe(op.id)
        assertEquals(OpState.OUTCOME_UNKNOWN, s.get(op.id)!!.state)
        assertTrue(s.get(op.id)!!.lastObservation!!.startsWith("NOT_FOUND"))
        gw.height = 151
        gw.calls.clear()
        c.observe(op.id)
        val expired = s.get(op.id)!!
        assertEquals(OpState.EXPIRED, expired.state)
        assertTrue(expired.reason!!.contains("Review again"))
        // Height is read before status, so NOT_FOUND after a past-expiry height is final.
        assertEquals(listOf("height", "status:${fx.signature}"), gw.calls.toList())
        assertEquals(0L, s.exposure(fx.signer, "devnet"))
        // Expired operations cannot be submitted again; a new message needs a new review.
        assertTrue(c.submit(op.id) is TransferCoordinator.Outcome.NotAllowed)
        assertEquals("no send after the first attempt", 0, gw.sendCount)
    }

    @Test fun unavailableHeightNeverExpires() = runBlocking {
        val gw = FakeGateway(); gw.sendBehavior = { throw java.io.IOException("reset") }; gw.heightFails = true
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c, lastValid = 1); c.submit(op.id)
        c.observe(op.id)
        assertEquals(OpState.OUTCOME_UNKNOWN, s.get(op.id)!!.state)
    }

    @Test fun statusRpcFailureKeepsStateAndRecordsTheAttempt() = runBlocking {
        val gw = FakeGateway(); gw.statusBehavior = { throw java.io.IOException("RPC down") }
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c); c.submit(op.id)
        c.observe(op.id)
        val after = s.get(op.id)!!
        assertEquals(OpState.SUBMITTED, after.state)
        assertTrue(after.lastObservation!!.contains("unavailable"))
    }

    @Test fun chainErrorIsFailedAndReleasesPrincipal() = runBlocking {
        val gw = FakeGateway(); gw.statusBehavior = { ChainStatus.Failed("{\"InstructionError\":[0,\"Custom\"]}") }
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c); c.submit(op.id); c.observe(op.id)
        assertEquals(OpState.FAILED, s.get(op.id)!!.state)
        assertTrue(s.get(op.id)!!.reason!!.contains("fee"))
        assertEquals(0L, s.exposure(fx.signer, "devnet"))
    }

    @Test fun signedButUnsentExpiresWithoutAnySend() = runBlocking {
        val gw = FakeGateway(); gw.height = 500
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c, lastValid = 400)
        c.reconcileAll()
        assertEquals(OpState.EXPIRED, s.get(op.id)!!.state)
        assertEquals(0, gw.sendCount)
    }

    @Test fun restartAbandonsUnsignedAndInterruptedSigningSessions() {
        val s1 = store(); val c1 = TransferCoordinator(s1, FakeGateway(), clock)
        val a = reserved(s1, SignedFixture(seed = 1))
        val b = reserved(s1, SignedFixture(seed = 2)); c1.beginSigning(b.id)
        val s2 = store(); TransferCoordinator(s2, FakeGateway(), clock).recoverAfterRestart()
        assertEquals(OpState.ABANDONED, s2.get(a.id)!!.state)
        assertEquals(OpState.ABANDONED, s2.get(b.id)!!.state)
        assertNull(s2.get(b.id)!!.signature)
    }

    @Test fun damagedStoreFailsClosedInsteadOfLookingEmpty() {
        file().parentFile!!.mkdirs(); file().writeText("{\"schema\":1,\"operations\":[{\"id\":")
        assertTrue(runCatching { store() }.exceptionOrNull() is OperationStoreCorrupt)
    }

    @Test fun walletRefusalAndRejectedBytesReleaseTheReservation() {
        val s = store(); val c = TransferCoordinator(s, FakeGateway(), clock)
        val op = reserved(s); c.beginSigning(op.id)
        c.recordRefused(op.id, "Wallet bytes rejected: different message")
        assertEquals(OpState.REFUSED, s.get(op.id)!!.state)
        assertEquals(0L, s.exposure(fx.signer, "devnet"))
        assertNull(c.recordSigned(op.id, fx.signature, fx.signedTx)) // a late wallet return cannot revive it
    }

    @Test fun refusedOrInvalidTransitionsAreRejected() = runBlocking {
        val s = store(); val c = TransferCoordinator(s, FakeGateway(), clock)
        val op = reserved(s)
        assertTrue(c.submit(op.id) is TransferCoordinator.Outcome.NotAllowed) // not signed yet
        assertNull(c.recordSigned(op.id, fx.signature, fx.signedTx))      // signing never began
        assertTrue(c.submit("missing") is TransferCoordinator.Outcome.NotAllowed)
    }

    @Test fun cancellationExceptionPropagates() = runBlocking {
        val gw = FakeGateway(); gw.sendBehavior = { throw CancellationException("scope ended") }
        val s = store(); val c = TransferCoordinator(s, gw, clock)
        val op = signed(s, c)
        assertTrue(runCatching { c.submit(op.id) }.exceptionOrNull() is CancellationException)
        assertEquals(OpState.OUTCOME_UNKNOWN, s.get(op.id)!!.state)
    }
}
