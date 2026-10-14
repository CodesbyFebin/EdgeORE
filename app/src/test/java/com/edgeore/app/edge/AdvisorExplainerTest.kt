package com.edgeore.app.edge

import com.edgeore.app.ai.ondevice.ModelDownloader
import com.edgeore.app.ai.ondevice.OnDeviceCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class AdvisorExplainerTest {
    private val round = AdvisorExplainer.Subject.ROUND

    private val rule = "Round 7: 25 squares, 0.3 SOL deployed."

    @Test fun noModelFallsBackToRulesAndSaysNotConfigured() =
        runBlocking {
            val r = AdvisorExplainer.explain(AdvisorExplainer.Subject.ROUND, rule, rule, null)
            assertEquals(AdvisorExplainer.Source.RULES, r.source)
            assertEquals(rule, r.text)
            assertEquals(RoundExplainer.NOT_CONFIGURED, r.note)
        }

    @Test fun unsupportedDeviceFallsBackGracefully() =
        runBlocking {
            val r =
                AdvisorExplainer.explain(AdvisorExplainer.Subject.TILE, rule, rule) { _, _ ->
                    throw UnsatisfiedLinkError("no arm64")
                }
            assertEquals(AdvisorExplainer.Source.RULES, r.source)
            assertEquals(rule, r.text)
            assertTrue(r.note!!.contains("unavailable on this phone"))
            val empty = AdvisorExplainer.explain(AdvisorExplainer.Subject.TILE, rule, rule) { _, _ -> null }
            assertEquals(AdvisorExplainer.Source.RULES, empty.source)
        }

    @Test fun answersAreLabelledAndEarningsClaimsAreRejected() =
        runBlocking {
            val ok = AdvisorExplainer.explain(round, rule, rule) { _, _ -> "Round 7 has 25 squares." }
            assertEquals(AdvisorExplainer.Source.ON_DEVICE_AI, ok.source)
            assertTrue(ok.text.startsWith(AdvisorExplainer.LABEL))
            assertEquals("AI explanation, may be wrong; check the review.", AdvisorExplainer.LABEL)
            val claims =
                listOf("Expect 40% APR here.", "This is guaranteed.", "Your earnings will be high.") +
                    listOf("Great yield", "profit!")
            for (bad in claims) {
                val r = AdvisorExplainer.explain(AdvisorExplainer.Subject.ROUND, rule, rule) { _, _ -> bad }
                assertEquals(bad, AdvisorExplainer.Source.RULES, r.source)
                assertEquals(rule, r.text)
            }
        }

    @Test fun promptOnlyCarriesFactsAndForbidsAmountsAndEarnings() {
        val p = AdvisorExplainer.prompt(AdvisorExplainer.Subject.REVIEW_MESSAGE, "FACT")
        assertTrue(p.endsWith("FACTS:\nFACT"))
        assertTrue(AdvisorExplainer.SYSTEM.contains("Do not suggest amounts"))
        assertTrue(AdvisorExplainer.SYSTEM.contains("cannot sign"))
        val f = AdvisorExplainer.reviewFacts(9, 0b101, 1000, 2000, true)
        assertTrue(f.contains("squares 1,3") && f.contains("2000 lamports total") && f.contains("capped at 0.01 SOL"))
    }

    /** No-sign guarantee, structurally: explainer + VM wiring never touch signing, MWA, keys or the deploy form. */
    @Test fun explainerHasNoPathToSigningKeysOrAmounts() {
        val forbidden =
            listOf(
                "wallet",
                "Wallet",
                "Mwa",
                "MWA",
                "ReceiptSigner",
                "signer",
                "KeyStore",
                "Keystore",
                ".sign(",
                "transact",
                "_oreDeploy",
                "OreDeployReview",
                "StrategyPreset",
                "applyPreset",
                "_review.update",
                "_review.value =",
            )
        val explainer = File("src/main/java/com/edgeore/app/edge/AdvisorExplainer.kt").readText()
        val vm = File("src/main/java/com/edgeore/app/EdgeOreViewModel.kt").readText()
        val wiring = vm.substring(vm.indexOf("private fun onDeviceAdvisorModel"))
        for (src in listOf(explainer, wiring).map(::code)) {
            forbidden.forEach { assertFalse("found $it", src.contains(it)) }
        }
        val ui = File("src/main/java/com/edgeore/app/ui/screens/EdgePanels.kt").readText()
        val section = ui.substring(ui.indexOf("fun ReviewExplainSection"))
        assertTrue(section.contains("vm.explainReview()"))
        val banned = listOf("onSign", "signReviewed", "applyPreset", "setAmount", "editOreDeploy")
        banned.forEach { assertFalse(it, section.contains(it)) }
    }

    @Test fun modelIsOnlyUsableAfterChecksumVerifiedDownload() {
        val asset = File("src/main/assets/${OnDeviceCatalog.ASSET}").readText()
        val downloadable = OnDeviceCatalog.parse(asset).filter { !it.gated }
        assertTrue(downloadable.isNotEmpty())
        downloadable.forEach { assertTrue(it.sha256!!.matches(Regex("[0-9a-f]{64}"))) }
        val d = Files.createTempDirectory("adv").toFile()
        val bytes = ByteArray(4096) { (it % 13).toByte() }
        val good = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val part = File(d, "m.part")
        val dest = File(d, "m.litertlm")

        fun copy(sha: String): ModelDownloader.Result {
            val input = ByteArrayInputStream(bytes)
            return ModelDownloader.copyVerified(input, 4096, sha, part, dest, { _, _ -> }, { false })
        }
        val bad = copy("f".repeat(64))
        assertTrue(bad is ModelDownloader.Result.Failed)
        assertFalse(dest.exists())
        val ok = copy(good)
        assertTrue(ok is ModelDownloader.Result.Ok)
    }

    /** Source without comments, so docs may describe what the code cannot touch. */
    private fun code(src: String) =
        src
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//[^\\n]*"), "")
}
