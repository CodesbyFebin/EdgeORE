package com.edgeore.app.ai.ondevice

import com.edgeore.app.ChatMessage
import com.edgeore.app.ai.ExecutionLocation
import com.edgeore.app.ai.InferenceClaim
import com.edgeore.litertlm.LiteRtLmBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** A loaded in-process model. [LiteRtLmRuntime] is the real one; tests use a fake. */
interface OnDeviceRuntime : AutoCloseable {
    fun generate(system: String, prompt: String): String
    fun cancel(): Boolean
}

fun interface RuntimeLoader { fun load(file: File, model: OnDeviceModel): OnDeviceRuntime }

class LiteRtLmRuntime(file: File, private val model: OnDeviceModel) : OnDeviceRuntime {
    private val bridge = LiteRtLmBridge(file.absolutePath, model.maxTokens, null)
    override fun generate(system: String, prompt: String): String = bridge.generate(system, prompt, model.topK, model.topP, model.temperature)
    override fun cancel(): Boolean = bridge.cancel()
    override fun close() = bridge.close()
}

enum class OnDevicePhase { IDLE, AWAITING_CONSENT, DOWNLOADING, LOADING_MODEL, GENERATING }

data class OnDeviceState(
    val models: List<OnDeviceModel> = emptyList(),
    val catalogError: String? = null,
    val downloaded: Set<String> = emptySet(),
    val selectedId: String? = null,
    val phase: OnDevicePhase = OnDevicePhase.IDLE,
    val consentFor: OnDeviceModel? = null,
    val consentMetered: Boolean = false,
    val downloadingId: String? = null,
    val downloadedBytes: Long = 0,
    val loadedId: String? = null,
    val status: String = OnDeviceAiController.NO_MODEL,
    val error: String? = null,
    val freeBytes: Long = 0,
    val deviceRamBytes: Long = 0,
    val messages: List<ChatMessage> = emptyList(),
) {
    val selected: OnDeviceModel? get() = models.firstOrNull { it.id == selectedId }
    val selectedDownloaded: Boolean get() = selectedId != null && selectedId in downloaded
    val canSend: Boolean get() = selectedDownloaded && phase == OnDevicePhase.IDLE
}

/**
 * On-device LLM path, modelled on Google AI Edge Gallery (allowlisted Hugging Face model, user-started
 * download into app-private storage, in-process LiteRT-LM inference). Separate from the owned-host path:
 * prompts on this path are never sent over the network. The only network use is the weights download the
 * user confirmed, with name, size and license shown first.
 */
