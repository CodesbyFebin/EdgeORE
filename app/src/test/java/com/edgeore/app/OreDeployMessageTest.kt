package com.edgeore.app

import com.edgeore.app.crypto.Base58
import com.edgeore.app.ore.OreDeployAvailability
import com.edgeore.app.ore.OreDeployCheck
import com.edgeore.app.ore.OreV3
import com.edgeore.app.solana.AccountReader
import com.edgeore.app.solana.AccountsAt
import com.edgeore.app.solana.OreDeployLimits
import com.edgeore.app.solana.OreDeployMessage
import com.edgeore.app.solana.OreDeployReview
import com.edgeore.app.solana.RawAccount
import com.edgeore.app.solana.SlotRate
import com.edgeore.app.solana.SolanaMessage
import com.edgeore.app.solana.TransferReview
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ORE v3 Deploy encoding against fixtures derived independently (tools/ore-fixtures/gen_deploy_fixture.py) from the ORE source. */
class OreDeployMessageTest {
    private val fx = JSONObject(javaClass.getResource("/ore/deploy-fixture.json")!!.readText())
    private val cases = (0 until fx.getJSONArray("cases").length()).map { fx.getJSONArray("cases").getJSONObject(it) }
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun deploy(c: JSONObject) = OreDeployMessage.Deploy(Base58.decodePublicKey(c.getString("signer")), c.getLong("roundId"),
        c.getLong("amountPerSquareLamports"), c.getInt("mask"), Base58.decodePublicKey(c.getString("blockhash")))

    @Test fun fixtureIdsMatchTheApp() {
        assertEquals(OreV3.PROGRAM_ID, fx.getString("programId"))
        assertEquals(OreDeployMessage.ENTROPY_PROGRAM_ID, fx.getString("entropyProgramId"))
        assertEquals(OreV3.BOARD_ADDRESS, fx.getString("board"))
        assertEquals(OreV3.TREASURY_ADDRESS, fx.getString("treasury"))
        assertEquals(OreDeployMessage.CONFIG_ADDRESS, fx.getString("config"))
        assertTrue(fx.getString("source").contains(OreV3.SOURCE_COMMIT))
    }

    @Test fun buildMatchesTheSourceDerivedBytes() {
        assertEquals(2, cases.size)
        for (c in cases) {
            val d = deploy(c)
            assertEquals(c.getString("name"), c.getString("dataHex"), OreDeployMessage.data(d.amountPerSquareLamports, d.mask).joinToString("") { "%02x".format(it) })
            val msg = OreDeployMessage.build(d)
            assertArrayEquals(c.getString("name"), hex(c.getString("messageHex")), msg)
            assertEquals(c.getString("messageSha256"), OreDeployMessage.sha256(msg))
            val keys = OreDeployMessage.keys(d.signer, d.roundId).map(Base58::encode)
            val acc = c.getJSONObject("accounts")
            assertEquals(listOf(acc.getString("automation"), OreV3.BOARD_ADDRESS, OreDeployMessage.CONFIG_ADDRESS, acc.getString("miner"), acc.getString("round"),
                OreV3.TREASURY_ADDRESS, acc.getString("entropyVar")), keys.subList(1, 8))
            assertEquals(OreV3.roundAddress(d.roundId), acc.getString("round"))
        }
    }

    @Test fun decodeAcceptsOnlyTheExactShapeForTheReviewedRound() {
        val c = cases[1]; val d = deploy(c); val msg = OreDeployMessage.build(d)
        val back = OreDeployMessage.decode(msg, d.roundId)!!
        assertEquals(d.amountPerSquareLamports, back.amountPerSquareLamports); assertEquals(d.mask, back.mask); assertEquals(3, back.squareCount)
        assertNull("other round", OreDeployMessage.decode(msg, d.roundId + 1))
        fun flip(i: Int) = msg.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
        for (i in listOf(0, 10, 200, 390, msg.size - 13)) assertNull("byte $i flipped", OreDeployMessage.decode(flip(i), d.roundId)) // header, keys, program index, discriminator
        // Flipping an amount or mask byte is a different, valid deploy; the decoder reports it as such (and the wallet-bytes check refuses it).
        assertEquals(d.mask xor (1 shl 24), OreDeployMessage.decode(flip(msg.size - 1), d.roundId)!!.mask)
        assertNull("trailing", OreDeployMessage.decode(msg + byteArrayOf(0), d.roundId))
        assertTrue("not a transfer", SolanaMessage.decode(msg) is SolanaMessage.Decoded.Unsupported)
    }

