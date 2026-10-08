package com.edgeore.app.receipts

import com.edgeore.app.crypto.Sha256
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.UUID

enum class ReceiptKind { REVIEW, NODE, LOCAL_AI, POLICY }

/** One evidence dimension: its state and exactly what was checked. Missing never means "passed". */
data class Evidence(val state: String, val checked: String) {
    companion object {
        val NOT_AVAILABLE = Evidence("NOT_AVAILABLE", "Nothing was checked for this dimension")
    }
}

data class ReceiptDraft(
    val kind: ReceiptKind,
    val outcome: String,
    val title: String,
    val detail: String,
    val source: String,
    val cluster: String? = null,
    val operationId: String? = null,
    val solanaSignature: String? = null,
    val digests: Map<String, String> = emptyMap(),
    val localObservation: Evidence = Evidence.NOT_AVAILABLE,
    val nodeSignature: Evidence = Evidence.NOT_AVAILABLE,
    val independentVerification: Evidence = Evidence.NOT_AVAILABLE,
    val providerAcknowledgement: Evidence = Evidence.NOT_AVAILABLE,
    val payment: String = "NOT_OBSERVED",
    val relatesTo: String? = null,
    val lamports: Long? = null,
)

/** Signs receipt digests with an app-held key (Android Keystore in production). Not a wallet signature. */
interface ReceiptSigner {
    val algorithm: String
    fun publicKeySpki(): ByteArray
    fun sign(data: ByteArray): ByteArray
}

class SoftwareReceiptSigner(private val keyPair: KeyPair = generate()) : ReceiptSigner {
    companion object {
        fun generate(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    }
    override val algorithm = "SHA256withECDSA"
    override fun publicKeySpki(): ByteArray = keyPair.public.encoded
    override fun sign(data: ByteArray): ByteArray = Signature.getInstance(algorithm).run { initSign(keyPair.private); update(data); sign() }
}

/** A stored receipt: the exact body string that was written, its digest, and the device signature. */
data class StoredReceipt(val body: String, val sha256: String, val deviceSignature: String) {
    val json: JSONObject by lazy { JSONObject(body) }
    val id: String get() = json.getString("id")
    val sequence: Long get() = json.getLong("sequence")
    val kind: String get() = json.getString("kind")
    val outcome: String get() = json.getString("outcome")
    val title: String get() = json.getString("title")
    val detail: String get() = json.getString("detail")
    val createdAt: String get() = json.getString("createdAt")
    val source: String get() = json.getString("source")
    val cluster: String? get() = json.optString("cluster").takeIf { it.isNotEmpty() && !json.isNull("cluster") }
    val operationId: String? get() = json.optString("operationId").takeIf { it.isNotEmpty() && !json.isNull("operationId") }
    val solanaSignature: String? get() = json.optString("solanaSignature").takeIf { it.isNotEmpty() && !json.isNull("solanaSignature") }
    val lamports: Long? get() = if (json.isNull("lamports") || !json.has("lamports")) null else json.getLong("lamports")
    val relatesTo: String? get() = json.optString("relatesTo").takeIf { it.isNotEmpty() && !json.isNull("relatesTo") }
    val integrityOk: Boolean get() = MessageDigest.isEqual(Sha256.hex(body).toByteArray(), sha256.toByteArray())

    fun toLine(): JSONObject = JSONObject().put("sha256", sha256).put("body", body).put("deviceSignature", deviceSignature)

    fun evidence(name: String): Evidence {
        val e = json.optJSONObject("evidence")?.optJSONObject(name) ?: return Evidence.NOT_AVAILABLE
        return Evidence(e.optString("state", "NOT_AVAILABLE"), e.optString("checked", ""))
    }

    companion object {
        fun fromLine(o: JSONObject) = StoredReceipt(o.getString("body"), o.getString("sha256"), o.getString("deviceSignature"))
    }
}

/**
 * Append-only JSON-lines receipt log. Reviewed bytes are never rewritten: later observations are
 * appended as new receipts that reference earlier ones. Each receipt chains to the previous digest.
 */
class ReceiptLog(private val file: File, private val signer: ReceiptSigner, private val clock: () -> Instant = Instant::now) {
    companion object {
        const val SCHEMA = "edgeore.receipt.v1"
        const val EXPORT_SCHEMA = "edgeore.receipt-export.v1"
        const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"
        val EXCLUSIONS = listOf(
            "Private keys (wallet keys stay in the wallet; node and device keys stay in Android Keystore)",
            "Document contents and AI prompts/responses (only SHA-256 digests are included)",
            "Node endpoints' credentials and pairing codes",
        )
    }

    @Synchronized
    fun all(): List<StoredReceipt> {
        if (!file.exists()) return emptyList()
        return file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { StoredReceipt.fromLine(JSONObject(it)) }
    }

