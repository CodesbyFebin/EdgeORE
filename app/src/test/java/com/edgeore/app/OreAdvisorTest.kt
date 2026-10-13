package com.edgeore.app

import com.edgeore.app.ore.OreAdvisor
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.OreV3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OreAdvisorTest {
    private val f = OreV3Test.Fixture("mainnet-round-437127.json")
    private val snap =
        OreSnapshot(
            "https://api.mainnet-beta.solana.com",
            OreV3.decodeBoard(f.board.owner, f.board.data),
            f.slot("board"),
            OreV3.decodeTreasury(f.treasury.owner, f.treasury.data),
            OreV3.decodeRound(f.round.owner, f.round.data),
            f.slot("round"),
            0L,
            null,
        )
    private val prompt = OreAdvisor.prompt(snap, "38 slots", "Which square has the most SOL?")

    @Test fun promptCarriesOnlySnapshotValuesAndSourcedFacts() {
        assertTrue(prompt.contains("Round id: 437127"))
        assertTrue(prompt.contains("Total SOL deployed this round: 6.5"))
        assertTrue(prompt.contains("Motherlode pool: 4.6 ORE"))
        assertTrue(prompt.contains("square 1: 0.261 SOL, 122 miners"))
        assertTrue(prompt.contains(OreV3.SOURCE_COMMIT.take(12)))
        assertTrue(prompt.endsWith("Question: Which square has the most SOL?"))
    }

    @Test fun everyReplyGetsTheNoticeAndUnknownNumbersAreReplaced() {
        val c = OreAdvisor.check("Square 3 has 0.275 SOL. You could win 12.7 SOL with 83% odds.", prompt)
        assertTrue(c.text.endsWith(OreAdvisor.NOTICE))
        assertTrue(c.text.contains("Square 3 has 0.275 SOL"))
        assertFalse(c.text.contains("12.7"))
        assertFalse(c.text.contains("83"))
        assertEquals(2, c.redacted)
        assertEquals("Not financial advice. ORE rounds can lose SOL. EdgeORE cannot deploy.", OreAdvisor.NOTICE)
        val plain = OreAdvisor.check("The round has 133 miners.", prompt)
        assertEquals(0, plain.redacted)
        assertTrue(plain.text.startsWith("The round has 133 miners."))
        assertTrue(plain.text.endsWith(OreAdvisor.NOTICE))
    }

    @Test fun systemPromptForbidsActionsAndInventedNumbers() {
        val s = OreAdvisor.SYSTEM_PROMPT.lowercase()
        for (w in listOf("no tools", "cannot sign, send", "deploy", "never compute, estimate or invent")) assertTrue(w, s.contains(w))
        assertTrue(OreAdvisor.NOT_CONFIGURED.startsWith("Set up"))
    }
}
