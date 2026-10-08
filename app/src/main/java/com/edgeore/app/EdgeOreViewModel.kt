package com.edgeore.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edgeore.app.ai.EndpointPolicy
import com.edgeore.app.ai.OwnedHostModelClient
import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.device.DeviceObservations
import com.edgeore.app.device.DeviceResources
import com.edgeore.app.device.DeviceResourcesReader
import com.edgeore.app.device.DeviceSnapshot
import com.edgeore.app.device.KeystoreReceiptSigner
import com.edgeore.app.device.NodeKeyVault
import com.edgeore.app.device.ResourceSettings
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
import com.edgeore.app.solana.RpcObservation
import com.edgeore.app.solana.SpendLedger
import com.edgeore.app.solana.SolanaMessage
import com.edgeore.app.solana.SolanaRpc
import com.edgeore.app.solana.TransferReview
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.StorageAudit
import com.edgeore.app.wallet.WalletCoordinator
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

data class WalletState(val publicKey: ByteArray? = null, val label: String? = null, val status: String = "Not connected", val busy: Boolean = false) {
    val address: String? get() = publicKey?.let(Base58::encode)
}

data class NodeRecord(
    val endpoint: String, val certSha256: String, val fingerprint: String, val sessionId: String,
    val scopes: List<String>, val pairedAt: String, val revocationPending: Boolean = false,
)

data class NodeOp(val time: String, val action: String, val operationId: String?, val payloadSha256: String?, val outcome: String)

data class NodeState(
    val record: NodeRecord? = null,
    val health: JSONObject? = null,
    val healthObservedAt: Long? = null,
    val status: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val ops: List<NodeOp> = emptyList(),
)

enum class AiStatus { NO_ENDPOINT, CHECKING, NO_MODEL, READY, LOADING, COMPLETED, CANCELED, FAILED }
data class ChatMessage(val fromUser: Boolean, val text: String, val time: Long)
data class AiState(
    val endpoint: String = "",
    val location: String? = null,
    val models: List<String> = emptyList(),
    val selectedModel: String? = null,
    val status: AiStatus = AiStatus.NO_ENDPOINT,
    val statusDetail: String = "No local model installed. On-device inference is not bundled in this build.",
    val messages: List<ChatMessage> = emptyList(),
    val documentName: String? = null,
    val documentSha256: String? = null,
    val documentChars: Int = 0,
    val documentTruncated: Boolean = false,
    val memoryLimitMb: Int = 2048,
    val pauseComputeDuringChat: Boolean = false,
    val allocationChars: Int = 8000,
    val galleryNote: String? = null,
    val checksumResult: String? = null,
    val airplaneResult: String? = null,
)

enum class ReviewPhase { EDITING, PREPARING, READY, SIGNING, SIGNED, SUBMITTING, SUBMITTED, REFUSED }
data class ReviewState(
    val destination: String = "",
    val amount: String = "0.001",
    val phase: ReviewPhase = ReviewPhase.EDITING,
    val draft: TransferReview.Draft? = null,
    val feeLamports: Long? = null,
    val feeKnown: Boolean = false,
    val verified: TransferReview.WalletReturn.Verified? = null,
    val submittedSignature: String? = null,
    val confirmation: String? = null,
    val message: String? = null,
    val operationId: String? = null,
)

data class VerifyOutcome(val accepted: Boolean, val summary: String, val findings: List<String>)

data class StorageState(
    val files: List<String> = emptyList(),
    val usedBytes: Long = 0,
    val allocationMb: Int = 0,
    val sharingConsent: Boolean = false,
    val quotaMb: Int = 500,
    val pauseOnMetered: Boolean = true,
    val blockOnDisconnect: Boolean = false,
    val provider: String = "",
    val note: String = "Vault is empty until you encrypt a file.",
    val auditCount: Int = 0,
    val auditOk: Boolean? = null,
    val device: DeviceResources? = null,
)

class EdgeOreViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("edgeore.settings", 0)
    private val nodePrefs = app.getSharedPreferences("edgeore.node", 0)
    private val signer: ReceiptSigner = try { KeystoreReceiptSigner() } catch (_: Exception) { SoftwareReceiptSigner() }
    val receiptSignerProtection: String = if (signer is KeystoreReceiptSigner) "Android Keystore P-256" else "Software key (Keystore unavailable)"
    private val log = ReceiptLog(File(app.filesDir, "receipts/receipts.jsonl"), signer)
    private val vault = NodeKeyVault(app)
    private val rpc = SolanaRpc()
    private val ledger = SpendLedger()
    val wallet = WalletCoordinator()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ResourceSettings> = _settings.asStateFlow()
    private val _device = MutableStateFlow<DeviceSnapshot?>(null)
    val device: StateFlow<DeviceSnapshot?> = _device.asStateFlow()
    private val _wallet = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _wallet.asStateFlow()
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
    private val fileVault = LocalVault(app)
    private val storageAudit = StorageAudit(File(app.filesDir, "storage/audit.jsonl"))
    private val _storage = MutableStateFlow(loadStorage())
    val storage: StateFlow<StorageState> = _storage.asStateFlow()
    private val _review = MutableStateFlow(ReviewState())
    val review: StateFlow<ReviewState> = _review.asStateFlow()
    private val _receipts = MutableStateFlow<List<StoredReceipt>>(emptyList())
    val receipts: StateFlow<List<StoredReceipt>> = _receipts.asStateFlow()
    private val _verify = MutableStateFlow<VerifyOutcome?>(null)
    val verify: StateFlow<VerifyOutcome?> = _verify.asStateFlow()

    private var documentText: String? = null
    private var aiJob: Job? = null

    init {
        refreshReceipts()
        viewModelScope.launch {
            while (true) { _device.value = DeviceObservations.read(getApplication()); delay(15_000) }
        }
    }

    // ---------- settings ----------
    private fun loadSettings() = ResourceSettings(
        edgeModeResumed = prefs.getBoolean("edge.resumed", false),
        chargeOnly = prefs.getBoolean("ctl.chargeOnly", true),
        thermalGuard = prefs.getBoolean("ctl.thermal", true),
        batteryReservePercent = prefs.getInt("ctl.reserve", 20),
        cpuLimitPercent = prefs.getInt("ctl.cpu", 50),
        dailyLimitLamports = prefs.getLong("budget.daily", 50_000_000L),
    )

    fun updateSettings(transform: (ResourceSettings) -> ResourceSettings) {
        val s = transform(_settings.value).let {
            it.copy(batteryReservePercent = it.batteryReservePercent.coerceIn(5, 90), cpuLimitPercent = it.cpuLimitPercent.coerceIn(10, 100), dailyLimitLamports = it.dailyLimitLamports.coerceAtLeast(0))
        }
        prefs.edit().putBoolean("edge.resumed", s.edgeModeResumed).putBoolean("ctl.chargeOnly", s.chargeOnly)
            .putBoolean("ctl.thermal", s.thermalGuard).putInt("ctl.reserve", s.batteryReservePercent)
            .putInt("ctl.cpu", s.cpuLimitPercent).putLong("budget.daily", s.dailyLimitLamports).apply()
        _settings.value = s
    }

    fun refreshDevice() { _device.value = DeviceObservations.read(getApplication()) }

    // ---------- receipts ----------
    private fun refreshReceipts() = viewModelScope.launch(Dispatchers.IO) { _receipts.value = runCatching { log.all() }.getOrDefault(emptyList()).reversed() }

    private suspend fun record(d: ReceiptDraft): StoredReceipt = withContext(Dispatchers.IO) { log.append(d) }.also { refreshReceipts() }

    fun spentTodayLamports(): Long {
        val today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().toString()
        return _receipts.value.filter { it.kind == "REVIEW" && it.outcome == "SUBMITTED" && it.createdAt.startsWith(today) }.sumOf { it.lamports ?: 0L }
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
            runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { s -> s.readBytes().takeIf { it.size <= 4_000_000 }?.toString(Charsets.UTF_8) } }.getOrNull()
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
        val report = ReceiptVerifier.verify(text, signer.publicKeySpki(), local)
        _verify.value = VerifyOutcome(report.accepted, if (report.accepted) "$label: accepted · ${report.verifiedReceipts} receipt(s) verified" else "$label: REJECTED", report.findings)
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
        _wallet.update { it.copy(busy = true, status = "Waiting for wallet…") }
        _wallet.value = try {
            when (val r = wallet.connect(sender)) {
                is WalletCoordinator.ConnectResult.Authorized -> WalletState(r.publicKey, r.label, "Authorized on devnet")
                is WalletCoordinator.ConnectResult.Refused -> WalletState(status = r.reason)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { WalletState(status = "Wallet unavailable") }
        _wallet.value.address?.let { refreshBalance(it) }
    }

    fun disconnectWallet(sender: ActivityResultSender) = viewModelScope.launch {
        runCatching { wallet.disconnect(sender) }
        _wallet.value = WalletState(status = "Disconnected")
        _balance.value = null
    }

    fun refreshBalance(address: String? = _wallet.value.address) = viewModelScope.launch {
        if (address == null) { _balance.value = null; return@launch }
        _balance.value = rpc.balance(address)
    }

    // ---------- review route ----------
    fun editReview(destination: String? = null, amount: String? = null) = _review.update {
        ReviewState(destination = destination ?: it.destination, amount = amount ?: it.amount)
    }

    fun resetReview() { _review.value = ReviewState(destination = _wallet.value.address ?: "") }

    fun prepareReview() = viewModelScope.launch {
        val from = _wallet.value.publicKey ?: run { _review.update { it.copy(message = "Connect a wallet first") }; return@launch }
        _review.update { it.copy(phase = ReviewPhase.PREPARING, message = null, draft = null, verified = null) }
        try {
            val bh = rpc.latestBlockhash()
            val draft = TransferReview.prepare(from, _review.value.destination, _review.value.amount, bh.blockhash, spentTodayLamports(), _settings.value.dailyLimitLamports)
            val fee = if (draft.message.isNotEmpty()) rpc.feeForMessage(draft.message) else null
            if (!draft.approvable) {
                _review.update { it.copy(phase = ReviewPhase.REFUSED, draft = draft, feeLamports = fee, feeKnown = fee != null, message = draft.refusal, operationId = null) }
                return@launch
            }
            val operationId = draft.messageSha256
            val reserved = ledger.reserve(operationId, draft.lamports, SolanaRpc.CLUSTER, draft.fromAddress, draft.messageSha256, spentTodayLamports(), _settings.value.dailyLimitLamports)
            if (reserved is SpendLedger.ReserveResult.Refused) {
                _review.update { it.copy(phase = ReviewPhase.REFUSED, draft = draft, feeLamports = fee, feeKnown = fee != null, message = reserved.reason, operationId = null) }
                return@launch
            }
            _review.update { it.copy(phase = ReviewPhase.READY, draft = draft, feeLamports = fee, feeKnown = fee != null, message = null, operationId = operationId) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _review.update { it.copy(phase = ReviewPhase.EDITING, message = "Could not fetch a devnet blockhash: ${e.message}") }
        }
    }

    fun refuseReview(reason: String = "Refused by you during review") = viewModelScope.launch {
        val d = _review.value.draft
        _review.value.operationId?.let { ledger.release(it) }
        record(ReceiptDraft(ReceiptKind.REVIEW, "REFUSED", "Review refused", reason, "EdgeORE review route", SolanaRpc.CLUSTER,
            digests = d?.messageSha256?.takeIf { it.isNotEmpty() }?.let { mapOf("messageSha256" to it) } ?: emptyMap(),
            localObservation = Evidence("CHECKED", "Message decoded on device; no signature requested"), lamports = d?.lamports))
        _review.update { it.copy(phase = ReviewPhase.REFUSED, message = reason) }
    }

    fun approveAndSign(sender: ActivityResultSender) = viewModelScope.launch {
        val st = _review.value
        val d = st.draft ?: return@launch
        val signerKey = _wallet.value.publicKey ?: return@launch
        if (!d.approvable || st.phase != ReviewPhase.READY) return@launch
        _review.update { it.copy(phase = ReviewPhase.SIGNING, message = "Waiting for wallet signature…") }
        val result = try { wallet.signTransaction(sender, SolanaMessage.unsignedTransaction(d.message)) } catch (e: CancellationException) { throw e } catch (e: Exception) { WalletCoordinator.SignResult.Refused("Wallet unavailable") }
        when (result) {
            is WalletCoordinator.SignResult.Refused -> {
                _review.value.operationId?.let { ledger.release(it) }
                record(ReceiptDraft(ReceiptKind.REVIEW, "REFUSED", "Wallet did not sign", result.reason, "Mobile Wallet Adapter", SolanaRpc.CLUSTER, digests = mapOf("messageSha256" to d.messageSha256), lamports = d.lamports,
                    localObservation = Evidence("CHECKED", "Reviewed message digest recorded; wallet returned no signature")))
                _review.update { it.copy(phase = ReviewPhase.REFUSED, message = result.reason) }
            }
            is WalletCoordinator.SignResult.Signed -> when (val v = TransferReview.verifyWalletReturn(d.message, signerKey, result.signedTransaction)) {
                is TransferReview.WalletReturn.Refused -> {
                    record(ReceiptDraft(ReceiptKind.REVIEW, "REFUSED", "Wallet bytes rejected", v.reason, "Exact-message policy", SolanaRpc.CLUSTER,
                        digests = mapOf("messageSha256" to d.messageSha256, "returnedTransactionSha256" to Sha256.hex(result.signedTransaction)), lamports = d.lamports,
                        localObservation = Evidence("CHECKED", "Compared wallet-returned message bytes with reviewed bytes and verified Ed25519 signature")))
                    _review.update { it.copy(phase = ReviewPhase.REFUSED, message = v.reason) }
                }
                is TransferReview.WalletReturn.Verified -> {
                    record(ReceiptDraft(ReceiptKind.REVIEW, "SIGNED_NOT_BROADCAST", "Signed after exact-message review", "Wallet signature verified over the reviewed bytes. Not broadcast.", "Mobile Wallet Adapter", SolanaRpc.CLUSTER,
                        solanaSignature = v.signature, digests = mapOf("messageSha256" to d.messageSha256), lamports = d.lamports,
                        localObservation = Evidence("CHECKED", "Returned message bytes identical to reviewed bytes; Ed25519 signature valid for connected account")))
                    _review.update { it.copy(phase = ReviewPhase.SIGNED, verified = v, message = null) }
                }
            }
        }
    }

    fun submitSigned() = viewModelScope.launch {
        val st = _review.value
        val v = st.verified ?: return@launch
        val d = st.draft ?: return@launch
        _review.update { it.copy(phase = ReviewPhase.SUBMITTING) }
        try {
            val sig = rpc.sendTransaction(v.signedTransaction)
            if (sig != v.signature) throw IllegalStateException("RPC reported a different signature")
            _review.value.operationId?.let { ledger.markUnknown(it) }
            record(ReceiptDraft(ReceiptKind.REVIEW, "SUBMITTED", "Submitted to devnet", "RPC accepted the verified bytes. Settlement not yet observed.", SolanaRpc.DEVNET, SolanaRpc.CLUSTER,
                solanaSignature = sig, digests = mapOf("messageSha256" to d.messageSha256), lamports = d.lamports, payment = "SUBMITTED_NOT_CONFIRMED",
                localObservation = Evidence("CHECKED", "RPC returned the same signature as the verified wallet signature")))
            _review.update { it.copy(phase = ReviewPhase.SUBMITTED, submittedSignature = sig) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _review.value.operationId?.let { ledger.markUnknown(it) }
            _review.update { it.copy(phase = ReviewPhase.SIGNED, message = "Submission failed: ${e.message}. Budget reservation kept until the outcome is known.") }
        }
    }

    fun checkConfirmation() = viewModelScope.launch {
        val sig = _review.value.submittedSignature ?: return@launch
        val status = try { rpc.signatureStatus(sig) } catch (e: CancellationException) { throw e } catch (e: Exception) { "UNAVAILABLE: ${e.message}" }
        _review.update { it.copy(confirmation = status) }
        if (status == "CONFIRMED" || status == "FINALIZED") _review.value.operationId?.let { ledger.settle(it) }
        val related = _receipts.value.firstOrNull { it.solanaSignature == sig && it.outcome == "SUBMITTED" }?.id
        record(ReceiptDraft(ReceiptKind.REVIEW, "STATUS_OBSERVED", "Devnet status observed", "Signature status: $status", SolanaRpc.DEVNET, SolanaRpc.CLUSTER, solanaSignature = sig, relatesTo = related,
            payment = if (status == "CONFIRMED" || status == "FINALIZED") "OBSERVED_$status" else "NOT_OBSERVED",
            providerAcknowledgement = Evidence(if (status == "CONFIRMED" || status == "FINALIZED") "OBSERVED" else "NOT_AVAILABLE", "getSignatureStatuses from ${SolanaRpc.DEVNET}")))
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
                val models = OwnedHostModelClient(decision.url).listModels()
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
            runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { Sha256.hex(it.readBytes()) } }.getOrNull()
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
                val bytes = app.contentResolver.openInputStream(uri)?.use { s -> s.readBytes() } ?: error("unreadable")
                Triple(name, bytes, String(bytes, Charsets.UTF_8))
            }.getOrNull()
        }
        if (result == null) { _ai.update { it.copy(statusDetail = "Could not read that document") }; return@launch }
        val (name, bytes, text) = result
        val truncated = text.length > OwnedHostModelClient.MAX_DOCUMENT_CHARS
        documentText = text.take(OwnedHostModelClient.MAX_DOCUMENT_CHARS)
        _ai.update { it.copy(documentName = name, documentSha256 = Sha256.hex(bytes), documentChars = documentText!!.length, documentTruncated = truncated) }
    }

    fun detachDocument() { documentText = null; _ai.update { it.copy(documentName = null, documentSha256 = null, documentChars = 0, documentTruncated = false) } }

    fun sendPrompt(prompt: String) {
        val st = _ai.value
        val model = st.selectedModel ?: return
        if (prompt.isBlank() || st.status == AiStatus.LOADING) return
        val decision = EndpointPolicy.check(st.endpoint).let { it as? EndpointPolicy.Decision.Allowed } ?: return
        if (st.pauseComputeDuringChat) updateSettings { it.copy(edgeModeResumed = false) }
        val doc = documentText?.take(st.allocationChars)
        val userContent = if (doc != null) "Private document \"${st.documentName}\":\n<<<\n$doc\n>>>\n\nQuestion: $prompt" else prompt
        _ai.update { it.copy(status = AiStatus.LOADING, statusDetail = "Running on ${it.location}…", messages = it.messages + ChatMessage(true, prompt, System.currentTimeMillis())) }
        aiJob = viewModelScope.launch {
            try {
                val answer = OwnedHostModelClient(decision.url).chat(model, OwnedHostModelClient.SYSTEM_PROMPT, userContent)
                _ai.update { it.copy(status = AiStatus.COMPLETED, statusDetail = "Completed on ${it.location}", messages = it.messages + ChatMessage(false, answer, System.currentTimeMillis())) }
                record(ReceiptDraft(ReceiptKind.LOCAL_AI, "COMPLETED_ON_OWNED_HOST", "Private AI run", "Model $model on ${st.location}. Contents not stored; digests only.", decision.url,
                    digests = buildMap { put("promptSha256", Sha256.hex(userContent)); put("responseSha256", Sha256.hex(answer)); st.documentSha256?.let { put("documentSha256", it) } },
                    localObservation = Evidence("CHECKED", "Response received from owned host endpoint; this is not independent verification")))
            } catch (e: CancellationException) {
                _ai.update { it.copy(status = AiStatus.CANCELED, statusDetail = "Canceled. The host may finish computing; the result will be discarded.") }
            } catch (e: Exception) {
                _ai.update { it.copy(status = AiStatus.FAILED, statusDetail = "Failed: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun cancelPrompt() { aiJob?.cancel() }
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
        val device = runCatching { DeviceResourcesReader.read(getApplication()) }.getOrNull()
        _storage.update {
            it.copy(
                files = fileVault.names(),
                usedBytes = fileVault.usedBytes(),
                auditCount = storageAudit.count(),
                device = device ?: it.device,
            )
        }
    }

    fun setAllocationMb(mb: Int) {
        val v = mb.coerceIn(0, 8192)
        prefs.edit().putInt("store.allocMb", v).apply()
        _storage.update { it.copy(allocationMb = v) }
        storageAudit.append("allocation set to $v MB")
        refreshStorage()
    }

    fun importVault(uri: Uri) = viewModelScope.launch {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val name = getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "file"
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
                if (bytes.size > 8_000_000) error("larger than 8 MB")
                fileVault.put(name, bytes)
            }
        }
        outcome.onSuccess {
            storageAudit.append("encrypted file $it")
            refreshStorage()
            _storage.update { s -> s.copy(note = "Encrypted $it with AES-256-GCM. The plaintext was not kept.") }
        }.onFailure { e ->
            _storage.update { it.copy(note = "Import failed: ${e.message ?: "unreadable"}") }
        }
    }

    fun deleteVault(name: String) {
        val ok = fileVault.delete(name)
        storageAudit.append(if (ok) "deleted $name" else "delete missed $name")
        refreshStorage()
        _storage.update { it.copy(note = if (ok) "Deleted $name from this phone." else "That file was not in the vault.") }
    }

    fun readVault(name: String): ByteArray? = fileVault.read(name)

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

    fun setProvider(name: String) {
        prefs.edit().putString("store.provider", name).apply()
        storageAudit.append("provider label set")
        _storage.update { it.copy(provider = name, note = "Label saved. No cloud upload ran.") }
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
        _storage.value = loadStorage().copy(files = fileVault.names(), usedBytes = fileVault.usedBytes(), note = "Consent, quota, provider and kill switch reset. Vault files were kept.")
    }

    private fun metered(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return online && !unmetered
    }
}
