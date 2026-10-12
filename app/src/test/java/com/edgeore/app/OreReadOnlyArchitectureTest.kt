package com.edgeore.app

import com.edgeore.app.solana.AccountReader
import com.edgeore.app.solana.ReadOnlyAccountRpc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The ORE code path has no way to build, sign or submit a transaction, and no ORE instruction builder. */
class OreReadOnlyArchitectureTest {
    private val sources = File("src/main/java/com/edgeore/app/ore").listFiles()!!.toList() +
        File("src/main/java/com/edgeore/app/ui/screens/OreScreen.kt") + File("src/main/java/com/edgeore/app/ui/screens/OreHomeStrip.kt") + File("src/main/java/com/edgeore/app/solana/AccountReader.kt") + File("src/main/java/com/edgeore/app/solana/JsonRpcHttp.kt")

    @Test fun noSigningTransactionOrWalletApisInTheOrePath() {
        assertTrue(sources.size >= 6 && sources.all { it.isFile })
        val banned = listOf(
            Regex("""\bSolanaRpc\b"""), Regex("""sendTransaction|signTransaction|signAndSend|signMessages?\b"""), Regex("""\bTransaction[A-Z]?\w*\("""),
            Regex("""\bChainGateway\b"""), Regex("""TransferCoordinator|TransferReview|SolanaMessage"""), Regex("""walletadapter|MobileWalletAdapter|WalletConnection""", RegexOption.IGNORE_CASE),
            Regex("""\bEd25519\b|KeyPair|Signer\b"""), Regex("""latestBlockhash|getLatestBlockhash|feeForMessage"""), Regex("""Instruction\(|AccountMeta"""),
        )
        for (f in sources) {
            val text = f.readText()
            for (b in banned) assertFalse("${f.name} matches ${b.pattern}", b.containsMatchIn(text))
        }
    }

    @Test fun theReaderTypesExposeOnlyReads() {
        val allowed = setOf("accounts", "slotRate", "getEndpoint")
        assertEquals(allowed, AccountReader::class.java.declaredMethods.filter { !it.isSynthetic }.map { it.name }.toSet())
        val public = ReadOnlyAccountRpc::class.java.declaredMethods.filter { java.lang.reflect.Modifier.isPublic(it.modifiers) && !it.isSynthetic }.map { it.name }.toSet()
        assertEquals(allowed, public)
        for (m in public) assertFalse(m, Regex("send|sign|transfer|deploy|stake|trade", RegexOption.IGNORE_CASE).containsMatchIn(m))
    }

    @Test fun theScreenOffersNoActingControls() {
        val ui = File("src/main/java/com/edgeore/app/ui/screens/OreScreen.kt").readText()
        for (chip in listOf("\"+0.01\"", "\"+0.1\"", "\"+1\"", "\"MAX\"", "\"ALL\"")) assertFalse(chip, ui.contains(chip))
        assertTrue(ui.contains("enabled = false") && ui.contains("Deploy disabled in EdgeORE (read-only)"))
        assertTrue(ui.contains("Read-only · mainnet observation"))
    }

    @Test fun theMineHomeStripOffersNoActingControlsOrHardcodedPrices() {
        val ui = File("src/main/java/com/edgeore/app/ui/screens/OreHomeStrip.kt").readText()
        for (chip in listOf("\"+0.01\"", "\"+0.1\"", "\"+1\"", "\"MAX\"", "\"ALL\"", "Deploy\"", "per round", "Rounds\"")) assertFalse(chip, ui.contains(chip))
        assertFalse("no hardcoded dollar amounts", Regex(""""\$\d""").containsMatchIn(ui))
        val mine = File("src/main/java/com/edgeore/app/ui/screens/MineScreen.kt").readText()
        assertFalse(mine.contains("PriceQuote("))
    }
}