class OnDeviceAiController(
    private val store: ModelStore,
    catalog: () -> List<OnDeviceModel>,
    private val scope: CoroutineScope,
    private val runtimeLoader: RuntimeLoader,
    private val networkUp: () -> Boolean,
    private val metered: () -> Boolean = { false },
    private val ramBytes: () -> Long = { 0L },
    private val downloader: ModelDownloader = ModelDownloader(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val onCompleted: suspend (model: OnDeviceModel, prompt: String, answer: String, location: ExecutionLocation) -> Unit = { _, _, _, _ -> },
) {
    private val _state = MutableStateFlow(
        try { OnDeviceState(models = catalog()) } catch (e: Exception) { OnDeviceState(catalogError = "Model allowlist unreadable: ${e.message}", status = "No on-device models are offered: the allowlist failed validation.") }
    )
    val state: StateFlow<OnDeviceState> = _state.asStateFlow()

    private val runtimeLock = Mutex()
    @Volatile private var runtime: OnDeviceRuntime? = null
    private var runtimeId: String? = null
    private var downloadJob: Job? = null
    private val downloadCancelled = AtomicBoolean(false)

    init { refresh() }

    fun refresh() {
        val st = _state.value
        val present = st.models.filter { store.isDownloaded(it) }.map { it.id }.toSet()
        if (st.phase != OnDevicePhase.DOWNLOADING) st.models.forEach { m -> store.partFor(m).takeIf { it.exists() }?.delete() }
        _state.update { s ->
            s.copy(downloaded = present, freeBytes = runCatching { store.usableBytes() }.getOrDefault(0L), deviceRamBytes = runCatching { ramBytes() }.getOrDefault(0L),
                status = if (s.phase == OnDevicePhase.IDLE && s.error == null) idleStatus(s.copy(downloaded = present)) else s.status)
        }
    }

    fun select(id: String) {
        if (_state.value.models.none { it.id == id }) return
        _state.update { it.copy(selectedId = id, error = null).let { s -> if (s.phase == OnDevicePhase.IDLE) s.copy(status = idleStatus(s)) else s } }
    }

    /** First step of a download: show the consent dialog. Nothing is fetched until [confirmDownload]. */
    fun requestDownload(id: String) {
        val m = _state.value.models.firstOrNull { it.id == id } ?: return
        if (_state.value.phase != OnDevicePhase.IDLE) return
        if (!m.downloadable) { _state.update { it.copy(selectedId = id, error = gatedReason(m)) }; return }
        _state.update { it.copy(selectedId = id, phase = OnDevicePhase.AWAITING_CONSENT, consentFor = m, consentMetered = runCatching { metered() }.getOrDefault(false), error = null) }
    }

    fun declineDownload() {
        if (_state.value.phase != OnDevicePhase.AWAITING_CONSENT) return
        _state.update { it.copy(phase = OnDevicePhase.IDLE, consentFor = null, status = "Download declined. Nothing was fetched.") }
    }

    fun confirmDownload() {
        val m = _state.value.consentFor ?: return
        if (_state.value.phase != OnDevicePhase.AWAITING_CONSENT) return
        downloadCancelled.set(false)
        _state.update { it.copy(phase = OnDevicePhase.DOWNLOADING, consentFor = null, downloadingId = m.id, downloadedBytes = 0, error = null,
            status = "Downloading ${m.name} from huggingface.co (pinned commit ${m.commit.take(10)}) into app-private storage…") }
        downloadJob = scope.launch {
            val result = withContext(io) {
                downloader.download(m, store, { done, _ -> _state.update { it.copy(downloadedBytes = done) } }, { downloadCancelled.get() })
            }
            _state.update { it.copy(phase = OnDevicePhase.IDLE, downloadingId = null) }
            when (result) {
                is ModelDownloader.Result.Ok -> { refresh(); _state.update { it.copy(status = "${m.name} downloaded. Size and SHA-256 matched the allowlist. Not loaded yet; it loads on your first prompt.") } }
                is ModelDownloader.Result.Failed -> _state.update { it.copy(error = result.reason, status = "Not downloaded. ${result.reason}") }
                ModelDownloader.Result.Cancelled -> _state.update { it.copy(status = "Download cancelled. The partial file was deleted.") }
            }
            refresh()
        }
    }

    fun cancelDownload() { downloadCancelled.set(true) }

    fun delete(id: String) = scope.launch {
        val m = _state.value.models.firstOrNull { it.id == id } ?: return@launch
        if (_state.value.downloadingId == id) return@launch
        runtimeLock.withLock {
            if (runtimeId == id) { runCatching { runtime?.close() }; runtime = null; runtimeId = null; _state.update { it.copy(loadedId = null) } }
            withContext(io) { store.delete(m) }
        }
        refresh()
        _state.update { it.copy(status = "${m.name} deleted from this phone.") }
    }

    fun send(prompt: String) {
        val st = _state.value
        val m = st.selected
        val text = prompt.trim().take(MAX_PROMPT_CHARS)
        if (m == null || text.isEmpty()) return
        if (!st.selectedDownloaded) { _state.update { it.copy(error = null, status = "${m.name} is not downloaded. Download it first; nothing ran.") }; return }
        if (st.phase != OnDevicePhase.IDLE) return
        _state.update { it.copy(phase = OnDevicePhase.LOADING_MODEL, error = null, messages = it.messages + ChatMessage(true, text, System.currentTimeMillis()),
            status = if (runtimeId == m.id) "Running ${m.name} on this phone…" else "Loading ${m.name} into memory (LiteRT-LM, CPU)…") }
        scope.launch {
            try {
                val offlineBefore = !networkUp()
                val answer = runtimeLock.withLock {
                    withContext(io) {
                        if (runtimeId != m.id) {
                            runCatching { runtime?.close() }; runtime = null; runtimeId = null
                            runtime = runtimeLoader.load(store.fileFor(m), m); runtimeId = m.id
                        }
                        _state.update { it.copy(phase = OnDevicePhase.GENERATING, loadedId = m.id, status = "Generating on this phone with ${m.name}…") }
                        runtime!!.generate(SYSTEM_PROMPT, text)
                    }
                }
                val offline = offlineBefore && !networkUp()
                val location = InferenceClaim.classify(modelAnswered = answer.isNotBlank(), networkDisabled = offline, runtimeInsideApk = true)
                val label = runLabel(m, answer.isNotBlank(), offline)
                _state.update { it.copy(phase = OnDevicePhase.IDLE, status = label,
                    messages = if (answer.isNotBlank()) it.messages + ChatMessage(false, answer, System.currentTimeMillis()) else it.messages) }
                if (answer.isNotBlank()) onCompleted(m, text, answer, location)
            } catch (e: CancellationException) {
                _state.update { it.copy(phase = OnDevicePhase.IDLE, status = "Stopped.") }
                throw e
            } catch (e: Throwable) {
                // Native load failures surface as exceptions or UnsatisfiedLinkError; drop the half-loaded runtime.
                runCatching { runtime?.close() }; runtime = null; runtimeId = null
                _state.update { it.copy(phase = OnDevicePhase.IDLE, loadedId = null, error = "On-device run failed: ${e.message ?: e.javaClass.simpleName}", status = "Not run. ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    /** Asks LiteRT-LM to stop the current generation. */
    fun cancelGeneration(): Boolean {
        val stopped = runtime?.cancel() ?: false
        if (stopped) _state.update { it.copy(status = "Stop requested from the runtime.") }
        return stopped
    }

    fun clearConversation() = _state.update { it.copy(messages = emptyList()) }

    fun close() {
        downloadCancelled.set(true)
        runCatching { runtime?.close() }
        runtime = null; runtimeId = null
    }

    private fun idleStatus(s: OnDeviceState): String {
        val m = s.selected ?: return if (s.downloaded.isEmpty()) NO_MODEL else "Choose a downloaded model to chat on this phone."
        return when {
            m.id in s.downloaded -> "${m.name} is downloaded. Prompts run on this phone; nothing is sent."
            !m.downloadable -> gatedReason(m)
            else -> "${m.name} is not downloaded (${m.sizeBytes} bytes). Download it to run prompts on this phone."
        }
    }

    companion object {
        const val NO_MODEL = "No on-device model downloaded. Nothing runs on this phone until you download one."
        const val MAX_PROMPT_CHARS = 4_000
        const val SYSTEM_PROMPT = "You are a private assistant running inside the EdgeORE app on this phone. You cannot approve transactions, grant permissions or change settings."

        fun gatedReason(m: OnDeviceModel) = "${m.name} is gated on Hugging Face (${m.license}): it needs a signed-in account that accepted the terms. This build has no Hugging Face sign-in, so it cannot download it."

        fun runLabel(m: OnDeviceModel, answered: Boolean, offline: Boolean): String = when {
            !answered -> "${m.name} returned no text. Nothing to show."
            offline -> "Answered by ${m.name} inside this app (LiteRT-LM, CPU) with no active network."
            else -> "Answered by ${m.name} inside this app (LiteRT-LM, CPU). A network was up, so this run is not claimed as offline. This path sends no prompt."
        }
    }
}
