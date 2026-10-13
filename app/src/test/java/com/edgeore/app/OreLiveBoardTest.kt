package com.edgeore.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.edgeore.app.ore.OreDeployAvailability
import com.edgeore.app.ore.OreObserver
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.OreV3
import com.edgeore.app.solana.AccountReader
import com.edgeore.app.solana.AccountsAt
import com.edgeore.app.solana.RawAccount
import com.edgeore.app.solana.SlotRate
import com.edgeore.app.ui.screens.OreMiningCard
import com.edgeore.app.ui.screens.boardSyncLabel
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Live ORE v3 board on the Home ORE card, against a real mainnet capture (round 438915, written read-only by
 * app/src/test/resources/ore/capture.py on 2026-10-13). Expected numbers were decoded independently with Python struct.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28], qualifiers = "w400dp-h2000dp")
class OreLiveBoardTest {
    @get:Rule val rule = createComposeRule()
    private val f = OreV3Test.Fixture("mainnet-round-438915.json")

    private class FakeReader(
        val board: RawAccount?,
        val treasury: RawAccount?,
        val round: RawAccount?,
    ) : AccountReader {
        override val endpoint = "https://api.mainnet-beta.solana.com"
        val asked = mutableListOf<List<String>>()

        override suspend fun accounts(addresses: List<String>): AccountsAt {
            asked += addresses
            return if (addresses.size == 2) {
                AccountsAt(456440769, listOf(board, treasury))
            } else {
                AccountsAt(456440770, listOf(round))
            }
        }

        override suspend fun slotRate(): SlotRate? = null
    }

    private fun snap(observedAt: Long = 1_000L) =
        OreSnapshot(
            "https://api.mainnet-beta.solana.com",
            OreV3.decodeBoard(f.board.owner, f.board.data),
            f.slot("board"),
            OreV3.decodeTreasury(f.treasury.owner, f.treasury.data),
            OreV3.decodeRound(f.round.owner, f.round.data),
            f.slot("round"),
            observedAt,
            f.rate,
        )

    @Test fun realCaptureDecodesPerSquareSolMinersAndRoundId() {
        val s = snap()
        assertEquals(438915L, s.board.roundId)
        assertEquals(438915L, s.round.id)
        assertEquals(listOf(438244062L, 437352284L, 440093538L), s.round.deployedLamports.take(3))
        assertEquals(listOf(126L, 123L, 130L), s.round.count.take(3))
        assertEquals(22460000000000L, s.treasury.motherlodeGrams)
        assertEquals(f.roundAddress, OreV3.roundAddress(438915))
    }

    @Test fun observerReadsBoardThenRoundPdaAndRejectsMismatchedOwnerSizeDiscriminatorOrRound() {
        val ok = FakeReader(f.board, f.treasury, f.round)
        val s = runBlocking { OreObserver(ok).read() }
        assertEquals(438915L, s.round.id)
        assertEquals(listOf(OreV3.BOARD_ADDRESS, OreV3.TREASURY_ADDRESS), ok.asked[0])
        assertEquals(listOf(OreV3.roundAddress(438915)), ok.asked[1])

        val wrongOwner = RawAccount("11111111111111111111111111111111", f.round.data)
        val short = RawAccount(f.round.owner, f.round.data.copyOf(f.round.data.size - 8))
        val badDisc =
            RawAccount(
                f.round.owner,
                f.round.data
                    .copyOf()
                    .also { it[0] = 105 },
            )
        val otherRound =
            RawAccount(
                f.round.owner,
                f.round.data
                    .copyOf()
                    .also { it[8] = (it[8] + 1).toByte() },
            )
        for (r in listOf(wrongOwner, short, badDisc, otherRound, null)) {
            val reader = FakeReader(f.board, f.treasury, r)
            assertThrows(OreV3.DecodeException::class.java) { runBlocking { OreObserver(reader).read() } }
        }
        val boardWrongOwner = RawAccount("11111111111111111111111111111111", f.board.data)
        assertThrows(
            OreV3.DecodeException::class.java,
        ) { runBlocking { OreObserver(FakeReader(boardWrongOwner, f.treasury, f.round)).read() } }
    }

