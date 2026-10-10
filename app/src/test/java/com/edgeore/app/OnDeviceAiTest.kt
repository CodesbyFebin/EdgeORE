package com.edgeore.app

import com.edgeore.app.ai.ExecutionLocation
import com.edgeore.app.ai.ondevice.AllowlistException
import com.edgeore.app.ai.ondevice.ModelDownloader
import com.edgeore.app.ai.ondevice.ModelStore
import com.edgeore.app.ai.ondevice.OnDeviceAiController
import com.edgeore.app.ai.ondevice.OnDeviceCatalog
import com.edgeore.app.ai.ondevice.OnDeviceModel
import com.edgeore.app.ai.ondevice.OnDevicePhase
import com.edgeore.app.ai.ondevice.OnDeviceRuntime
import com.edgeore.app.ai.ondevice.RuntimeLoader
import com.edgeore.app.crypto.Sha256
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class OnDeviceAiTest {
    private val asset = File("src/main/assets/${OnDeviceCatalog.ASSET}").readText()
    private fun tmp(): File = Files.createTempDirectory("ondevice").toFile()

    private fun model(bytes: ByteArray, gated: Boolean = false, sha: String? = Sha256.hex(bytes)) = OnDeviceModel(
        "tiny", "Tiny", "litert-community/Tiny", "tiny.litertlm", "a".repeat(40), bytes.size.toLong(), sha,
        "Apache-2.0", gated, null, 20, 0.8, 0.7, 256)

    private class FakeConn(url: URL, private val body: ByteArray, private val code: Int = 200, private val length: Long = body.size.toLong()) : HttpURLConnection(url) {
        var opened = false
        override fun connect() { opened = true }
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode(): Int { opened = true; return code }
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun getHeaderFieldLong(name: String?, default: Long): Long = if (name.equals("Content-Length", true)) length else default
    }

    private class FakeRuntime(val reply: String) : OnDeviceRuntime {
        var closed = false; var prompts = mutableListOf<String>()
        override fun generate(system: String, prompt: String): String { prompts += prompt; return reply }
        override fun cancel() = false
        override fun close() { closed = true }
    }

    // ---- allowlist ----

    @Test fun bundledAllowlistIsGallerySubsetPinnedToCommits() {
        val models = OnDeviceCatalog.parse(asset)
        assertEquals(listOf("qwen2.5-1.5b-instruct-q8", "gemma3-1b-it-int4"), models.map { it.id })
        val qwen = models[0]
        assertTrue(qwen.downloadable)
        assertEquals(1_597_931_520L, qwen.sizeBytes)
        assertEquals("https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true", qwen.downloadUrl)
        val gemma = models[1]
        assertTrue(gemma.gated); assertFalse(gemma.downloadable); assertNull(gemma.sha256)
        assertTrue(JSONObject(asset).getString("source").contains("google-ai-edge/gallery"))
    }

    private fun entry(mutate: (JSONObject) -> Unit): String {
        val root = JSONObject(asset); mutate(root.getJSONArray("models").getJSONObject(0)); return root.toString()
    }

    @Test fun allowlistRejectsUnpinnedTraversalAndUnverifiableEntries() {
        val bad = listOf(
            entry { it.put("commitHash", "main") },
            entry { it.put("modelFile", "../../shared_prefs/x.litertlm") },
            entry { it.put("modelFile", "model.bin") },
            entry { it.put("modelId", "https://evil.example/x") },
            entry { it.put("sha256", JSONObject.NULL) },
            entry { it.put("sizeInBytes", 0) },
            entry { it.put("id", "gemma3-1b-it-int4") },
        )
        bad.forEachIndexed { i, json -> try { OnDeviceCatalog.parse(json); fail("case $i accepted") } catch (_: AllowlistException) {} }
    }

    // ---- verified copy ----

    @Test fun verifiedCopyRenamesOnlyOnExactSizeAndDigest() {
        val d = tmp(); val bytes = ByteArray(300_000) { (it % 251).toByte() }
        val part = File(d, "m.part"); val dest = File(d, "m")
        val ok = ModelDownloader.copyVerified(ByteArrayInputStream(bytes), bytes.size.toLong(), Sha256.hex(bytes), part, dest, { _, _ -> }, { false })
        assertTrue(ok is ModelDownloader.Result.Ok); assertTrue(dest.isFile); assertFalse(part.exists())
        assertEquals(Sha256.hex(bytes), (ok as ModelDownloader.Result.Ok).sha256)
    }

    @Test fun verifiedCopyRejectsShortLongAndWrongDigestWithoutLeavingFiles() {
        val d = tmp(); val bytes = ByteArray(1000) { 7 }
        val part = File(d, "m.part"); val dest = File(d, "m")
        val cases = listOf(
            Triple(bytes.copyOf(999), 1000L, Sha256.hex(bytes)),
            Triple(bytes + byteArrayOf(1), 1000L, Sha256.hex(bytes)),
            Triple(bytes, 1000L, "0".repeat(64)),
        )
        for ((input, size, sha) in cases) {
            val r = ModelDownloader.copyVerified(ByteArrayInputStream(input), size, sha, part, dest, { _, _ -> }, { false })
            assertTrue(r.toString(), r is ModelDownloader.Result.Failed)
            assertFalse(dest.exists()); assertFalse(part.exists())
        }
        val c = ModelDownloader.copyVerified(ByteArrayInputStream(bytes), 1000L, Sha256.hex(bytes), part, dest, { _, _ -> }, { true })
        assertEquals(ModelDownloader.Result.Cancelled, c); assertFalse(dest.exists()); assertFalse(part.exists())
    }

    @Test fun storeCountsOnlyExactSizeFilesAsDownloaded() {
        val store = ModelStore(tmp()); val m = model(ByteArray(10))
        assertFalse(store.isDownloaded(m))
        store.dirFor(m).mkdirs(); store.fileFor(m).writeBytes(ByteArray(9))
        assertFalse(store.isDownloaded(m))
        store.fileFor(m).writeBytes(ByteArray(10))
        assertTrue(store.isDownloaded(m))
        assertTrue(store.delete(m)); assertFalse(store.isDownloaded(m))
    }

    @Test fun downloaderNeverOpensAConnectionForGatedOrUnpinnedModels() {
        var opened = 0
        val dl = ModelDownloader { opened++; error("must not open") }
        val store = ModelStore(tmp())
        assertTrue(dl.download(model(ByteArray(4), gated = true, sha = null), store, { _, _ -> }, { false }) is ModelDownloader.Result.Failed)
        assertTrue(dl.download(model(ByteArray(4), sha = null), store, { _, _ -> }, { false }) is ModelDownloader.Result.Failed)
        assertEquals(0, opened)
    }

    @Test fun downloaderRefusesServerSizeMismatch() {
        val bytes = ByteArray(64) { 3 }
        val dl = ModelDownloader { FakeConn(it, bytes, length = 65) }
        val store = ModelStore(tmp()); val m = model(bytes)
        val r = dl.download(m, store, { _, _ -> }, { false })
        assertTrue(r.toString(), r is ModelDownloader.Result.Failed); assertFalse(store.isDownloaded(m))
    }

    // ---- controller ----

    private fun TestScope.controller(m: OnDeviceModel, store: ModelStore, runtime: FakeRuntime, net: Boolean, conn: (URL) -> HttpURLConnection,
                                     loads: MutableList<String>, done: MutableList<ExecutionLocation>) =
        OnDeviceAiController(store, { listOf(m) }, this, RuntimeLoader { _, mm -> loads += mm.id; runtime }, { net },
            downloader = ModelDownloader(conn), io = StandardTestDispatcher(testScheduler),
            onCompleted = { _, _, _, loc -> done += loc })

    @Test fun nothingRunsOrDownloadsUntilDownloadedAndConsented() = runTest {
        val bytes = ByteArray(2048) { (it * 31).toByte() }; val m = model(bytes); val store = ModelStore(tmp())
        val loads = mutableListOf<String>(); val done = mutableListOf<ExecutionLocation>(); var connections = 0
        val c = controller(m, store, FakeRuntime("hi"), net = false, conn = { connections++; FakeConn(it, bytes) }, loads, done)
        assertEquals(OnDeviceAiController.NO_MODEL, c.state.value.status)
        c.select(m.id)
        c.send("hello"); advanceUntilIdle()
        assertTrue(loads.isEmpty()); assertTrue(c.state.value.messages.isEmpty())
        assertTrue(c.state.value.status.contains("not downloaded"))

        c.requestDownload(m.id); advanceUntilIdle()
        assertEquals(OnDevicePhase.AWAITING_CONSENT, c.state.value.phase); assertEquals(0, connections)
        c.declineDownload(); advanceUntilIdle()
        assertEquals(0, connections); assertFalse(store.isDownloaded(m))

        c.requestDownload(m.id); c.confirmDownload(); advanceUntilIdle()
        assertEquals(1, connections); assertTrue(store.isDownloaded(m))
        assertTrue(c.state.value.selectedDownloaded); assertEquals(OnDevicePhase.IDLE, c.state.value.phase)
    }

    @Test fun runLabelsOfflineOnlyWithoutNetworkAndRecordsCompletion() = runTest {
        val bytes = ByteArray(16) { 1 }; val m = model(bytes); val store = ModelStore(tmp())
        store.dirFor(m).mkdirs(); store.fileFor(m).writeBytes(bytes)
        for (net in listOf(false, true)) {
            val loads = mutableListOf<String>(); val done = mutableListOf<ExecutionLocation>(); val rt = FakeRuntime("answer")
            val c = controller(m, store, rt, net, { error("no network use") }, loads, done)
            c.select(m.id); c.send("  question  "); advanceUntilIdle()
            assertEquals(listOf(m.id), loads); assertEquals(listOf("question"), rt.prompts)
            assertEquals(listOf("question", "answer"), c.state.value.messages.map { it.text })
            assertEquals(listOf(if (net) ExecutionLocation.NOT_RUN else ExecutionLocation.ON_DEVICE), done)
            assertEquals(net, c.state.value.status.contains("not claimed as offline"))
            c.send("again"); advanceUntilIdle()
            assertEquals("model loads once", 1, loads.size)
            c.delete(m.id); advanceUntilIdle()
            assertTrue(rt.closed); assertFalse(store.isDownloaded(m))
            store.dirFor(m).mkdirs(); store.fileFor(m).writeBytes(bytes)
        }
    }

    @Test fun gatedModelShowsReasonAndNeverAsksForConsent() = runTest {
        val m = model(ByteArray(4), gated = true, sha = null); val store = ModelStore(tmp())
        val c = controller(m, store, FakeRuntime("x"), false, { error("no network use") }, mutableListOf(), mutableListOf())
        c.requestDownload(m.id); advanceUntilIdle()
        assertEquals(OnDevicePhase.IDLE, c.state.value.phase); assertNull(c.state.value.consentFor)
        assertTrue(c.state.value.error!!.contains("gated"))
    }

    @Test fun loadFailureIsReportedAsNotRun() = runTest {
        val bytes = ByteArray(8); val m = model(bytes); val store = ModelStore(tmp())
        store.dirFor(m).mkdirs(); store.fileFor(m).writeBytes(bytes)
        val c = OnDeviceAiController(store, { listOf(m) }, this, RuntimeLoader { _, _ -> throw UnsatisfiedLinkError("no liblitertlm_jni") }, { false },
            io = StandardTestDispatcher(testScheduler))
        c.select(m.id); c.send("hi"); advanceUntilIdle()
        assertTrue(c.state.value.status.startsWith("Not run."))
        assertEquals(listOf("hi"), c.state.value.messages.map { it.text })
        assertEquals(OnDevicePhase.IDLE, c.state.value.phase)
    }

    // H5: the AI card must not say "On-device execution" while no model is on the phone.
    @org.junit.Test fun executionTitleNamesWhatCanActuallyRun() {
        org.junit.Assert.assertEquals("Owned-host AI · on-device not set up", com.edgeore.app.ai.ondevice.ExecutionLabel.title(emptySet(), null))
        org.junit.Assert.assertEquals("On-device model downloaded, not loaded", com.edgeore.app.ai.ondevice.ExecutionLabel.title(setOf("qwen"), null))
        org.junit.Assert.assertEquals("On-device model loaded", com.edgeore.app.ai.ondevice.ExecutionLabel.title(setOf("qwen"), "qwen"))
        org.junit.Assert.assertFalse(com.edgeore.app.ai.ondevice.ExecutionLabel.title(emptySet(), null).contains("On-device execution"))
    }
}