    @Test fun builderRefusesBadInputs() {
        val d = deploy(cases[0])
        for (bad in listOf(d.copy(amountPerSquareLamports = 0), d.copy(mask = 0), d.copy(mask = 1 shl 25), d.copy(roundId = -1)))
            assertTrue(runCatching { OreDeployMessage.build(bad) }.isFailure)
    }

    @Test fun limitsCapMaxAndTheDeploy() {
        assertNull(OreDeployLimits.max(null))
        assertEquals(0L, OreDeployLimits.max(5_000_000))
        assertEquals(40_000_000L, OreDeployLimits.max(50_000_000))
        assertEquals(OreDeployLimits.PER_DEPLOY_CAP_LAMPORTS, OreDeployLimits.max(10_000_000_000))
        assertEquals("Above the per-deploy cap of 0.1 SOL", OreDeployLimits.refusal(100_000_001, 25, 10_000_000_000))
        assertEquals("Above your balance minus the 0.01 SOL fee reserve", OreDeployLimits.refusal(45_000_000, 25, 50_000_000))
        assertTrue(OreDeployLimits.refusal(10, 25, 10_000_000_000)!!.contains("too small"))
        assertNull(OreDeployLimits.refusal(25_000_000, 25, 1_000_000_000))
        assertEquals(1_000_000L, OreDeployLimits.perSquare(25_000_000, 25))
    }

    private val signer = ByteArray(32) { (it + 1).toByte() }
    private val bh = ByteArray(32) { 7 }

    @Test fun reviewPreparesAnApprovableDraftOnlyWithinLimits() {
        val ok = OreDeployReview.prepare(signer, 437127, 25_000_000, OreDeployMessage.ALL_SQUARES_MASK, bh, 1_000_000_000, 0, 50_000_000)
        assertTrue(ok.refusal, ok.approvable)
        assertEquals(25_000_000L, ok.lamports); assertEquals(OreV3.PROGRAM_ID, ok.toAddress)
        assertTrue(ok.decoded is SolanaMessage.Decoded.OreDeploy)
        assertFalse(OreDeployReview.prepare(signer, 1, 25_000_000, OreDeployMessage.ALL_SQUARES_MASK, bh, null, 0, 50_000_000).approvable)
        assertFalse(OreDeployReview.prepare(signer, 1, 25_000_000, OreDeployMessage.ALL_SQUARES_MASK, bh, 1_000_000_000, 40_000_000, 50_000_000).approvable) // daily budget
        assertEquals("ORE deploy is devnet-only in this build",
            OreDeployReview.prepare(signer, 1, 25_000_000, OreDeployMessage.ALL_SQUARES_MASK, bh, 1_000_000_000, 0, 50_000_000, cluster = "mainnet-beta").refusal)
    }

    @Test fun walletReturningDifferentBytesIsRefused() {
        val d = OreDeployReview.prepare(signer, 437127, 25_000_000, OreDeployMessage.ALL_SQUARES_MASK, bh, 1_000_000_000, 0, 50_000_000)
        val other = OreDeployMessage.build(OreDeployMessage.Deploy(signer, 437127, 2_000_000, OreDeployMessage.ALL_SQUARES_MASK, bh))
        val r = TransferReview.verifyWalletReturn(d.message, signer, SolanaMessage.unsignedTransaction(other))
        assertTrue(r is TransferReview.WalletReturn.Refused)
    }

    private class Fake(private val accts: List<RawAccount?>, private val fail: Boolean = false) : AccountReader {
        override val endpoint = "https://api.devnet.solana.com"
        override suspend fun accounts(addresses: List<String>): AccountsAt { if (fail) error("timeout"); return AccountsAt(9, accts) }
        override suspend fun slotRate(): SlotRate? = null
    }

    @Test fun devnetCheckNeverAssumesTheProgramIsThere() = runBlocking {
        val none = OreDeployCheck.check(Fake(listOf(null, null, null, null))) as OreDeployAvailability.Unavailable
        assertTrue(none.reason.contains("not deployed"))
        assertTrue(OreDeployCheck.check(Fake(emptyList(), fail = true)) is OreDeployAvailability.Unavailable)
        val prog = RawAccount(OreDeployCheck.UPGRADEABLE_LOADER, ByteArray(36))
        assertTrue((OreDeployCheck.check(Fake(listOf(prog, null, null, null))) as OreDeployAvailability.Unavailable).reason.contains("missing"))
        val f = OreV3Test.Fixture("mainnet-round-437127.json")
        val ok = OreDeployCheck.check(Fake(listOf(prog, f.board, f.treasury, RawAccount(OreV3.PROGRAM_ID, ByteArray(8))))) as OreDeployAvailability.Available
        assertEquals(437127L, ok.roundId)
    }
}
