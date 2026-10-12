package com.edgeore.app

import com.edgeore.app.ore.OreClock
import com.edgeore.app.ore.OreObserver
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ore.Pda
import com.edgeore.app.ore.RoundClock
import com.edgeore.app.solana.AccountReader
import com.edgeore.app.solana.AccountsAt
import com.edgeore.app.solana.RawAccount
import com.edgeore.app.solana.ReadOnlyAccountRpc
import com.edgeore.app.solana.SlotRate
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Decoding against real mainnet account bytes captured on the build host (app/src/test/resources/ore/, written by
 * capture.py there; each file records the RPC, the context slot, its block time and the capture time in UTC).
 * Expected values were decoded independently with Python struct from the same bytes.
 */
class OreV3Test {
    class Fixture(name: String) {
        val j = JSONObject(javaClass.getResource("/ore/$name")!!.readText())
        fun acct(k: String) = j.getJSONObject(k).let { RawAccount(it.getString("owner"), Base64.getDecoder().decode(it.getString("dataBase64"))) }
        fun slot(k: String) = j.getJSONObject(k).getLong("contextSlot")
        val board get() = acct("board"); val treasury get() = acct("treasury"); val round get() = acct("round")
        val roundAddress: String get() = j.getJSONObject("round").getString("address")
        val rate get() = j.getJSONObject("performanceSample").let { SlotRate(it.getLong("samplePeriodSecs") * 1000.0 / it.getLong("numSlots"), it.getLong("numSlots"), it.getLong("samplePeriodSecs")) }
    }
    private val f1 = Fixture("mainnet-round-437127.json")
    private val f2 = Fixture("mainnet-round-437134.json")

    @Test fun fixturesAreRealCapturesWithSlotAndTime() {
        for (f in listOf(f1, f2)) {
            assertEquals(OreV3.PROGRAM_ID, f.j.getString("programId"))
            assertEquals(OreV3.SOURCE_COMMIT, f.j.getString("oreSourceCommit"))
            assertEquals("https://api.mainnet-beta.solana.com", f.j.getString("rpc"))
            assertTrue(f.slot("board") > 455_000_000 && f.j.getLong("boardContextBlockTime") > 1_791_000_000)
            assertTrue(f.j.getString("capturedAtUtc").startsWith("2026-10-12T"))
        }
    }

    @Test fun pdasMatchTheSourceConstantsAndTheCapturedRound() {
        assertEquals(OreV3.BOARD_ADDRESS to 255, Pda.find(listOf("board".toByteArray()), OreV3.PROGRAM_ID))
        assertEquals(OreV3.TREASURY_ADDRESS to 253, Pda.find(listOf("treasury".toByteArray()), OreV3.PROGRAM_ID))
        assertEquals(f1.roundAddress, OreV3.roundAddress(437127))
        assertEquals(f2.roundAddress, OreV3.roundAddress(437134))
    }

    @Test fun decodesBoardTreasuryAndRound() {
        val b = OreV3.decodeBoard(f1.board.owner, f1.board.data)
        assertEquals(OreV3.Board(437127, 455913576, 455913816, 750083956), b)
        assertEquals(460_000_000_000L, OreV3.decodeTreasury(f1.treasury.owner, f1.treasury.data).motherlodeGrams)
        val r = OreV3.decodeRound(f1.round.owner, f1.round.data)
        assertEquals(437127, r.id)
        assertEquals(261558918L, r.deployedLamports[0]); assertEquals(262169410L, r.deployedLamports[24])
        assertEquals(6500628720L, r.totalDeployedLamports)
        assertEquals(122L, r.count[0]); assertEquals(2881L, r.count.sum()); assertEquals(133L, r.totalMiners)
        // reset/deploy.rs: expires_at = end_slot + ONE_DAY_SLOTS (288,000): an independent check of the offsets.
        assertEquals(b.endSlot + 288_000, r.expiresAt)
        assertEquals("6.5", com.edgeore.app.ore.OreFormat.sol(r.totalDeployedLamports))
        assertEquals("4.6", com.edgeore.app.ore.OreFormat.ore(460_000_000_000L))

        val r2 = OreV3.decodeRound(f2.round.owner, f2.round.data)
        assertEquals(437134, r2.id); assertEquals(4933724143L, r2.totalDeployedLamports); assertEquals(127L, r2.totalMiners)
        assertEquals(600_000_000_000L, OreV3.decodeTreasury(f2.treasury.owner, f2.treasury.data).motherlodeGrams)
    }

    @Test fun refusesWrongOwnerSizeOrDiscriminator() {
        val b = f1.board
        assertThrows(OreV3.DecodeException::class.java) { OreV3.decodeBoard("11111111111111111111111111111111", b.data) }
        assertThrows(OreV3.DecodeException::class.java) { OreV3.decodeBoard(b.owner, b.data.copyOf(48)) }
        assertThrows(OreV3.DecodeException::class.java) { OreV3.decodeRound(b.owner, b.data) }
        assertThrows(OreV3.DecodeException::class.java) { OreV3.decodeBoard(b.owner, b.data.clone().also { it[0] = 109 }) }
        assertThrows(OreV3.DecodeException::class.java) { OreV3.decodeTreasury(f1.treasury.owner, f1.treasury.data.clone().also { it[3] = 1 }) }
    }

