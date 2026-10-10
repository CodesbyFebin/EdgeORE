package com.edgeore.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edgeore.app.ai.EndpointPolicy
import com.edgeore.app.contribution.ContributionPolicy
import com.edgeore.app.contribution.ContributionScheduling
import com.edgeore.app.contribution.JobState
import com.edgeore.app.contribution.LastRun
import com.edgeore.app.contribution.LastRunStore
import com.edgeore.app.ai.OwnedHostModelClient
import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.device.DeviceObservations
import com.edgeore.app.device.DeviceResourcesReader
import com.edgeore.app.device.DeviceSnapshot
import com.edgeore.app.device.KeystoreReceiptSigner
import com.edgeore.app.device.NodeKeyVault
import com.edgeore.app.device.ResourceSettings
import com.edgeore.app.device.ResourceSettingsStore
import com.edgeore.app.node.NodeAgentClient
import com.edgeore.app.node.NodeAgentProtocol
import com.edgeore.app.node.NodeResult
import com.edgeore.app.receipts.Evidence
import com.edgeore.app.receipts.ReceiptDraft
import com.edgeore.app.receipts.ReceiptKind
import com.edgeore.app.receipts.ReceiptLog
import com.edgeore.app.receipts.ReceiptSigner
import com.edgeore.app.receipts.ReceiptVerifier
import com.edgeore.app.receipts.SoftwareReceiptSigner
import com.edgeore.app.receipts.StoredReceipt
import com.edgeore.app.receipts.DamagedLine
import com.edgeore.app.receipts.KeyRegistry
import com.edgeore.app.receipts.WalletEvidence
import com.edgeore.app.receipts.keyId
import com.edgeore.app.solana.OpState
import com.edgeore.app.solana.OperationStore
import com.edgeore.app.solana.OperationStoreUnavailable
import com.edgeore.app.solana.PendingOperation
import com.edgeore.app.solana.TransferCoordinator
import com.edgeore.app.storage.VaultException
import com.edgeore.app.io.BoundedInput
import com.edgeore.app.io.InputTooLargeException
import com.edgeore.app.ai.InferenceClaim
import com.edgeore.app.ai.ExecutionLocation
import com.edgeore.app.ai.ondevice.LiteRtLmRuntime
import com.edgeore.app.ai.ondevice.ModelStore
import com.edgeore.app.ai.ondevice.OnDeviceAiController
import com.edgeore.app.ai.ondevice.OnDeviceCatalog
import com.edgeore.app.ai.ondevice.OnDeviceState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.edgeore.app.solana.RpcObservation
import com.edgeore.app.solana.SolanaMessage
import com.edgeore.app.solana.SolanaRpc
import com.edgeore.app.solana.TransferReview
import com.edgeore.app.storage.BackupJobStore
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.NotConfiguredBackupProvider
import com.edgeore.app.storage.StorageAudit
import com.edgeore.app.storage.StorageController
import com.edgeore.app.storage.StorageEvent
import com.edgeore.app.wallet.WalletConnection
import com.edgeore.app.wallet.WalletCoordinator
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

class EdgeOreViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ResourceSettingsStore.prefs(app)
    private val nodePrefs = app.getSharedPreferences("edgeore.node", 0)
    private val keyRegistry = KeyRegistry(File(app.filesDir, "receipts/keys.json"))
    // Keystore first. If it is unavailable, a persisted (weaker, labelled) software key keeps the same identity across restarts.
    private val signer: ReceiptSigner = try { KeystoreReceiptSigner() } catch (_: Exception) { SoftwareReceiptSigner.persisted(File(app.filesDir, "receipts/software-signer.json")) }
    val receiptSignerProtection: String = signer.protection + " · key " + signer.keyId().take(16)
    private val log = ReceiptLog(File(app.filesDir, "receipts/receipts.jsonl"), signer, keys = keyRegistry)
    private val vault = NodeKeyVault(app)
    private val rpc = SolanaRpc()
    // Durable wallet operations. A damaged store fails closed: transfers are disabled, never reset to "nothing pending".
    private val opStore: OperationStore? = try { OperationStore(File(app.filesDir, "operations/operations.json")) } catch (_: Exception) { null }
    /** Non-null when financial actions must be refused: the store could not be read, or a write failed (latched). */
    val operationsUnavailable: String? get() = if (opStore == null) "The wallet operation store could not be read. Transfers are disabled so unknown outcomes are not hidden." else opStore.unavailableReason
    private val coordinator: TransferCoordinator? = opStore?.let { TransferCoordinator(it, rpc) }
    private val _operations = MutableStateFlow(opStore?.all().orEmpty())
    val operations: StateFlow<List<PendingOperation>> = _operations.asStateFlow()
    private val receiptMutex = Mutex()
    val wallet = WalletCoordinator()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ResourceSettings> = _settings.asStateFlow()
    private val _device = MutableStateFlow<DeviceSnapshot?>(null)
    val device: StateFlow<DeviceSnapshot?> = _device.asStateFlow()
    private val walletConnection = WalletConnection(log = { Log.w(WALLET_LOG_TAG, it) })
    private val _wallet get() = walletConnection.state
    val walletState: StateFlow<WalletState> = walletConnection.state
    private val _balance = MutableStateFlow<RpcObservation?>(null)
    val balance: StateFlow<RpcObservation?> = _balance.asStateFlow()
    private val _node = MutableStateFlow(NodeState(record = loadNode(), ops = loadOps()))
    val node: StateFlow<NodeState> = _node.asStateFlow()
    private val _ai = MutableStateFlow(AiState(
        endpoint = prefs.getString("ai.endpoint", "") ?: "",
        memoryLimitMb = prefs.getInt("ai.memoryMb", 2048),
        pauseComputeDuringChat = prefs.getBoolean("ai.pauseCompute", false),
        allocationChars = prefs.getInt("ai.allocChars", 8000),
    ))
    val ai: StateFlow<AiState> = _ai.asStateFlow()
    private val fileVault = LocalVault.forApp(app)
    private val storageAudit = StorageAudit(File(app.filesDir, "storage/audit.jsonl"))
    // No EdgeORE backend exists in this build: remote backup is honestly unavailable, never simulated.
    private val backupJobs = BackupJobStore(File(app.filesDir, "storage/backup-jobs.json"))
    private val storageController = StorageController(fileVault, backupJobs, NotConfiguredBackupProvider, storageAudit, onEvent = { recordStorage(it) })
    private val _storage = MutableStateFlow(loadStorage())
    val storage: StateFlow<StorageState> = _storage.asStateFlow()
    private val _review = MutableStateFlow(ReviewState())
    val review: StateFlow<ReviewState> = _review.asStateFlow()
    private val _receipts = MutableStateFlow<List<StoredReceipt>>(emptyList())
    val receipts: StateFlow<List<StoredReceipt>> = _receipts.asStateFlow()
    private val _receiptDamage = MutableStateFlow<List<DamagedLine>>(emptyList())
    /** Unreadable receipt lines. Shown as evidence damage, never as an empty history. */
    val receiptDamage: StateFlow<List<DamagedLine>> = _receiptDamage.asStateFlow()
    private val _verify = MutableStateFlow<VerifyOutcome?>(null)
    val verify: StateFlow<VerifyOutcome?> = _verify.asStateFlow()

    // Contribution scheduler state. Declared before init, which reads it.
    private val _contributionJob = MutableStateFlow(JobState.NONE)
    /** WorkManager's state for the unique job. NONE while the user has not opted in (WorkManager is not touched). */
    val contributionJob: StateFlow<JobState> = _contributionJob.asStateFlow()
    private val _unmetered = MutableStateFlow<Boolean?>(null)
    /** Active network has NET_CAPABILITY_NOT_METERED. Null when not observed. */
    val unmetered: StateFlow<Boolean?> = _unmetered.asStateFlow()
    private val _lastContributionRun = MutableStateFlow<LastRun?>(LastRunStore.read(prefs))
    val lastContributionRun: StateFlow<LastRun?> = _lastContributionRun.asStateFlow()
    private var jobWatch: Job? = null

    private var documentText: String? = null
    // On-device LLM (LiteRT-LM, the Google AI Edge Gallery runtime). Weights are downloaded only after consent.
    private val onDeviceAi = OnDeviceAiController(
        store = ModelStore(File(app.filesDir, "models")),
        catalog = { app.assets.open(OnDeviceCatalog.ASSET).bufferedReader().use { OnDeviceCatalog.parse(it.readText()) } },
        scope = viewModelScope,
        runtimeLoader = { file, model -> LiteRtLmRuntime(file, model) },
        networkUp = { networkUp() },
        metered = { metered() },
        ramBytes = { app.getSystemService(android.app.ActivityManager::class.java)?.let { am -> android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem } ?: 0L },
        onCompleted = { model, prompt, answer, location ->
            val offline = location == ExecutionLocation.ON_DEVICE
            record(ReceiptDraft(ReceiptKind.LOCAL_AI, if (offline) "COMPLETED_ON_DEVICE_OFFLINE" else "COMPLETED_IN_APP_NETWORK_UP", "On-device AI run",
                "Model ${model.name} (${model.repo}@${model.commit.take(10)}) via LiteRT-LM in this app. Contents not stored; digests only.", "in-app LiteRT-LM",
                digests = mapOf("promptSha256" to Sha256.hex(prompt), "responseSha256" to Sha256.hex(answer), "modelSha256" to (model.sha256 ?: "unpinned")),
                localObservation = Evidence("CHECKED", if (offline) "Runtime answered in-process; no active network before or after the run" else "Runtime answered in-process; a network was active, so offline is not claimed")))
        },
    )
    val onDevice: StateFlow<OnDeviceState> = onDeviceAi.state
    fun refreshOnDevice() = onDeviceAi.refresh()
    fun selectOnDeviceModel(id: String) = onDeviceAi.select(id)
    fun requestOnDeviceDownload(id: String) = onDeviceAi.requestDownload(id)
    fun confirmOnDeviceDownload() = onDeviceAi.confirmDownload()
    fun declineOnDeviceDownload() = onDeviceAi.declineDownload()
    fun cancelOnDeviceDownload() = onDeviceAi.cancelDownload()
    fun deleteOnDeviceModel(id: String) { onDeviceAi.delete(id) }
    fun sendOnDevicePrompt(prompt: String) = onDeviceAi.send(prompt)
    fun cancelOnDeviceGeneration() { onDeviceAi.cancelGeneration() }
    fun clearOnDeviceConversation() = onDeviceAi.clearConversation()
    override fun onCleared() { onDeviceAi.close(); super.onCleared() }

    private var aiJob: Job? = null
    @Volatile private var aiClient: OwnedHostModelClient? = null

    init {
        refreshReceipts()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val abandonedWrites = fileVault.recoverAbandonedWrites()
                if (abandonedWrites > 0) storageAudit.append("reclaimed $abandonedWrites interrupted vault write(s)")
            }
            storageController.view.collect { v ->
                _storage.update { it.copy(files = v.files.map { f -> f.entry }, views = v.files, usedBytes = v.usedBytes, backup = v.backup, busy = v.busy, auditCount = storageAudit.count(), note = v.message ?: it.note) }
            }
        }
        // Restart recovery: local state first (no network), then receipts for any transition the
        // previous process committed but did not record, then read-only chain observation.
        coordinator?.recoverAfterRestart()
        viewModelScope.launch {
            flushOperationReceipts()
            coordinator?.let { runCatching { it.reconcileAll() } }
            flushOperationReceipts()
        }
        viewModelScope.launch {
            while (true) { refreshDevice(); delay(15_000) }
        }
        // Re-register the job after a restart only if the user opted in; never touch WorkManager otherwise.
        if (ContributionPolicy.shouldSchedule(_settings.value)) applyContributionSchedule(_settings.value)
    }

    // ---------- settings ----------
    private fun loadSettings() = ResourceSettingsStore.load(prefs)

    fun updateSettings(transform: (ResourceSettings) -> ResourceSettings) {
        val old = _settings.value
        val s = transform(old).let {
            it.copy(batteryReservePercent = it.batteryReservePercent.coerceIn(5, 90), cpuLimitPercent = it.cpuLimitPercent.coerceIn(10, 100), dailyLimitLamports = it.dailyLimitLamports.coerceAtLeast(0))
        }
        ResourceSettingsStore.save(prefs, s)
        _settings.value = s
        if (ContributionPolicy.shouldSchedule(old) != ContributionPolicy.shouldSchedule(s)) applyContributionSchedule(s)
    }

    fun refreshDevice() {
        _device.value = DeviceObservations.read(getApplication())
        refreshContributionObservations()
    }

    // ---------- contribution scheduler (opt-in, WorkManager) ----------
    private fun applyContributionSchedule(s: ResourceSettings) {
        val want = ContributionPolicy.shouldSchedule(s)
        jobWatch?.cancel(); jobWatch = null
        if (!ContributionScheduling.apply(getApplication(), want)) { _contributionJob.value = JobState.UNAVAILABLE; return }
        if (!want) { _contributionJob.value = JobState.NONE; return }
        jobWatch = viewModelScope.launch {
            val flow = runCatching { androidx.work.WorkManager.getInstance(getApplication()).getWorkInfosForUniqueWorkFlow(ContributionScheduling.UNIQUE_NAME) }.getOrNull()
            if (flow == null) { _contributionJob.value = JobState.UNAVAILABLE; return@launch }
            flow.catch { _contributionJob.value = JobState.UNAVAILABLE }.collect { infos ->
                _contributionJob.value = ContributionScheduling.jobState(infos)
                _lastContributionRun.value = LastRunStore.read(prefs)
            }
        }
    }

    private fun refreshContributionObservations() {
        val cm = runCatching { getApplication<Application>().getSystemService(ConnectivityManager::class.java) }.getOrNull()
        _unmetered.value = when {
            cm == null -> null
            cm.activeNetwork == null -> false // no network at all: the unmetered constraint is not met
            else -> cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        _lastContributionRun.value = LastRunStore.read(prefs)
    }

    // ---------- receipts ----------
    private fun refreshReceipts() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val r = log.read()
            _receipts.value = r.receipts.reversed()
            _receiptDamage.value = r.damaged
        } catch (e: Exception) {
            _receiptDamage.value = listOf(DamagedLine(0, "Receipt log could not be read: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private suspend fun record(d: ReceiptDraft): StoredReceipt = withContext(Dispatchers.IO) { log.append(d) }.also { refreshReceipts() }

    /** Pre-0.2.7 submissions were tracked only as receipts without an operation id; they still count today. */
    private fun legacySpentToday(): Long {
        val today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().toString()
        return _receipts.value.filter { it.kind == "REVIEW" && it.outcome == "SUBMITTED" && it.operationId == null && it.createdAt.startsWith(today) }.sumOf { it.lamports ?: 0L }
    }

    /** Lamports counted against today's budget (UTC day) for the connected account: each operation once, plus legacy receipts. */
    fun spentTodayLamports(): Long {
        val signer = _wallet.value.address ?: return legacySpentToday()
        return (opStore?.exposure(signer, SolanaRpc.CLUSTER) ?: 0L) + legacySpentToday()
    }

    /** Writes an export JSON into cache/exports and returns the file for FileProvider sharing. */
    suspend fun exportReceipts(only: StoredReceipt? = null, hideDeviceKey: Boolean = false): File = withContext(Dispatchers.IO) {
        val text = log.export(only?.let { listOf(it) }, hideDeviceKey)
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        val name = "edgeore-receipts-" + Instant.now().toString().replace(":", "").replace(".", "") + ".json"
        File(dir, name).apply { writeText(text, Charsets.UTF_8) }
    }

    fun verifyImported(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { s -> BoundedInput.readAtMost(s, 4_000_000).toString(Charsets.UTF_8) } }.getOrNull()
        }
        if (text == null) { _verify.value = VerifyOutcome(false, "Could not read file (missing or larger than 4 MB)", emptyList()); return@launch }
        runVerification(text, "Imported file")
    }

    /** Demonstration: export the real log, change one byte of a receipt body, and verify. */
    fun runTamperTest() = viewModelScope.launch {
        val original = withContext(Dispatchers.IO) { log.export() }
        val doc = JSONObject(original)
        val arr = doc.getJSONArray("receipts")
        if (arr.length() == 0) { _verify.value = VerifyOutcome(false, "No receipts yet: create one first, then run the tamper test.", emptyList()); return@launch }
        val first = arr.getJSONObject(0)
        val body = JSONObject(first.getString("body"))
        body.put("outcome", body.getString("outcome") + "_EDITED")
        first.put("body", body.toString())
        runVerification(doc.toString(), "Tamper test (one receipt outcome edited)")
    }

    private suspend fun runVerification(text: String, label: String) {
        val local = withContext(Dispatchers.IO) { log.all() }
        val pinned = keyRegistry.all().map { java.util.Base64.getDecoder().decode(it.spkiBase64) }
        val report = ReceiptVerifier.verify(text, pinned, local)
        _verify.value = VerifyOutcome(report.accepted, "$label: ${report.summary}", report.findings)
        record(ReceiptDraft(ReceiptKind.POLICY, if (report.accepted) "EXPORT_VERIFIED" else "TAMPER_REJECTED", if (report.accepted) "Receipt file verified" else "Tampered receipt file rejected",
            "$label. " + (report.findings.firstOrNull() ?: "Digest, device signature, chain and local copy matched."), "On-device verifier",
            digests = mapOf("documentSha256" to Sha256.hex(text)),
            localObservation = Evidence("CHECKED", "SHA-256 of each body, device-key signature, chain links, bundle digest and local copies")))
    }

    fun clearVerify() { _verify.value = null }

    data class ObservationAttempt(val at: Long, val ok: Boolean, val detail: String)
    private val _observation = MutableStateFlow<ObservationAttempt?>(null)
    val observation: StateFlow<ObservationAttempt?> = _observation.asStateFlow()

    fun retryObservation(address: String?) = viewModelScope.launch {
        val at = System.currentTimeMillis()
        if (address.isNullOrBlank()) {
            _observation.value = ObservationAttempt(at, false, "No wallet address to observe. Nothing was broadcast.")
            return@launch
        }
        when (val result = SolanaRpc().balance(address)) {
            is RpcObservation.Fresh -> _observation.value = ObservationAttempt(at, true, "Devnet balance observed at slot ${result.slot}. This is not a payment and was not broadcast.")
            is RpcObservation.Unavailable -> _observation.value = ObservationAttempt(at, false, "RPC unavailable: ${result.reason}. Nothing was broadcast.")
        }
    }

    // ---------- wallet / RPC ----------
    fun connectWallet(sender: ActivityResultSender) = viewModelScope.launch {
        // The connection state is published by WalletConnection only; a balance (RPC) failure below
        // is shown on its own line and never changes an authorized connection.
        if (walletConnection.connect { wallet.connect(sender) }) _wallet.value.address?.let { refreshBalance(it) }
    }

    fun disconnectWallet(sender: ActivityResultSender) = viewModelScope.launch {
        val confirmed = wallet.disconnect(sender)
        walletConnection.disconnected(confirmed)
        _balance.value = null
    }

    fun refreshBalance(address: String? = _wallet.value.address) = viewModelScope.launch {
        if (address == null) { _balance.value = null; return@launch }
        _balance.value = rpc.balance(address)
    }

    // ---------- review route ----------
    // Every wallet action is a durable operation (OperationStore). The UI state below is derived from it.
    fun editReview(destination: String? = null, amount: String? = null) {
        abandonCurrentDraft()
        _review.update { ReviewState(destination = destination ?: it.destination, amount = amount ?: it.amount) }
    }

    fun resetReview() {
        abandonCurrentDraft()
        // Destination and amount start empty. Prefilling the user's own address made a self-transfer the default.
        _review.value = ReviewState()
    }

    /** Applies scanned QR text to the review form only. It never prepares, signs or submits anything. */
    fun applyScannedQr(raw: String?) {
        when (val r = com.edgeore.app.scan.AddressQr.parse(raw)) {
            is com.edgeore.app.scan.AddressQr.Result.Filled -> {
                editReview(destination = r.destination, amount = r.amountSol ?: _review.value.amount)
                val what = buildString {
                    append("Filled from QR: destination ${com.edgeore.app.ui.Format.short(r.destination, 6)}")
                    if (r.amountSol != null) append(" and amount ${r.amountSol} SOL")
                    r.label?.let { append(" (label \"$it\", not verified)") }
                    append(". Compare it with what the payee shows you, then prepare the review.")
                }
                _review.update { it.copy(info = what) }
            }
            is com.edgeore.app.scan.AddressQr.Result.Refused -> {
                editReview()
                _review.update { it.copy(message = "QR not used: ${r.reason}") }
            }
        }
    }

    fun scanFailed(reason: String) {
        _review.update { it.copy(message = reason, info = null) }
    }

    /** An unsigned draft that is edited or reset releases its reservation. Signed bytes are kept until observed or discarded. */
    private fun abandonCurrentDraft() {
        val id = _review.value.operationId ?: return
        val done = try {
            opStore?.transition(id, setOf(OpState.REVIEWED), OpState.ABANDONED) { it.copy(reason = "Draft edited or reset before signing") }
        } catch (e: OperationStoreUnavailable) { _review.update { it.copy(message = e.message) }; null }
        done?.let { viewModelScope.launch { flushOperationReceipts() } }
    }

    fun prepareReview() = viewModelScope.launch {
        val from = _wallet.value.publicKey ?: run { _review.update { it.copy(message = "Connect a wallet first") }; return@launch }
        val store = opStore ?: run { _review.update { it.copy(message = operationsUnavailable) }; return@launch }
        operationsUnavailable?.let { why -> _review.update { it.copy(message = why) }; return@launch }
        if (_review.value.phase == ReviewPhase.PREPARING) return@launch
        abandonCurrentDraft()
        _review.update { it.copy(phase = ReviewPhase.PREPARING, message = null, draft = null, verified = null, operationId = null, operation = null) }
        try {
            val bh = rpc.latestBlockhash()
            val signer = Base58.encode(from)
            val counted = spentTodayLamports()
            val draft = TransferReview.prepare(from, _review.value.destination, _review.value.amount, bh.blockhash, counted, _settings.value.dailyLimitLamports)
            val fee = if (draft.message.isNotEmpty()) rpc.feeForMessage(draft.message) else null
            if (!draft.approvable) {
                _review.update { it.copy(phase = ReviewPhase.REFUSED, draft = draft, feeLamports = fee, feeKnown = fee != null, message = draft.refusal) }
                return@launch
            }
            val reserved = withContext(Dispatchers.IO) {
                store.reserve(SolanaRpc.CLUSTER, signer, draft.toAddress, draft.lamports, draft.message, draft.messageSha256,
                    draft.blockhash, bh.lastValidBlockHeight, _settings.value.dailyLimitLamports, externalLamports = legacySpentToday())
            }
            when (reserved) {
                is OperationStore.ReserveResult.Refused ->
                    _review.update { it.copy(phase = ReviewPhase.REFUSED, draft = draft, feeLamports = fee, feeKnown = fee != null, message = reserved.reason) }
                is OperationStore.ReserveResult.Reserved ->
                    _review.update { it.copy(phase = ReviewPhase.READY, draft = draft, feeLamports = fee, feeKnown = fee != null, message = null, operationId = reserved.op.id, operation = reserved.op) }
            }
            flushOperationReceipts()
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _review.update { it.copy(phase = ReviewPhase.EDITING, message = "Could not fetch a devnet blockhash: ${e.message}") }
        }
    }

    fun refuseReview(reason: String = "Refused by you during review") = viewModelScope.launch {
        val id = _review.value.operationId
        if (id != null) coordinator?.recordRefused(id, reason)
        _review.update { it.copy(phase = ReviewPhase.REFUSED, message = reason) }
        flushOperationReceipts()
        id?.let(::syncReview)
    }

    fun approveAndSign(sender: ActivityResultSender) = viewModelScope.launch {
        val st = _review.value
        val d = st.draft ?: return@launch
        val id = st.operationId ?: return@launch
        val c = coordinator ?: return@launch
        val signerKey = _wallet.value.publicKey ?: return@launch
        if (!d.approvable) return@launch
        if (Base58.encode(signerKey) != d.fromAddress) {
            c.recordRefused(id, "Connected account changed after review")
            flushOperationReceipts(); syncReview(id); return@launch
        }
        // Single flight: only one tap can move REVIEWED -> SIGNING; later taps are ignored. Inside that flight the
        // block height is re-read before the wallet opens: unavailable refuses, expired needs a fresh review.
        when (val begun = c.beginSigningChecked(id)) {
            is TransferCoordinator.Outcome.Done -> Unit
            is TransferCoordinator.Outcome.Busy -> return@launch
            is TransferCoordinator.Outcome.NotAllowed -> {
                flushOperationReceipts(); syncReview(id)
                _review.update { it.copy(message = operationsUnavailable ?: begun.reason) }
                return@launch
            }
        }
        syncReview(id)
        _review.update { it.copy(message = "Waiting for wallet signature…") }
        val result = try { wallet.signTransaction(sender, SolanaMessage.unsignedTransaction(d.message)) } catch (e: CancellationException) {
            c.recordRefused(id, "Signing was interrupted; no bytes were received"); throw e
        } catch (e: Exception) { WalletCoordinator.SignResult.Refused("Wallet unavailable") }
        when (result) {
            is WalletCoordinator.SignResult.Refused -> c.recordRefused(id, "Wallet did not sign: ${result.reason}")
            is WalletCoordinator.SignResult.Signed -> when (val v = TransferReview.verifyWalletReturn(d.message, signerKey, result.signedTransaction)) {
                is TransferReview.WalletReturn.Refused ->
                    c.recordRefused(id, "Wallet bytes rejected: ${v.reason} (returned transaction SHA-256 ${Sha256.hex(result.signedTransaction)})")
                is TransferReview.WalletReturn.Verified -> {
                    c.recordSigned(id, v.signature, v.signedTransaction)
                    _review.update { it.copy(verified = v) }
                }
            }
        }
        flushOperationReceipts()
        syncReview(id)
    }

    fun submitSigned() = viewModelScope.launch {
        val id = _review.value.operationId ?: return@launch
        val c = coordinator ?: return@launch
        if (opStore?.get(id)?.state != OpState.SIGNED) return@launch
        _review.update { it.copy(phase = ReviewPhase.SUBMITTING) }
        val out = c.submit(id)
        if (out is TransferCoordinator.Outcome.Busy) return@launch
        flushOperationReceipts()
        syncReview(id)
        if (out is TransferCoordinator.Outcome.NotAllowed) _review.update { it.copy(message = out.reason) }
    }

    /** Read-only status observation by the known signature. Never resends. */
    fun checkConfirmation() { _review.value.operationId?.let(::observeOperation) }

    fun observeOperation(id: String) = viewModelScope.launch {
        val c = coordinator ?: return@launch
        c.observe(id)
        flushOperationReceipts()
        if (_review.value.operationId == id) syncReview(id)
    }

    /** Signed bytes that were never sent can be discarded; the budget reservation is released. */
    fun discardSigned(id: String) = viewModelScope.launch {
        coordinator?.discardSigned(id)
        flushOperationReceipts()
        if (_review.value.operationId == id) syncReview(id)
    }

    private fun phaseFor(op: PendingOperation): ReviewPhase = when (op.state) {
        OpState.REVIEWED -> ReviewPhase.READY
        OpState.SIGNING -> ReviewPhase.SIGNING
        OpState.SIGNED -> ReviewPhase.SIGNED
        OpState.SUBMIT_ATTEMPTED -> ReviewPhase.SUBMITTING
        OpState.SUBMITTED, OpState.CONFIRMED, OpState.FINALIZED, OpState.FAILED -> ReviewPhase.SUBMITTED
        OpState.OUTCOME_UNKNOWN -> ReviewPhase.UNKNOWN
        OpState.REFUSED, OpState.ABANDONED, OpState.EXPIRED -> ReviewPhase.REFUSED
    }

    private fun syncReview(id: String) {
        val op = opStore?.get(id) ?: return
        _review.update {
            if (it.operationId != id) it else it.copy(
                phase = phaseFor(op), operation = op,
                submittedSignature = op.signature?.takeIf { op.state.needsObservation || op.state == OpState.FINALIZED || op.state == OpState.FAILED },
                confirmation = op.lastObservation, message = opStore?.unavailableReason ?: op.reason,
            )
        }
    }

    /** Appends a receipt for every committed transition that has none yet (including after a crash). */
    private suspend fun flushOperationReceipts() {
        val store = opStore ?: return
        receiptMutex.withLock {
            withContext(Dispatchers.IO) {
                // A latched store cannot mark receipts, so appending would duplicate them on every flush.
                if (store.unavailableReason == null) for (op in store.unreceipted()) {
                    receiptFor(op)?.let { draft ->
                        val related = log.all().lastOrNull { it.operationId == op.id }?.id
                        log.append(draft.copy(relatesTo = related))
                    }
                    try { store.markReceipted(op.id, op.state) } catch (_: OperationStoreUnavailable) { break }
                }
            }
            _operations.value = store.all()
        }
        refreshReceipts()
    }

    private fun receiptFor(op: PendingOperation): ReceiptDraft? {
        val wallet = op.signature?.let { WalletEvidence(op.messageBase64, op.signer, it) }
        val digests = mapOf("messageSha256" to op.messageSha256)
        fun d(outcome: String, title: String, detail: String, source: String = "EdgeORE review route", payment: String = "NOT_OBSERVED",
              local: Evidence = Evidence("CHECKED", "Durable operation ${op.state}; exact reviewed message digest recorded"),
              provider: Evidence = Evidence.NOT_AVAILABLE, withWallet: Boolean = true) =
            ReceiptDraft(ReceiptKind.REVIEW, outcome, title, detail, source, op.cluster, operationId = op.id,
                solanaSignature = if (withWallet) op.signature else null, digests = digests, lamports = op.lamports, payment = payment,
                localObservation = local, providerAcknowledgement = provider, wallet = if (withWallet) wallet else null)
        return when (op.state) {
            OpState.REVIEWED, OpState.SIGNING, OpState.SUBMIT_ATTEMPTED -> null
            OpState.REFUSED -> d("REFUSED", "Review refused", op.reason ?: "Refused before sending", withWallet = false,
                local = Evidence("CHECKED", "Message decoded on device; no bytes were sent"))
            OpState.ABANDONED -> d("ABANDONED", "Review abandoned", op.reason ?: "Ended before sending", withWallet = false,
                local = Evidence("CHECKED", "No bytes were sent; reservation released"))
            OpState.SIGNED -> d("SIGNED_NOT_BROADCAST", "Signed after exact-message review", "Wallet signature verified over the reviewed bytes. Not broadcast.", "Mobile Wallet Adapter",
                local = Evidence("CHECKED", "Returned message bytes identical to reviewed bytes; Ed25519 signature valid for connected account"))
            OpState.SUBMITTED -> d("SUBMITTED", "Submitted to devnet", "RPC accepted the verified bytes (attempt ${op.submitAttempts}). Settlement not yet observed.", SolanaRpc.DEVNET,
                payment = "SUBMITTED_NOT_CONFIRMED", local = Evidence("CHECKED", "RPC returned the same signature as the verified wallet signature"))
            OpState.OUTCOME_UNKNOWN -> d("OUTCOME_UNKNOWN", "Submission outcome unknown", op.reason ?: "The bytes may have reached the cluster", SolanaRpc.DEVNET,
                local = Evidence("CHECKED", "Send attempt recorded before the network call; no acknowledgement observed"))
            OpState.CONFIRMED, OpState.FINALIZED -> d("STATUS_OBSERVED", "Devnet status observed", "Signature status: ${op.state}", SolanaRpc.DEVNET,
                payment = "OBSERVED_${op.state}", provider = Evidence("OBSERVED", "getSignatureStatuses from ${SolanaRpc.DEVNET}"))
            OpState.FAILED -> d("STATUS_OBSERVED", "Devnet reported failure", op.lastObservation ?: "FAILED", SolanaRpc.DEVNET,
                provider = Evidence("OBSERVED", "getSignatureStatuses from ${SolanaRpc.DEVNET} reported an error"))
            OpState.EXPIRED -> d("EXPIRED", "Blockhash expired", op.reason ?: "Never sent; blockhash expired. Reservation released.", SolanaRpc.DEVNET,
                provider = Evidence("NOT_AVAILABLE", op.lastObservation ?: "Not observed before expiry"))
        }
    }

    // ---------- node agent ----------
    private fun loadNode(): NodeRecord? {
        val s = nodePrefs.getString("record", null) ?: return null
        return runCatching {
            val o = JSONObject(s)
            NodeRecord(o.getString("endpoint"), o.getString("cert"), o.getString("fingerprint"), o.getString("session"),
                o.getJSONArray("scopes").let { a -> List(a.length()) { a.getString(it) } }, o.getString("pairedAt"), o.optBoolean("revocationPending"))
        }.getOrNull()
    }

    private fun saveNode(r: NodeRecord?) {
        if (r == null) { nodePrefs.edit().remove("record").apply(); return }
        nodePrefs.edit().putString("record", JSONObject().put("endpoint", r.endpoint).put("cert", r.certSha256).put("fingerprint", r.fingerprint)
            .put("session", r.sessionId).put("scopes", JSONArray(r.scopes)).put("pairedAt", r.pairedAt).put("revocationPending", r.revocationPending).toString()).apply()
    }

    private fun loadOps(): List<NodeOp> = runCatching {
        val a = JSONArray(nodePrefs.getString("ops", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { NodeOp(it.getString("t"), it.getString("a"), it.optString("op").ifEmpty { null }, it.optString("sha").ifEmpty { null }, it.getString("o")) } }
    }.getOrDefault(emptyList())

    private fun addOp(op: NodeOp) {
        val ops = (listOf(op) + _node.value.ops).take(200)
        nodePrefs.edit().putString("ops", JSONArray(ops.map { JSONObject().put("t", it.time).put("a", it.action).put("op", it.operationId ?: "").put("sha", it.payloadSha256 ?: "").put("o", it.outcome) }).toString()).apply()
        _node.update { it.copy(ops = ops) }
    }

    fun pairNode(endpoint: String, certSha256: String, challengeJson: String, code: String, scopes: List<String>) = viewModelScope.launch {
        _node.update { it.copy(busy = true, error = null, status = "Pairing…") }
        try {
            val challenge = NodeAgentProtocol.parseChallenge(challengeJson)
            val client = NodeAgentClient(endpoint, certSha256)
            val key = vault.createFresh()
            val r = withContext(Dispatchers.IO) { client.pair(challenge, code, scopes, key) }
            when (r) {
                is NodeResult.Ok -> {
                    val rec = NodeRecord(client.baseUrl, certSha256.trim().lowercase().replace(":", ""), challenge.fingerprint, r.value, scopes, Instant.now().toString())
                    saveNode(rec)
                    addOp(NodeOp(Instant.now().toString(), "pair", null, null, "PAIRED"))
                    record(ReceiptDraft(ReceiptKind.NODE, "PAIRED", "Node paired", "Scopes: ${scopes.joinToString()}. Session valid 24 h.", client.baseUrl,
                        digests = mapOf("nodeFingerprint" to challenge.fingerprint, "tlsCertificateSha256" to rec.certSha256),
                        localObservation = Evidence("CHECKED", "TLS certificate matched pinned SHA-256; node accepted Ed25519-signed single-use challenge")))
                    _node.value = NodeState(record = rec, ops = _node.value.ops, status = "Paired. Read health to observe the node.")
                }
                is NodeResult.Refused -> { addOp(NodeOp(Instant.now().toString(), "pair", null, null, r.code)); vault.destroy()
                    record(ReceiptDraft(ReceiptKind.NODE, "PAIRING_REFUSED", "Pairing refused", r.message, client.baseUrl, localObservation = Evidence("CHECKED", "Node returned ${r.code}")))
                    _node.update { it.copy(busy = false, error = r.message, status = null) } }
                is NodeResult.Unreachable -> { vault.destroy(); _node.update { it.copy(busy = false, error = r.message, status = null) } }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _node.update { it.copy(busy = false, error = e.message ?: "Pairing failed", status = null) }
        }
    }

    fun readHealth() = nodeCommand("observe")
    fun revokeNode() = nodeCommand("revoke")

    private fun nodeCommand(action: String) = viewModelScope.launch {
        val rec = _node.value.record ?: return@launch
        val key = vault.load() ?: run { _node.update { it.copy(error = "Node key unavailable on this device. Forget and pair again.") }; return@launch }
        _node.update { it.copy(busy = true, error = null, status = if (action == "revoke") "Revoking…" else "Reading health…") }
        val r = withContext(Dispatchers.IO) {
            try { NodeAgentClient(rec.endpoint, rec.certSha256).command(rec.sessionId, key, action) } catch (e: Exception) { NodeResult.Unreachable(e.message ?: "error") }
        }
        val now = Instant.now().toString()
        when (r) {
            is NodeResult.Ok -> {
                addOp(NodeOp(now, action, r.operationId, r.payloadSha256, "OK"))
                if (action == "observe") {
                    val fp = r.value.optString("fingerprint")
                    if (fp != rec.fingerprint) {
                        _node.update { it.copy(busy = false, error = "Node fingerprint changed; refusing observation") }
                        return@launch
                    }
                    record(ReceiptDraft(ReceiptKind.NODE, "HEALTH_READ", "Node health read", "${r.value.optString("hostOS")}/${r.value.optString("architecture")} · ${r.value.optInt("logicalCPUs")} logical CPUs", rec.endpoint, operationId = r.operationId,
                        digests = mapOf("commandPayloadSha256" to (r.payloadSha256 ?: ""), "responseSha256" to Sha256.hex(r.value.toString()), "nodeFingerprint" to rec.fingerprint),
                        localObservation = Evidence("CHECKED", "Signed observe command accepted over pinned TLS; fingerprint matched")))
                    _node.update { it.copy(busy = false, health = r.value, healthObservedAt = System.currentTimeMillis(), status = "Health read") }
                } else {
                    record(ReceiptDraft(ReceiptKind.NODE, "REVOKED", "Node access revoked", r.value.optString("state"), rec.endpoint, operationId = r.operationId,
                        digests = mapOf("commandPayloadSha256" to (r.payloadSha256 ?: ""), "nodeFingerprint" to rec.fingerprint),
                        localObservation = Evidence("CHECKED", "Node acknowledged signed revoke for this session; local key destroyed")))
                    vault.destroy(); saveNode(null)
                    _node.value = NodeState(ops = _node.value.ops, status = "Access revoked. Local pairing key destroyed.")
                }
            }
            is NodeResult.Refused -> {
                addOp(NodeOp(now, action, r.operationId, null, r.code))
                if (action == "revoke" && r.code == "SESSION_REVOKED_OR_UNKNOWN") { vault.destroy(); saveNode(null); _node.value = NodeState(ops = _node.value.ops, status = "Session already revoked on node. Local key destroyed."); return@launch }
                _node.update { it.copy(busy = false, error = r.message, status = null) }
            }
            is NodeResult.Unreachable -> {
                addOp(NodeOp(now, action, null, null, "UNREACHABLE"))
                if (action == "revoke") { val p = rec.copy(revocationPending = true); saveNode(p); _node.update { it.copy(record = p) } }
                _node.update { it.copy(busy = false, error = r.message + if (action == "revoke") " · Revocation pending" else " · showing last known data", status = null) }
            }
        }
    }

    /** Forget locally without node acknowledgement; the node session remains valid until it expires. */
    fun forgetNode() = viewModelScope.launch {
        val rec = _node.value.record ?: return@launch
        vault.destroy(); saveNode(null)
        addOp(NodeOp(Instant.now().toString(), "forget", null, null, "LOCAL_ONLY"))
        record(ReceiptDraft(ReceiptKind.NODE, "FORGOTTEN_LOCALLY", "Node forgotten locally", "Node did not acknowledge revocation; its session stays valid until expiry (≤24 h) or owner SIGUSR1.", rec.endpoint,
            localObservation = Evidence("CHECKED", "Local key destroyed; no node acknowledgement")))
        _node.value = NodeState(ops = _node.value.ops, status = "Forgotten locally. Revoke on the host with SIGUSR1 to be sure.")
    }

    // ---------- AI ----------
    fun setAiEndpoint(endpoint: String) = viewModelScope.launch {
        prefs.edit().putString("ai.endpoint", endpoint).apply()
        _ai.update { it.copy(endpoint = endpoint, status = AiStatus.CHECKING, statusDetail = "Checking endpoint…", models = emptyList(), selectedModel = null, location = null) }
        val decision = withContext(Dispatchers.IO) { EndpointPolicy.check(endpoint) }
        when (decision) {
            is EndpointPolicy.Decision.Refused -> _ai.update { it.copy(status = AiStatus.NO_ENDPOINT, statusDetail = decision.reason) }
            is EndpointPolicy.Decision.Allowed -> try {
                val models = OwnedHostModelClient(decision, timeoutMs = 15_000).listModels()
                _ai.update { it.copy(location = decision.location, models = models, status = AiStatus.NO_MODEL,
                    statusDetail = if (models.isEmpty()) "Host reachable but reports no installed models" else "${models.size} model(s) on your host. Choose one explicitly.") }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _ai.update { it.copy(status = AiStatus.NO_ENDPOINT, statusDetail = "Host unreachable: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun selectModel(name: String) = _ai.update { if (name in it.models) it.copy(selectedModel = name, status = AiStatus.READY, statusDetail = "Prompts go to $name on ${it.location}. This phone does not run the weights.") else it }

    fun inspectGallery(kind: String) {
        val models = _ai.value.models
        val note = if (models.isEmpty()) "$kind: no model is installed on a connected host, and none is bundled in this app."
        else "$kind: the host listed ${models.size} name(s). That is not a license, size, or device-fit check."
        _ai.update { it.copy(galleryNote = note) }
    }

    fun verifyChecksum(uri: Uri, expected: String) = viewModelScope.launch {
        val hex = withContext(Dispatchers.IO) {
            // Streamed: a multi-GB model file is hashed without loading it into memory.
            runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { BoundedInput.sha256Hex(it) } }.getOrNull()
        }
        if (hex == null) { _ai.update { it.copy(checksumResult = "Could not read that file.") }; return@launch }
        val want = expected.trim().lowercase()
        val result = when {
            want.isEmpty() -> "SHA-256 $hex. No expected digest was entered, so nothing was matched."
            want == hex -> "SHA-256 matches the digest you entered."
            else -> "SHA-256 does not match. File is $hex."
        }
        _ai.update { it.copy(checksumResult = result) }
    }

    fun setMemoryLimit(mb: Int) {
        val clamped = mb.coerceIn(256, 8192)
        prefs.edit().putInt("ai.memoryMb", clamped).apply()
        _ai.update { it.copy(memoryLimitMb = clamped) }
    }

    fun setPauseComputeDuringChat(on: Boolean) {
        prefs.edit().putBoolean("ai.pauseCompute", on).apply()
        _ai.update { it.copy(pauseComputeDuringChat = on) }
    }

    fun setAllocationChars(chars: Int) {
        val clamped = chars.coerceIn(1000, 32000)
        prefs.edit().putInt("ai.allocChars", clamped).apply()
        _ai.update { it.copy(allocationChars = clamped) }
    }

    fun runAirplaneCheck() {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val result = if (online) "A network is up. No prompt was sent. This is not an offline inference result."
        else "No active network. No prompt was sent. This app does not contain model weights, so offline chat is still unavailable."
        _ai.update { it.copy(airplaneResult = result) }
    }

    fun attachDocument(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "document"
                // Byte limit while reading, strict UTF-8, then a separate character limit.
                name to (app.contentResolver.openInputStream(uri)?.use { s ->
                    BoundedInput.readUtf8Text(s, OwnedHostModelClient.MAX_DOCUMENT_BYTES, OwnedHostModelClient.MAX_DOCUMENT_CHARS)
                } ?: error("unreadable"))
            }.getOrNull()
        }
        if (result == null) { _ai.update { it.copy(statusDetail = "Could not read that document") }; return@launch }
        val (name, read) = result
        when (read) {
            is BoundedInput.TextResult.Refused -> _ai.update { it.copy(statusDetail = "Document not attached: ${read.reason}") }
            is BoundedInput.TextResult.Ok -> {
                documentText = read.text
                _ai.update { it.copy(documentName = name, documentSha256 = Sha256.hex(read.bytes), documentChars = read.text.length, documentTruncated = read.truncatedChars) }
            }
        }
    }

    fun detachDocument() { documentText = null; _ai.update { it.copy(documentName = null, documentSha256 = null, documentChars = 0, documentTruncated = false) } }

    fun sendPrompt(prompt: String) {
        val st = _ai.value
        val model = st.selectedModel ?: return
        if (prompt.isBlank() || st.status == AiStatus.LOADING) return
        if (st.pauseComputeDuringChat) updateSettings { it.copy(edgeModeResumed = false) }
        val doc = documentText?.take(st.allocationChars)
        val userContent = if (doc != null) "Private document \"${st.documentName}\":\n<<<\n$doc\n>>>\n\nQuestion: $prompt" else prompt
        _ai.update { it.copy(status = AiStatus.LOADING, statusDetail = "Checking endpoint, then running on ${it.location}…", messages = it.messages + ChatMessage(true, prompt, System.currentTimeMillis())) }
        aiJob = viewModelScope.launch {
            try {
                // Re-validated off the main thread at send time; the request goes to the validated address.
                val decision = withContext(Dispatchers.IO) { EndpointPolicy.check(st.endpoint) }
                if (decision !is EndpointPolicy.Decision.Allowed) {
                    _ai.update { it.copy(status = AiStatus.FAILED, statusDetail = "Endpoint refused at send time: ${(decision as EndpointPolicy.Decision.Refused).reason}. Nothing was sent.") }
                    return@launch
                }
                val client = OwnedHostModelClient(decision)
                aiClient = client
                val answer = runInterruptibleChat(client, model, userContent)
                _ai.update { it.copy(status = AiStatus.COMPLETED, statusDetail = "Completed on ${it.location}", messages = it.messages + ChatMessage(false, answer, System.currentTimeMillis())) }
                record(ReceiptDraft(ReceiptKind.LOCAL_AI, "COMPLETED_ON_OWNED_HOST", "Private AI run", "Model $model on ${st.location}. Contents not stored; digests only.", decision.url,
                    digests = buildMap { put("promptSha256", Sha256.hex(userContent)); put("responseSha256", Sha256.hex(answer)); st.documentSha256?.let { put("documentSha256", it) } },
                    localObservation = Evidence("CHECKED", "Response received from owned host endpoint; this is not independent verification")))
            } catch (e: CancellationException) {
                if (_ai.value.status == AiStatus.LOADING) _ai.update { it.copy(status = AiStatus.CANCELED) }
            } catch (e: Exception) {
                if (aiClient?.cancelled == true) return@launch
                _ai.update { it.copy(status = AiStatus.FAILED, statusDetail = "Failed: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                aiClient = null
            }
        }
    }

    private suspend fun runInterruptibleChat(client: OwnedHostModelClient, model: String, userContent: String): String =
        withContext(Dispatchers.IO) { client.chatBlocking(model, OwnedHostModelClient.SYSTEM_PROMPT, userContent) }

    /** Closes the socket so this phone stops waiting at once. The host is not asked to stop and does not acknowledge. */
    fun cancelPrompt() {
        val closed = aiClient?.cancelActive() ?: false
        aiJob?.cancel()
        _ai.update {
            it.copy(status = AiStatus.CANCELED, statusDetail = InferenceClaim.cancellationLabel(clientClosed = closed, hostAcknowledgedStop = false) +
                " The host may keep generating; no stop acknowledgement exists in this API. Any late result is discarded.")
        }
    }
    fun clearConversation() = _ai.update { it.copy(messages = emptyList(), status = if (it.selectedModel != null) AiStatus.READY else it.status) }

    private fun loadStorage() = StorageState(
        allocationMb = prefs.getInt("store.allocMb", 0),
        sharingConsent = prefs.getBoolean("store.consent", false),
        quotaMb = prefs.getInt("store.quotaMb", 500),
        pauseOnMetered = prefs.getBoolean("store.metered", true),
        blockOnDisconnect = prefs.getBoolean("store.kill", false),
        provider = prefs.getString("store.provider", "") ?: "",
        auditCount = storageAudit.count(),
    )

    fun refreshStorage() {
        viewModelScope.launch {
            val device = withContext(Dispatchers.IO) { runCatching { DeviceResourcesReader.read(getApplication()) }.getOrNull() }
            _storage.update { it.copy(device = device ?: it.device, deviceReadFailed = device == null) }
            storageController.refresh()
        }
    }

    fun setAllocationMb(mb: Int) {
        val v = mb.coerceIn(0, 8192)
        prefs.edit().putInt("store.allocMb", v).apply()
        _storage.update { it.copy(allocationMb = v) }
        storageAudit.append("allocation set to $v MB")
        refreshStorage()
    }

    /** Storage Access Framework import. Reading, the byte limit and encryption run off the main thread. */
    fun importVault(uri: Uri) = viewModelScope.launch {
        val resolver = getApplication<Application>().contentResolver
        val name = withContext(Dispatchers.IO) {
            runCatching { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull()
        }
        storageController.import(name, _storage.value.allocationMb * 1024L * 1024L) { resolver.openInputStream(uri) }
    }

    fun deleteVault(id: String) = viewModelScope.launch { storageController.deleteLocal(id) }

    /** Decrypts off the main thread and writes to the chosen document after the user confirmed the warning. */
    fun exportVault(id: String, uri: Uri) = viewModelScope.launch {
        storageController.exportDecrypted(id) { getApplication<Application>().contentResolver.openOutputStream(uri) }
    }

    /** App-record integrity evidence for a storage action: identifiers and encrypted-object digests only. */
    private suspend fun recordStorage(e: StorageEvent) {
        runCatching {
            record(ReceiptDraft(ReceiptKind.STORAGE, e.action, e.action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                e.observation + ". App-record integrity evidence; not proof of provider storage or physical deletion.", "EdgeORE local vault",
                operationId = e.operationId,
                digests = buildMap { e.encryptedSha256?.let { put("encryptedObjectSha256", it) }; e.objectId?.let { put("vaultObjectId", it) } },
                localObservation = Evidence("OBSERVED", e.observation),
                providerAcknowledgement = e.providerObservation?.let { Evidence("OBSERVED", it) } ?: Evidence.NOT_AVAILABLE))
        }
    }

    fun setSharingConsent(on: Boolean) {
        if (on && _storage.value.pauseOnMetered && metered()) {
            _storage.update { it.copy(note = "Sharing stayed off. This network is metered.") }
            return
        }
        prefs.edit().putBoolean("store.consent", on).apply()
        storageAudit.append(if (on) "sharing consent on" else "sharing stopped")
        refreshStorage()
        _storage.update {
            it.copy(sharingConsent = on, note = if (on) "Consent recorded. No sharing protocol is running, so no bytes were sent." else "Sharing is off. Nothing is being sent.")
        }
    }

    fun setQuotaMb(mb: Int) {
        val v = mb.coerceIn(1, 10000)
        prefs.edit().putInt("store.quotaMb", v).apply()
        _storage.update { it.copy(quotaMb = v, note = "Daily quota stored as $v MB. Usage is not metered in this build.") }
    }

    fun setPauseOnMetered(on: Boolean) {
        prefs.edit().putBoolean("store.metered", on).apply()
        _storage.update { it.copy(pauseOnMetered = on) }
    }

    fun setBlockOnDisconnect(on: Boolean) {
        prefs.edit().putBoolean("store.kill", on).apply()
        _storage.update { it.copy(blockOnDisconnect = on, note = if (on) "Kill switch preference saved. No VPN tunnel exists, so traffic is not being blocked." else "Kill switch preference off. No tunnel is running.") }
    }

    fun verifyStorageAudit() {
        val ok = storageAudit.verify()
        _storage.update { it.copy(auditOk = ok, note = if (ok) "Audit chain matches." else "Audit chain does not match.") }
    }

    fun storageAuditText(): String = storageAudit.text()

    fun restoreStorageDefaults() {
        prefs.edit().putBoolean("store.consent", false).putInt("store.quotaMb", 500).putBoolean("store.metered", true).putBoolean("store.kill", false).putString("store.provider", "").apply()
        storageAudit.append("secure defaults restored")
        _storage.update { loadStorage().copy(files = it.files, views = it.views, usedBytes = it.usedBytes, backup = it.backup, device = it.device, note = "Consent, quota and kill switch reset. Vault files were kept.") }
    }

    private fun networkUp(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun metered(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return online && !unmetered
    }
}