    @Synchronized
    fun append(d: ReceiptDraft): StoredReceipt {
        val existing = all()
        val last = existing.lastOrNull()
        val evidence = JSONObject()
            .put("localObservation", ev(d.localObservation))
            .put("nodeSignature", ev(d.nodeSignature))
            .put("independentVerification", ev(d.independentVerification))
            .put("providerAcknowledgement", ev(d.providerAcknowledgement))
        val digests = JSONObject().also { o -> d.digests.toSortedMap().forEach { (k, v) -> o.put(k, v) } }
        val body = JSONObject()
            .put("schema", SCHEMA)
            .put("id", UUID.randomUUID().toString())
            .put("sequence", (last?.sequence ?: 0L) + 1)
            .put("previousSha256", last?.sha256 ?: GENESIS)
            .put("createdAt", clock().toString())
            .put("kind", d.kind.name)
            .put("outcome", d.outcome)
            .put("title", d.title)
            .put("detail", d.detail)
            .put("source", d.source)
            .put("cluster", d.cluster ?: JSONObject.NULL)
            .put("operationId", d.operationId ?: JSONObject.NULL)
            .put("solanaSignature", d.solanaSignature ?: JSONObject.NULL)
            .put("relatesTo", d.relatesTo ?: JSONObject.NULL)
            .put("lamports", d.lamports ?: JSONObject.NULL)
            .put("digests", digests)
            .put("evidence", evidence)
            .put("payment", d.payment)
            .toString()
        val sha = Sha256.hex(body)
        val sig = Base64.getEncoder().encodeToString(signer.sign(sha.toByteArray(Charsets.US_ASCII)))
        val stored = StoredReceipt(body, sha, sig)
        file.parentFile?.mkdirs()
        file.appendText(stored.toLine().toString() + "\n", Charsets.UTF_8)
        return stored
    }

    private fun ev(e: Evidence) = JSONObject().put("state", e.state).put("checked", e.checked)

    /** Builds the export document. `subset` exports selected receipts; default is the full chain. */
    fun export(subset: List<StoredReceipt>? = null): String {
        val list = subset ?: all()
        val arr = JSONArray().also { a -> list.forEach { a.put(it.toLine()) } }
        return JSONObject()
            .put("schema", EXPORT_SCHEMA)
            .put("exportedAt", clock().toString())
            .put("deviceKey", JSONObject().put("algorithm", signer.algorithm).put("publicKeySpkiBase64", Base64.getEncoder().encodeToString(signer.publicKeySpki())))
            .put("note", "Device-key signatures show these bytes were written by this app install. They are not independent verification, provider acknowledgement or payment.")
            .put("exclusions", JSONArray(EXCLUSIONS))
            .put("receipts", arr)
            .put("bundleSha256", bundleDigest(list.map { it.sha256 }))
            .toString(2)
    }
}

fun bundleDigest(shas: List<String>): String = Sha256.hex(shas.joinToString("\n"))

/** Verifies an exported receipt document and rejects any tampering it can detect. */
object ReceiptVerifier {
    data class Report(val accepted: Boolean, val verifiedReceipts: Int, val findings: List<String>)

    fun verify(exportJson: String, trustedDeviceKeySpki: ByteArray? = null, local: List<StoredReceipt> = emptyList()): Report {
        val findings = mutableListOf<String>()
        val doc = try { JSONObject(exportJson) } catch (_: Exception) { return Report(false, 0, listOf("Not a JSON document")) }
        if (doc.optString("schema") != ReceiptLog.EXPORT_SCHEMA) return Report(false, 0, listOf("Unknown export schema"))
        val keyB64 = doc.optJSONObject("deviceKey")?.optString("publicKeySpkiBase64").orEmpty()
        val algorithm = doc.optJSONObject("deviceKey")?.optString("algorithm").orEmpty()
        if (algorithm != "SHA256withECDSA") return Report(false, 0, listOf("Unsupported device signature algorithm"))
        val publicKey: PublicKey = try {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(keyB64)))
        } catch (_: Exception) { return Report(false, 0, listOf("Device public key is missing or malformed")) }
        if (trustedDeviceKeySpki != null && !MessageDigest.isEqual(trustedDeviceKeySpki, publicKey.encoded)) {
            findings += "Signed by a different device key than this install"
        }
        val arr = doc.optJSONArray("receipts") ?: return Report(false, 0, listOf("No receipts array"))
        val localById = local.associateBy { runCatching { it.id }.getOrNull() }
        var ok = 0
        var previous: StoredReceipt? = null
        val shas = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val r = try { StoredReceipt.fromLine(arr.getJSONObject(i)) } catch (_: Exception) { findings += "Receipt #${i + 1}: malformed entry"; continue }
            shas += r.sha256
            val label = "Receipt #${i + 1}"
            if (!r.integrityOk) { findings += "$label: SHA-256 does not match body (modified)"; previous = r; continue }
            val sigOk = try {
                Signature.getInstance(algorithm).run { initVerify(publicKey); update(r.sha256.toByteArray(Charsets.US_ASCII)); verify(Base64.getDecoder().decode(r.deviceSignature)) }
            } catch (_: Exception) { false }
            if (!sigOk) { findings += "$label: device signature invalid"; previous = r; continue }
            val body = try { r.json } catch (_: Exception) { findings += "$label: body is not JSON"; previous = r; continue }
            if (body.optString("schema") != ReceiptLog.SCHEMA) { findings += "$label: unknown receipt schema"; previous = r; continue }
            if (previous != null && previous.integrityOk) {
                val prevSeq = runCatching { previous.sequence }.getOrNull()
                if (prevSeq != null && body.optLong("sequence") == prevSeq + 1 && body.optString("previousSha256") != previous.sha256) {
                    findings += "$label: chain link to previous receipt broken"; previous = r; continue
                }
            }
            val localCopy = localById[body.optString("id")]
            if (localCopy != null && localCopy.sha256 != r.sha256) { findings += "$label: differs from the copy stored on this device"; previous = r; continue }
            ok++
            previous = r
        }
        if (doc.optString("bundleSha256") != bundleDigest(shas)) findings += "Bundle digest does not match the receipt list (receipt added, removed or reordered)"
        val accepted = findings.isEmpty() && ok == arr.length() && ok > 0
        if (ok == 0 && arr.length() == 0) findings += "Export contains no receipts"
        return Report(accepted, ok, findings)
    }
}