    @Test fun countdownFromSlots() {
        val b = OreV3.decodeBoard(f1.board.owner, f1.board.data)
        val rate = f1.rate // 277 slots in 60 s on the captured sample
        val at = OreClock.at(b, f1.slot("round"), 0, rate) as RoundClock.Live
        assertEquals(455913816 - 455913778L, at.slotsLeft)
        assertEquals(Math.round(38 * 60_000.0 / 277 / 1000), at.secondsLeft)
        // 3 s later on this phone's clock: ~13 slots at that rate.
        val later = OreClock.at(b, f1.slot("round"), 3_000, rate) as RoundClock.Live
        assertEquals(38L - (3_000 / (60_000.0 / 277)).toLong(), later.slotsLeft)
        // No measured rate: slots only, never an assumed time.
        val noRate = OreClock.at(b, f1.slot("round"), 3_000, null) as RoundClock.Live
        assertNull(noRate.secondsLeft); assertEquals("38 slots", OreClock.label(noRate))
        assertEquals(RoundClock.Ended(4), OreClock.at(b, 455913820, 0, null))
        assertEquals(RoundClock.WaitingForFirstDeploy, OreClock.at(b.copy(endSlot = OreV3.END_SLOT_WAITING), 1, 0, rate))
        assertEquals("00:08", OreClock.mmss(8)); assertEquals("01:05", OreClock.mmss(65))
    }

    private class FakeReader(val byAddress: Map<String, RawAccount?>, val slot: Long) : AccountReader {
        override val endpoint = "https://example.invalid"
        val asked = mutableListOf<List<String>>()
        override suspend fun accounts(addresses: List<String>): AccountsAt { asked += addresses; return AccountsAt(slot, addresses.map { byAddress[it] }) }
        override suspend fun slotRate(): SlotRate? = null
    }

    @Test fun observerReadsBoardThenTheRoundItNames() = runTest {
        val reader = FakeReader(mapOf(OreV3.BOARD_ADDRESS to f1.board, OreV3.TREASURY_ADDRESS to f1.treasury, f1.roundAddress to f1.round), 455913778)
        val s = OreObserver(reader) { 42L }.read()
        assertEquals(listOf(listOf(OreV3.BOARD_ADDRESS, OreV3.TREASURY_ADDRESS), listOf(f1.roundAddress)), reader.asked)
        assertEquals(437127, s.round.id); assertEquals(42L, s.observedAtMs); assertNull(s.slotRate)
    }

    @Test fun observerRefusesAMismatchedOrMissingRound() = runTest {
        // Board says 437127 but the address holds round 437134's bytes: refused, not shown.
        val wrong = FakeReader(mapOf(OreV3.BOARD_ADDRESS to f1.board, OreV3.TREASURY_ADDRESS to f1.treasury, f1.roundAddress to f2.round), 1)
        assertTrue(runCatching { OreObserver(wrong).read() }.exceptionOrNull() is OreV3.DecodeException)
        val missing = FakeReader(mapOf(OreV3.BOARD_ADDRESS to f1.board, OreV3.TREASURY_ADDRESS to f1.treasury), 1)
        assertTrue(runCatching { OreObserver(missing).read() }.exceptionOrNull()?.message!!.contains("not found"))
    }

    @Test fun rpcParsingAndEndpointRules() {
        val r = JSONObject("""{"context":{"slot":7},"value":[null,{"owner":"${OreV3.PROGRAM_ID}","data":["AQI=","base64"]}]}""")
        val a = ReadOnlyAccountRpc.parseMultipleAccounts(r, 2)
        assertEquals(7, a.slot); assertNull(a.accounts[0]); assertEquals(2, a.accounts[1]!!.data.size)
        assertThrows(Exception::class.java) { ReadOnlyAccountRpc.parseMultipleAccounts(r, 3) }
        assertEquals(60_000.0 / 277, ReadOnlyAccountRpc.parsePerformanceSample(JSONObject("""{"result":[{"numSlots":277,"samplePeriodSecs":60}]}"""))!!.msPerSlot, 1e-9)
        assertNull(ReadOnlyAccountRpc.parsePerformanceSample(JSONObject("""{"result":[]}""")))
        for (bad in listOf("http://api.mainnet-beta.solana.com", "https://user:pw@rpc.example", "ftp://x", "nonsense")) {
            assertThrows(bad, IllegalArgumentException::class.java) { ReadOnlyAccountRpc(bad) }
        }
        ReadOnlyAccountRpc("https://api.mainnet-beta.solana.com")
    }
}
