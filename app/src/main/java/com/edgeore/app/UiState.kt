package com.edgeore.app

import com.edgeore.app.crypto.Base58
import com.edgeore.app.device.DeviceResources
import com.edgeore.app.solana.PendingOperation
import com.edgeore.app.solana.TransferReview
import com.edgeore.app.storage.ProviderAvailability
import com.edgeore.app.storage.VaultEntry
import com.edgeore.app.storage.VaultFileView
import com.edgeore.app.wallet.ConnectFailure
import org.json.JSONObject

// UI state types for EdgeOreViewModel, moved verbatim out of EdgeOreViewModel.kt (same package, no behaviour change).

/** Logcat tag for sanitized wallet-connection diagnostics (codes, stages, UTC times, exception types). */
const val WALLET_LOG_TAG = "EdgeORE.Wallet"

data class WalletState(
    val publicKey: ByteArray? = null,
    val label: String? = null,
    val status: String = "Not connected",
    val busy: Boolean = false,
    /** Last connect failure; stays visible until the next attempt starts. */
    val error: ConnectFailure? = null,
) {
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
    val statusDetail: String = "No owned host connected. No model weights ship in this APK.",
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

enum class ReviewPhase { EDITING, PREPARING, READY, SIGNING, SIGNED, SUBMITTING, SUBMITTED, UNKNOWN, REFUSED }
data class ReviewState(
    val destination: String = "",
    /** Empty by default: the user types (or scans) the amount; nothing is prefilled. */
    val amount: String = "",
    val phase: ReviewPhase = ReviewPhase.EDITING,
    val draft: TransferReview.Draft? = null,
    val feeLamports: Long? = null,
    val feeKnown: Boolean = false,
    val verified: TransferReview.WalletReturn.Verified? = null,
    val submittedSignature: String? = null,
    val confirmation: String? = null,
    val message: String? = null,
    /** Non-error guidance, e.g. what a scanned QR code filled in. Cleared by the next edit. */
    val info: String? = null,
    val operationId: String? = null,
    val operation: PendingOperation? = null,
)

data class VerifyOutcome(val accepted: Boolean, val summary: String, val findings: List<String>)

data class StorageState(
    val files: List<VaultEntry> = emptyList(),
    /** Per-file local and remote-backup state, kept separate. */
    val views: List<VaultFileView> = emptyList(),
    val backup: ProviderAvailability = ProviderAvailability.NotConfigured,
    val busy: Boolean = false,
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
    /** True when the most recent device read failed; [device] then holds the previous (stale) readings. */
    val deviceReadFailed: Boolean = false,
)
