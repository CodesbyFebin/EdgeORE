package com.edgeore.app.edge

import java.io.File

/** UI state for the EdgeORE differentiators. Read-only data; nothing here can sign. */
data class EdgeState(
    val alertsOn: Boolean = false,
    val alertsMessage: String? = null,
    val explanation: String? = null,
    val explainerBackend: String? = null,
    val proofs: List<StoredProof> = emptyList(),
    val proofMessage: String? = null,
    val lastAlerts: List<RoundAlerts.Alert> = emptyList(),
    /** Null until checked; [ProtocolGuard.BANNER] plus reasons when the live program differs from this build. */
    val protocol: ProtocolGuard.Result? = null,
)

data class StoredProof(
    val sha256: String,
    val roundId: Long,
    val receiptId: String?,
    val proofJson: String,
    val check: RoundProof.Check,
)

/** Proof JSON files named by their SHA-256; the matching receipt carries the same digest. */
class RoundProofStore(
    private val dir: File,
) {
    fun save(json: String): String {
        dir.mkdirs()
        val sha = RoundProof.digest(json)
        File(dir, "$sha.json").writeText(json, Charsets.UTF_8)
        return sha
    }

    fun all(): List<Pair<String, String>> =
        (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> f.readText(Charsets.UTF_8).let { t -> (RoundProof.digest(t) to t).takeIf { it.first + ".json" == f.name } } }
}

/** Pending rounds waiting to settle: roundId -> deploy receipt id (or message sha when the receipt is not yet known). */
object PendingRounds {
    fun encode(m: Map<Long, String>): String = m.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decode(s: String?): Map<Long, String> =
        s
            .orEmpty()
            .split(";")
            .mapNotNull { e -> e.split("=", limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k.toLongOrNull()?.let { it to v } } }
            .toMap()
}
