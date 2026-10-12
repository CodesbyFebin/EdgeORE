package com.edgeore.app

import com.edgeore.app.crypto.Base58
import com.edgeore.app.ore.OreV3
import com.edgeore.app.solana.OpState
import com.edgeore.app.solana.OperationStore
import com.edgeore.app.solana.OreDeployLimits
import com.edgeore.app.solana.OreDeployMessage
import com.edgeore.app.solana.OreDeployNetwork
import com.edgeore.app.solana.OreDeployReview
import com.edgeore.app.solana.SolanaMessage
import com.edgeore.app.solana.SolanaRpc
import com.edgeore.app.solana.TransferCoordinator
import com.edgeore.app.ui.screens.MAINNET_ACK_REQUIRED
import com.edgeore.app.ui.screens.addChip
import com.edgeore.app.ui.screens.deployBlocker
import com.edgeore.app.ore.OreDeployAvailability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The owner-approved mainnet ORE deploy: hard 0.01 SOL cap, explicit switch, and per-cluster routing. */
class OreMainnetCapTest {
    @get:Rule val tmp = TemporaryFolder()
    private val signer = ByteArray(32) { (it + 1).toByte() }
    private val bh = ByteArray(32) { 7 }
    private val all = OreDeployMessage.ALL_SQUARES_MASK
    private val rich = 10_000_000_000L
    private val mainnet = OreDeployNetwork.MAINNET_CLUSTER

    @Test fun capIsExactlyPointZeroOneSol() {
        assertEquals(10_000_000L, OreDeployLimits.MAINNET_CAP_LAMPORTS)
        assertEquals(OreDeployLimits.MAINNET_CAP_LAMPORTS, OreDeployLimits.cap(mainnet))
        assertEquals(OreDeployLimits.PER_DEPLOY_CAP_LAMPORTS, OreDeployLimits.cap("devnet"))
        assertEquals(10_000_000L, OreDeployLimits.max(rich, mainnet))
    }

    @Test fun invalidNegativeBalanceCannotOverflowIntoSpendableFunds() {
        for (balance in listOf(Long.MIN_VALUE, -1L, 0L, 9_999_999L)) {
            assertEquals(0L, OreDeployLimits.max(balance, mainnet))
            val draft = OreDeployReview.prepare(signer, 1, 1, 1, bh, balance, 0, rich, mainnet)
            assertFalse(draft.approvable)
            assertEquals(0, draft.message.size)
        }
    }

    @Test fun overCapIsRefusedBeforeAnyMessageExists() {
        for (total in listOf(10_000_001L, 25_000_000L, 100_000_000L, Long.MAX_VALUE)) {
            assertEquals("Above the mainnet per-deploy cap of 0.01 SOL", OreDeployLimits.refusal(total, 25, rich, mainnet))
            assertEquals("cap first, even with no balance", "Above the mainnet per-deploy cap of 0.01 SOL", OreDeployLimits.refusal(total, 1, null, mainnet))
            val d = OreDeployReview.prepare(signer, 1, total, all, bh, rich, 0, 1_000_000_000, mainnet)
            assertFalse(d.approvable)
            assertEquals("no bytes means nothing can reach a wallet", 0, d.message.size)
            assertTrue(d.decoded is SolanaMessage.Decoded.Unsupported)
        }
    }

    @Test fun atCapIsApprovableAndEncodesAtMostTheCap() {
        val d = OreDeployReview.prepare(signer, 437127, 10_000_000, all, bh, rich, 0, 1_000_000_000, mainnet)
        assertTrue(d.refusal, d.approvable)
        val dec = (d.decoded as SolanaMessage.Decoded.OreDeploy).deploy
        assertEquals(400_000L, dec.amountPerSquareLamports); assertEquals(25, dec.squareCount)
        assertTrue(dec.totalLamports <= OreDeployLimits.MAINNET_CAP_LAMPORTS)
        assertEquals(OreV3.PROGRAM_ID, d.toAddress)
        // Three squares: 10_000_000 / 3 rounds down, so the encoded total stays under the cap.
        val three = OreDeployReview.prepare(signer, 437127, 10_000_000, 0b111, bh, rich, 0, 1_000_000_000, mainnet)
        assertEquals(9_999_999L, three.lamports)
    }