    @Test fun syncLabelCoversDevnetFailureWaitingAndLive() {
        val s = snap()
        val devnet = "ORE v3 is not deployed on devnet. The live board is mainnet only."
        assertEquals(devnet, boardSyncLabel(false, s, null, 1_000L))
        assertTrue(boardSyncLabel(true, null, "Read failed: timeout", 1_000L).startsWith("Not synced."))
        val stale = boardSyncLabel(true, s, "Read failed: timeout", 31_000L)
        assertTrue(stale.startsWith("Not synced. Showing the read from 30s ago."))
        assertTrue(boardSyncLabel(true, s, null, 1_000L).startsWith("Mainnet round #438915 · "))
        assertTrue(boardSyncLabel(true, s, null, 1_000L).contains("slots left"))
        val waiting = s.copy(board = s.board.copy(endSlot = OreV3.END_SLOT_WAITING))
        assertTrue(boardSyncLabel(true, waiting, null, 1_000L).contains("Waiting for first deploy"))
    }

    private val avail = OreDeployAvailability.Available("https://api.mainnet-beta.solana.com", 438915, 9, 0)

    @Test fun mainnetGridShowsLiveSolAndMinersAndRiskWording() {
        rule.setContent {
            EdgeOreTheme {
                OreMiningCard(
                    OreDeployUi(availability = avail, mainnet = true),
                    true,
                    5_000_000_000,
                    {},
                    {},
                    {},
                    snapshot = snap(),
                    nowMs = 1_000L,
                )
            }
        }
        rule.onNodeWithTag("miners-0").assertTextEquals("126")
        rule.onNodeWithTag("sol-0").assertTextEquals("◎0.438")
        rule.onNodeWithText("I understand SOL on squares that don't win can be lost.").assertExists()
        rule.onNodeWithText("Mainnet round #438915", substring = true).assertExists()
    }

    @Test fun devnetHidesTheMainnetBoard() {
        rule.setContent {
            EdgeOreTheme {
                val ui = OreDeployUi(mainnet = false)
                OreMiningCard(ui, true, 5_000_000_000, {}, {}, {}, snapshot = snap(), nowMs = 1_000L)
            }
        }
        rule.onNodeWithTag("miners-0").assertDoesNotExist()
        rule.onNodeWithText("ORE v3 is not deployed on devnet. The live board is mainnet only.").assertExists()
    }

    @Test fun failedReadSaysNotSynced() {
        rule.setContent {
            EdgeOreTheme {
                OreMiningCard(
                    OreDeployUi(availability = avail, mainnet = true),
                    true,
                    5_000_000_000,
                    {},
                    {},
                    {},
                    snapshot = null,
                    nowMs = 1_000L,
                    syncError = "Read failed: timeout",
                )
            }
        }
        rule.onNodeWithText("Not synced. Read failed: timeout").assertExists()
    }

    /** JVM render of the live grid from fixture data (round 438915). Not a phone capture. Needs LIVE_BOARD_DIR. */
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test
    fun renderLiveBoard() {
        val dir = System.getenv("LIVE_BOARD_DIR")
        org.junit.Assume.assumeTrue("set LIVE_BOARD_DIR to render", !dir.isNullOrBlank())
        rule.setContent {
            EdgeOreTheme {
                Surface(Modifier.fillMaxSize(), color = EdgeColors.background) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
                        androidx.compose.material3.Text(
                            "JVM render · fixture: real mainnet capture, round 438915 (2026-10-13) · " +
                                "not a phone screenshot",
                            color = EdgeColors.mainnetWarning,
                        )
                        OreMiningCard(
                            OreDeployUi(availability = avail, mainnet = true, pro = true),
                            true,
                            5_000_000_000,
                            {},
                            {},
                            {},
                            snapshot = snap(),
                            nowMs = 1_000L,
                        )
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("$dir/live-board.png")
    }
}
