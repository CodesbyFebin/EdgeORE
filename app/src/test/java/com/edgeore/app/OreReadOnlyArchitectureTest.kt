package com.edgeore.app

import com.edgeore.app.solana.AccountReader
import com.edgeore.app.solana.ReadOnlyAccountRpc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The ORE observation path (ore/, OreScreen, OreHomeStrip, the account reader) has no way to build, sign or submit a
 * transaction. Exactly one allowlisted file builds an ORE instruction (solana/OreDeployMessage.kt, Deploy only), and
 * only the reviewed signing path (OreDeployReview -> EdgeOreViewModel review -> MWA -> TransferReview check -> submit) uses it.
 */
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

    private val main = File("src/main/java/com/edgeore/app")
    private fun rel(f: File) = f.relativeTo(main).path
    private val all = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun onlyTheAllowlistedFileBuildsAnOreInstruction() {
        val builders = all.filter { Regex("""DEPLOY_DISCRIMINATOR|ProgramDerivedAddress|"automation"\.toByteArray|"miner"\.toByteArray""").containsMatchIn(it.readText()) }.map(::rel).toSet()
        assertEquals(setOf("solana/OreDeployMessage.kt", "ore/OreV3.kt"), builders) // OreV3.kt: the PDA helper used for read-only round addresses
        assertFalse(File(main, "ore/OreV3.kt").readText().contains("DEPLOY_DISCRIMINATOR"))
        val usesBuilder = all.filter { Regex("""OreDeployMessage\.(build|data|keys)\(""").containsMatchIn(it.readText()) }.map(::rel).toSet()
        assertEquals(setOf("solana/OreDeployReview.kt"), usesBuilder)
        val usesReview = all.filter { it.readText().contains("OreDeployReview.prepare(") }.map(::rel).toSet()
        assertEquals(setOf("EdgeOreViewModel.kt"), usesReview)
    }

    @Test fun theBuilderEncodesOnlyDeploy() {
        val src = File(main, "solana/OreDeployMessage.kt").readText()
        assertTrue(src.contains("const val DEPLOY_DISCRIMINATOR = 6"))
        for (other in listOf("Automate", "Checkpoint", "ClaimSOL", "ClaimORE", "Close", "Buyback", "Bury", "Wrap", "UpdateProtocolConfig", "Liq"))
            assertFalse(other, Regex("""\b$other\b""").containsMatchIn(src))
        for (b in listOf("sendTransaction", "signTransaction", "MobileWalletAdapter")) assertFalse(b, src.contains(b))
    }

    @Test fun theMineDeployCardOnlyHandsOffToTheReview() {
        val ui = File(main, "ui/screens/MineDeploy.kt").readText()
        for (b in listOf("sendTransaction", "signTransaction", "OreDeployMessage", "SolanaRpc(", "TransferCoordinator", "wallet.sign")) assertFalse(b, ui.contains(b))
        assertTrue(ui.contains("Not financial advice"))
        val vm = File(main, "EdgeOreViewModel.kt").readText()
        val fn = vm.substring(vm.indexOf("fun prepareOreDeploy()"), vm.indexOf("/** Which AI path the advisor would use"))
        for (b in listOf("signTransaction", "submit(", ".send(")) assertFalse(b, fn.contains(b))
    }

    @Test fun deployIsDevnetOnlyAndMainnetFlagIsOff() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("buildConfigField(\"boolean\", \"ORE_MAINNET_DEPLOY\", \"false\")"))
        assertFalse(Regex("""ORE_MAINNET_DEPLOY", "true"""").containsMatchIn(gradle))
        assertFalse(com.edgeore.app.solana.OreDeployNetwork.MAINNET_ENABLED)
        assertEquals("https://api.devnet.solana.com", com.edgeore.app.solana.OreDeployNetwork.RPC)
        val net = File(main, "solana/OreDeployReview.kt").readText()
        assertFalse(net.contains("mainnet-beta"))
    }
}