    @Test fun mainnetStillKeepsTheFeeReserveAndDailyBudget() {
        assertEquals("Above your balance minus the 0.01 SOL fee reserve", OreDeployLimits.refusal(10_000_000, 25, 15_000_000, mainnet))
        assertFalse(OreDeployReview.prepare(signer, 1, 10_000_000, all, bh, rich, 45_000_000, 50_000_000, mainnet).approvable)
    }

    private fun available() = OreDeployAvailability.Available("rpc", 1, 1, 0)

    @Test fun uiNeedsTheExplicitRiskTickAndClampsChipsToTheCap() {
        val ui = OreDeployUi(availability = available(), mainnet = true, totalLamports = 5_000_000, mainnetBalanceLamports = rich)
        assertEquals(MAINNET_ACK_REQUIRED, deployBlocker(ui, true, null))
        assertNull(deployBlocker(ui.copy(riskAcknowledged = true), true, null))
        assertEquals("Above the mainnet per-deploy cap of 0.01 SOL", deployBlocker(ui.copy(riskAcknowledged = true, totalLamports = 10_000_001), true, rich))
        assertEquals(10_000_000L, addChip(ui.copy(totalLamports = 0), 1_000_000_000, rich).totalLamports)
        // The devnet balance never stands in for the mainnet one.
        assertEquals("SOL balance not read, so the deploy limit is unknown",
            deployBlocker(ui.copy(riskAcknowledged = true, mainnetBalanceLamports = null), true, rich))
        assertFalse(OreDeployUi().mainnet)
    }

    @Test fun mainnetOperationsAreSentAndObservedOnlyThroughTheMainnetGateway() = runBlocking {
        val fx = SignedFixture()
        val store = OperationStore(File(tmp.root, "ops.json"))
        val devnet = FakeGateway(); val main = FakeGateway()
        val c = TransferCoordinator(store, { cl: String -> if (cl == SolanaRpc.MAINNET_CLUSTER) main else devnet })
        val op = (store.reserve(SolanaRpc.MAINNET_CLUSTER, fx.signer, fx.recipient, fx.lamports, fx.message, fx.messageSha256, "bh", 200, 50_000_000)
            as OperationStore.ReserveResult.Reserved).op
        assertTrue(c.beginSigningChecked(op.id) is TransferCoordinator.Outcome.Done)
        c.recordSigned(op.id, fx.signature, fx.signedTx)
        c.submit(op.id); c.observe(op.id)
        assertEquals(1, main.sendCount); assertEquals(0, devnet.sendCount)
        assertTrue(devnet.calls.isEmpty())
        assertEquals(SolanaRpc.MAINNET_CLUSTER, store.get(op.id)!!.cluster)
        assertTrue(store.get(op.id)!!.state in setOf(OpState.SUBMITTED, OpState.OUTCOME_UNKNOWN))
        // No retry path: a second submit of the same operation sends nothing.
        c.submit(op.id); assertEquals(1, main.sendCount)
        assertEquals(Base58.encode(fx.signatureBytes), store.get(op.id)!!.signature)
    }

    @Test fun entropyVarMatchesTheSourceConstant() {
        // regolith-labs/ore @48c203bd api/src/consts.rs VAR_ADDRESS; deploy.rs checks var_info.has_address(&VAR_ADDRESS).
        assertEquals("BWCaDY96Xe4WkFq1M7UiCCRcChsJ3p51L5KrGzhxgm2E", Base58.encode(OreDeployMessage.keys(signer, 1)[7]))
    }

    /**
     * Writes the app's exact Deploy bytes for the round in tools/ore-fork-sim/fixtures/snapshot.json, which the LiteSVM
     * mainnet-fork test (tools/ore-fork-sim) executes against the dumped ORE v3 program. Skipped when no snapshot exists.
     */
    @Test fun emitDeployForTheForkSimulation() {
        val snap = File("../tools/ore-fork-sim/fixtures/snapshot.json")
        org.junit.Assume.assumeTrue(snap.exists())
        val roundId = org.json.JSONObject(snap.readText()).getLong("roundId")
        val d = OreDeployReview.prepare(signer, roundId, OreDeployLimits.MAINNET_CAP_LAMPORTS, all, bh, rich, 0, 1_000_000_000, mainnet)
        assertTrue(d.refusal, d.approvable)
        File(snap.parentFile, "kotlin-deploy.json").writeText(org.json.JSONObject()
            .put("roundId", roundId).put("signer", Base58.encode(signer)).put("totalLamports", d.lamports)
            .put("messageHex", d.message.joinToString("") { "%02x".format(it) }).toString())
    }
}
