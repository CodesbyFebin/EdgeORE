package com.edgeore.app.receipts

import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.io.AtomicFiles
import com.edgeore.app.io.BoundedInput
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.UUID

enum class ReceiptKind { REVIEW, NODE, LOCAL_AI, POLICY, STORAGE }

/** One evidence dimension: its state and exactly what was checked. Missing never means "passed". */
data class Evidence(val state: String, val checked: String) {
    companion object {
        val NOT_AVAILABLE = Evidence("NOT_AVAILABLE", "Nothing was checked for this dimension")
    }
}

/**
 * Wallet authorization evidence carried inside a receipt so a second machine can check the wallet's
 * Ed25519 signature over the exact reviewed message without trusting the app's own claim.
 */
data class WalletEvidence(val messageBase64: String, val signerBase58: String, val signatureBase58: String)

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
    val wallet: WalletEvidence? = null,
)

/** Signs receipt digests with an app-held key (Android Keystore in production). Not a wallet signature. */
interface ReceiptSigner {
    val algorithm: String
    fun publicKeySpki(): ByteArray
    fun sign(data: ByteArray): ByteArray
    /** How the private key is protected, shown to the user and recorded with the key epoch. */
    val protection: String get() = "Software key"
}

/** Stable key identity: SHA-256 of the SubjectPublicKeyInfo. */
fun ReceiptSigner.keyId(): String = Sha256.hex(publicKeySpki())

class SoftwareReceiptSigner(private val keyPair: KeyPair = generate()) : ReceiptSigner {
    companion object {
        fun generate(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

        /**
         * Loads or creates a persisted software key so receipts written while Keystore is unavailable
         * still verify after restart. The file lives in app-private storage; this is weaker than Keystore
         * and is labelled that way.
         */
        fun persisted(file: File): SoftwareReceiptSigner {
            if (file.exists()) {
                val o = JSONObject(String(FileInputStream(file).use { BoundedInput.readAtMost(it, 16 * 1024) }, Charsets.UTF_8))
                val kf = KeyFactory.getInstance("EC")
                val pub = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(o.getString("spki"))))
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(o.getString("pkcs8"))))
                return SoftwareReceiptSigner(KeyPair(pub, priv))
            }
            val kp = generate()
            AtomicFiles.write(file, JSONObject().put("spki", Base64.getEncoder().encodeToString(kp.public.encoded))
                .put("pkcs8", Base64.getEncoder().encodeToString(kp.private.encoded)).toString().toByteArray(Charsets.UTF_8))
            return SoftwareReceiptSigner(kp)
        }
    }
    override val algorithm = "SHA256withECDSA"
    override val protection = "Software key in app storage (Keystore unavailable; weaker)"
    override fun publicKeySpki(): ByteArray = keyPair.public.encoded
    override fun sign(data: ByteArray): ByteArray = Signature.getInstance(algorithm).run { initSign(keyPair.private); update(data); sign() }
}

/** Every receipt-signing key this installation has used, so old records keep verifying after a key change. */
class KeyRegistry(private val file: File) {
    data class KeyEpoch(val keyId: String, val algorithm: String, val spkiBase64: String, val protection: String, val firstUsedAt: String)
    private val keys = LinkedHashMap<String, KeyEpoch>()

    init {
        if (file.exists()) {
            val arr = JSONArray(String(FileInputStream(file).use { BoundedInput.readAtMost(it, 1024 * 1024) }, Charsets.UTF_8))
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                keys[it.getString("keyId")] = KeyEpoch(it.getString("keyId"), it.getString("algorithm"), it.getString("spkiBase64"), it.getString("protection"), it.getString("firstUsedAt"))
            }
        }
    }

    @Synchronized fun register(signer: ReceiptSigner, at: Instant = Instant.now()): KeyEpoch {
        val id = signer.keyId()
        keys[id]?.let { return it }
        val e = KeyEpoch(id, signer.algorithm, Base64.getEncoder().encodeToString(signer.publicKeySpki()), signer.protection, at.toString())
        keys[id] = e
        AtomicFiles.write(file, JSONArray(keys.values.map { k ->
            JSONObject().put("keyId", k.keyId).put("algorithm", k.algorithm).put("spkiBase64", k.spkiBase64).put("protection", k.protection).put("firstUsedAt", k.firstUsedAt)
        }).toString().toByteArray(Charsets.UTF_8))
        return e
    }

    @Synchronized fun all(): List<KeyEpoch> = keys.values.toList()
    @Synchronized fun get(id: String): KeyEpoch? = keys[id]
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
    val signerKeyId: String? get() = json.optString("signerKeyId").takeIf { it.isNotEmpty() && !json.isNull("signerKeyId") }
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

/** One unreadable line in the receipt log. Valid records around it stay visible. */
data class DamagedLine(val lineNumber: Int, val reason: String)
data class ReceiptReadResult(val receipts: List<StoredReceipt>, val damaged: List<DamagedLine>) {
    val isDamaged: Boolean get() = damaged.isNotEmpty()
}

/**
 * Append-only JSON-lines receipt log. Reviewed bytes are never rewritten: later observations are
 * appended as new receipts that reference earlier ones. Each receipt chains to the previous valid
 * digest and names the key that signed it (key epochs). A damaged line is reported, never hidden.
 * The file is local app storage, not immutable storage.
 */
class ReceiptLog(
    private val file: File,
    private val signer: ReceiptSigner,
    private val clock: () -> Instant = Instant::now,
    private val keys: KeyRegistry? = null,
) {
    companion object {
        const val SCHEMA = "edgeore.receipt.v1"
        const val SCHEMA_V2 = "edgeore.receipt.v2"
        val KNOWN_SCHEMAS = setOf(SCHEMA, SCHEMA_V2)
        const val EXPORT_SCHEMA = "edgeore.receipt-export.v1"
        const val EXPORT_SCHEMA_V2 = "edgeore.receipt-export.v2"
        const val CHECKPOINT_DOMAIN = "edgeore.export-checkpoint.v1"
        const val ENVELOPE_DOMAIN = "edgeore.export-envelope.v1"
        const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"
        const val MAX_LINE_CHARS = 256 * 1024
        const val MAX_FILE_BYTES = 64L * 1024 * 1024
        val EXCLUSIONS = listOf(
            "Private keys (wallet keys stay in the wallet; node and device keys stay in Android Keystore)",
            "Document contents and AI prompts/responses (only SHA-256 digests are included)",
            "Node endpoints' credentials and pairing codes",
        )
    }

    enum class ExportMode { FULL_CHAIN, SELECTED }

    private var tail: Pair<Long, String>? = null // (last valid sequence, its sha256), cached after first read

    init { keys?.register(signer) }

    /** Reads every line; malformed, oversized or truncated lines are reported with their line number. */
    @Synchronized
    fun read(): ReceiptReadResult {
        if (!file.exists()) return ReceiptReadResult(emptyList(), emptyList())
        if (file.length() > MAX_FILE_BYTES) return ReceiptReadResult(emptyList(), listOf(DamagedLine(0, "Log is larger than ${MAX_FILE_BYTES / (1024 * 1024)} MB; refusing to load it whole")))
        val ok = mutableListOf<StoredReceipt>()
        val damaged = mutableListOf<DamagedLine>()
        val endsWithNewline = file.length() == 0L || RandomAccessFile(file, "r").use { it.seek(it.length() - 1); it.read() == '\n'.code }
        val lines = file.readLines(Charsets.UTF_8)
        lines.forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            val n = i + 1
            val isLast = i == lines.lastIndex
            if (line.length > MAX_LINE_CHARS) { damaged += DamagedLine(n, "Line exceeds ${MAX_LINE_CHARS} characters"); return@forEachIndexed }
            val r = try { StoredReceipt.fromLine(JSONObject(line)) } catch (_: Exception) {
                damaged += DamagedLine(n, if (isLast && !endsWithNewline) "Truncated final line (write was interrupted)" else "Not a valid receipt line"); return@forEachIndexed
            }
            if (!r.integrityOk) { damaged += DamagedLine(n, "SHA-256 does not match body"); return@forEachIndexed }
            if (runCatching { r.json.getString("schema") in KNOWN_SCHEMAS && r.sequence > 0 }.getOrDefault(false).not()) { damaged += DamagedLine(n, "Unknown schema or missing sequence"); return@forEachIndexed }
            ok += r
        }
        return ReceiptReadResult(ok, damaged)
    }

    /** Valid receipts only. Use [read] when the caller must show damage. */
    @Synchronized
    fun all(): List<StoredReceipt> = read().receipts

    @Synchronized
    fun append(d: ReceiptDraft): StoredReceipt {
        val (lastSeq, lastSha) = tail ?: read().receipts.lastOrNull().let { (it?.sequence ?: 0L) to (it?.sha256 ?: GENESIS) }
        val evidence = JSONObject()
            .put("localObservation", ev(d.localObservation))
            .put("nodeSignature", ev(d.nodeSignature))
            .put("independentVerification", ev(d.independentVerification))
            .put("providerAcknowledgement", ev(d.providerAcknowledgement))
        val digests = JSONObject().also { o -> d.digests.toSortedMap().forEach { (k, v) -> o.put(k, v) } }
        val body = JSONObject()
            .put("schema", SCHEMA_V2)
            .put("id", UUID.randomUUID().toString())
            .put("sequence", lastSeq + 1)
            .put("previousSha256", lastSha)
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
            .put("signerKeyId", signer.keyId())
            .put("wallet", d.wallet?.let { JSONObject().put("messageBase64", it.messageBase64).put("signer", it.signerBase58).put("signature", it.signatureBase58) } ?: JSONObject.NULL)
            .toString()
        val sha = Sha256.hex(body)
        val sig = Base64.getEncoder().encodeToString(signer.sign(sha.toByteArray(Charsets.US_ASCII)))
        val stored = StoredReceipt(body, sha, sig)
        // A torn final line from an earlier crash stays in place (and is reported); the new record starts on its own line.
        val needsNewline = file.exists() && file.length() > 0 && RandomAccessFile(file, "r").use { it.seek(it.length() - 1); it.read() != '\n'.code }
        AtomicFiles.appendDurably(file, ((if (needsNewline) "\n" else "") + stored.toLine().toString() + "\n").toByteArray(Charsets.UTF_8))
        tail = stored.sequence to sha
        return stored
    }

    private fun ev(e: Evidence) = JSONObject().put("state", e.state).put("checked", e.checked)

    private fun keyJson(k: KeyRegistry.KeyEpoch) = JSONObject().put("keyId", k.keyId).put("algorithm", k.algorithm)
        .put("publicKeySpkiBase64", k.spkiBase64).put("protection", k.protection).put("firstUsedAt", k.firstUsedAt)

    /**
     * Builds the export document. With no subset the export is FULL_CHAIN and carries a checkpoint
     * signed by the current key over (count, first/last sequence, last digest, bundle digest). A subset
     * is SELECTED: each record verifies on its own, and completeness is explicitly not claimed.
     */
    fun export(subset: List<StoredReceipt>? = null, hideDeviceKey: Boolean = false): String {
        val read = read()
        val mode = if (subset == null) ExportMode.FULL_CHAIN else ExportMode.SELECTED
        val list = subset ?: read.receipts
        val exclusions = EXCLUSIONS.toMutableList()
        if (hideDeviceKey) exclusions += "Device public keys omitted by the export-privacy control. This file cannot be signature-checked until those keys are supplied separately."
        val exportedAt = clock().toString()
        val bundle = bundleDigest(list.map { it.sha256 })
        val doc = JSONObject()
            .put("schema", EXPORT_SCHEMA_V2)
            .put("mode", mode.name)
            .put("exportedAt", exportedAt)
            .put("note", "Device-key signatures show these bytes were written by an install holding these keys. They are not independent verification, provider acknowledgement or payment. Wallet signatures, where present, can be checked separately.")
            .put("exclusions", JSONArray(exclusions))
            .put("receipts", JSONArray().also { a -> list.forEach { a.put(it.toLine()) } })
            .put("bundleSha256", bundle)
            .put("payment", "NOT_OBSERVED")
            .put("location", JSONObject.NULL)
        if (mode == ExportMode.FULL_CHAIN && read.isDamaged) {
            doc.put("damagedLines", JSONArray(read.damaged.map { JSONObject().put("line", it.lineNumber).put("reason", it.reason) }))
        }
        if (hideDeviceKey) {
            doc.put("deviceKey", JSONObject.NULL).put("deviceKeys", JSONArray())
        } else {
            doc.put("deviceKey", JSONObject().put("algorithm", signer.algorithm).put("publicKeySpkiBase64", Base64.getEncoder().encodeToString(signer.publicKeySpki())))
            val used = list.mapNotNull { it.signerKeyId }.toSet() + signer.keyId()
            val epochs = (keys?.all() ?: emptyList()).filter { it.keyId in used }.toMutableList()
            if (epochs.none { it.keyId == signer.keyId() }) {
                epochs += KeyRegistry.KeyEpoch(signer.keyId(), signer.algorithm, Base64.getEncoder().encodeToString(signer.publicKeySpki()), signer.protection, exportedAt)
            }
            doc.put("deviceKeys", JSONArray(epochs.map(::keyJson)))
        }
        if (mode == ExportMode.FULL_CHAIN) {
            val first = list.firstOrNull()?.sequence ?: 0
            val last = list.lastOrNull()
            val cp = checkpointPayload(list.size, first, last?.sequence ?: 0, last?.sha256 ?: GENESIS, bundle, exportedAt)
            doc.put("checkpoint", JSONObject().put("receiptCount", list.size).put("firstSequence", first).put("lastSequence", last?.sequence ?: 0)
                .put("lastSha256", last?.sha256 ?: GENESIS).put("bundleSha256", bundle).put("exportedAt", exportedAt)
                .put("keyId", signer.keyId()).put("signature", Base64.getEncoder().encodeToString(signer.sign(cp.toByteArray(Charsets.UTF_8)))))
        }
        // Additive, optional field (hardening backlog H4): the export schema, receipt lines and checkpoint are unchanged,
        // and a checker that predates it ignores unknown top-level fields.
        if (!hideDeviceKey) {
            val payload = envelopePayload(doc)
            doc.put("envelope", JSONObject().put("domain", ENVELOPE_DOMAIN).put("keyId", signer.keyId())
                .put("fieldsSha256", Sha256.hex(payload))
                .put("signature", Base64.getEncoder().encodeToString(signer.sign(payload.toByteArray(Charsets.UTF_8)))))
        }
        return doc.toString(2)
    }
}

/**
 * Deterministic text over the export's descriptive fields, built field by field (never by re-serialising the
 * document): domain, exportedAt, note, payment, location, each exclusion, each damaged line, then
 * keyId/protection/firstUsedAt of each key epoch, each value JSON-quoted on its own line. Structural fields (mode,
 * receipts, bundleSha256) are deliberately left out: receipts and the checkpoint already sign them, and a holder may
 * still relabel a truncated FULL_CHAIN as SELECTED (completeness is then not claimed), exactly as before.
 */
fun envelopePayload(doc: JSONObject): String {
    fun q(v: Any?): String = if (v == null || v == JSONObject.NULL) "null" else JSONObject.quote(v.toString())
    val lines = mutableListOf(ReceiptLog.ENVELOPE_DOMAIN)
    for (k in listOf("exportedAt", "note", "payment", "location")) lines += "$k=" + q(doc.opt(k))
    doc.optJSONArray("exclusions").let { a -> lines += "exclusions=" + (a?.length() ?: -1); if (a != null) for (i in 0 until a.length()) lines += "exclusion=" + q(a.opt(i)) }
    doc.optJSONArray("damagedLines").let { a -> lines += "damagedLines=" + (a?.length() ?: -1); if (a != null) for (i in 0 until a.length()) { val d = a.optJSONObject(i); lines += "damaged=" + q(d?.opt("line")) + "," + q(d?.opt("reason")) } }
    doc.optJSONArray("deviceKeys").let { a -> lines += "deviceKeys=" + (a?.length() ?: -1); if (a != null) for (i in 0 until a.length()) { val k = a.optJSONObject(i); lines += "key=" + q(k?.opt("keyId")) + "," + q(k?.opt("protection")) + "," + q(k?.opt("firstUsedAt")) } }
    return lines.joinToString("\n")
}

fun bundleDigest(shas: List<String>): String = Sha256.hex(shas.joinToString("\n"))

fun checkpointPayload(count: Int, firstSeq: Long, lastSeq: Long, lastSha: String, bundle: String, exportedAt: String): String =
    listOf(ReceiptLog.CHECKPOINT_DOMAIN, count.toString(), firstSeq.toString(), lastSeq.toString(), lastSha, bundle, exportedAt).joinToString("|")

/** A chain or broadcast claim with no signature is a false outcome, not a missing observation. */
fun chainClaimContradictsSignature(payment: String, broadcast: Boolean, solanaSignature: String): Boolean {
    val claimsChain = broadcast || payment in setOf("OBSERVED", "CONFIRMED", "FINALIZED", "PAID", "SUBMITTED") || payment.startsWith("OBSERVED_") || payment.startsWith("SUBMITTED_")
    return claimsChain && solanaSignature.isBlank()
}

/**
 * Verifies an exported receipt document. Four separate answers, never merged into one green label:
 * - integrity: digests and device signatures hold under the keys presented in the file;
 * - provenance: whether those keys match keys the caller pinned from another channel;
 * - completeness: full chain under a signed checkpoint, a selected subset, or not established;
 * - wallet: how many wallet Ed25519 signatures over reviewed messages verified independently.
 * Chain confirmation is not checked offline.
 */
object ReceiptVerifier {
    enum class Provenance { PINNED_KEY, UNPINNED_KEY, KEY_MISMATCH }
    enum class Completeness { FULL_CHAIN_CHECKPOINTED, SELECTED_SUBSET, NOT_ESTABLISHED }

    data class Report(
        val accepted: Boolean,
        val verifiedReceipts: Int,
        val findings: List<String>,
        val provenance: Provenance = Provenance.UNPINNED_KEY,
        val completeness: Completeness = Completeness.NOT_ESTABLISHED,
        val walletSignaturesVerified: Int = 0,
        /** True only when a signed envelope covering the descriptive export fields was present and verified. */
        val descriptiveFieldsSigned: Boolean = false,
    ) {
        val summary: String get() = buildString {
            append(if (accepted) "Integrity OK for $verifiedReceipts receipt(s)" else "REJECTED")
            append(" · key ").append(when (provenance) { Provenance.PINNED_KEY -> "matches pinned key"; Provenance.UNPINNED_KEY -> "not pinned (self-contained only)"; Provenance.KEY_MISMATCH -> "does NOT match pinned key" })
            append(" · ").append(when (completeness) { Completeness.FULL_CHAIN_CHECKPOINTED -> "full chain through signed checkpoint"; Completeness.SELECTED_SUBSET -> "selected subset, completeness not established"; Completeness.NOT_ESTABLISHED -> "completeness not established" })
            append(" · wallet signatures verified: $walletSignaturesVerified")
            append(if (descriptiveFieldsSigned) " · descriptive fields covered by the signed envelope" else " · descriptive fields not signed (no envelope)")
            append(" · chain status not checked offline")
        }
    }

    fun verify(exportJson: String, trustedDeviceKeySpki: ByteArray? = null, local: List<StoredReceipt> = emptyList()): Report =
        verify(exportJson, trustedDeviceKeySpki?.let { listOf(it) }, local)

    fun verify(exportJson: String, trustedKeys: Collection<ByteArray>?, local: List<StoredReceipt>): Report {
        val findings = mutableListOf<String>()
        val doc = try { JSONObject(exportJson) } catch (_: Exception) { return Report(false, 0, listOf("Not a JSON document")) }
        val schema = doc.optString("schema")
        if (schema != ReceiptLog.EXPORT_SCHEMA && schema != ReceiptLog.EXPORT_SCHEMA_V2) return Report(false, 0, listOf("Unknown export schema"))
        val v2 = schema == ReceiptLog.EXPORT_SCHEMA_V2

        val keyB64 = doc.optJSONObject("deviceKey")?.optString("publicKeySpkiBase64").orEmpty()
        val algorithm = doc.optJSONObject("deviceKey")?.optString("algorithm").orEmpty()
        if (algorithm != "SHA256withECDSA") return Report(false, 0, listOf("Unsupported device signature algorithm"))
        val currentKey: PublicKey = try { ecKey(keyB64) } catch (_: Exception) { return Report(false, 0, listOf("Device public key is missing or malformed")) }
        val keysById = HashMap<String, PublicKey>()
        keysById[Sha256.hex(currentKey.encoded)] = currentKey
        doc.optJSONArray("deviceKeys")?.let { arr ->
            for (i in 0 until arr.length()) {
                val k = arr.optJSONObject(i) ?: continue
                if (k.optString("algorithm") != "SHA256withECDSA") { findings += "Key epoch #${i + 1}: unsupported algorithm"; continue }
                val pk = try { ecKey(k.optString("publicKeySpkiBase64")) } catch (_: Exception) { findings += "Key epoch #${i + 1}: malformed key"; continue }
                val id = Sha256.hex(pk.encoded)
                if (k.optString("keyId") != id) { findings += "Key epoch #${i + 1}: keyId does not match its key"; continue }
                keysById[id] = pk
            }
        }
        var provenance = Provenance.UNPINNED_KEY
        if (trustedKeys != null) {
            val pinned = trustedKeys.map { Sha256.hex(it) }.toSet()
            if (Sha256.hex(currentKey.encoded) !in pinned) { findings += "Signed by a different device key than this install"; provenance = Provenance.KEY_MISMATCH }
            else provenance = Provenance.PINNED_KEY
        }

        val arr = doc.optJSONArray("receipts") ?: return Report(false, 0, listOf("No receipts array"))
        val localById = local.associateBy { runCatching { it.id }.getOrNull() }
        var ok = 0
        var walletOk = 0
        var previous: StoredReceipt? = null
        val parsed = mutableListOf<StoredReceipt?>()
        val shas = mutableListOf<String>()
        val usedKeys = HashSet<String>()
        for (i in 0 until arr.length()) {
            val label = "Receipt #${i + 1}"
            val r = try { StoredReceipt.fromLine(arr.getJSONObject(i)) } catch (_: Exception) { findings += "$label: malformed entry"; parsed += null; continue }
            parsed += r
            shas += r.sha256
            if (!r.integrityOk) { findings += "$label: SHA-256 does not match body (modified)"; previous = r; continue }
            val body = try { r.json } catch (_: Exception) { findings += "$label: body is not JSON"; previous = r; continue }
            if (body.optString("schema") !in ReceiptLog.KNOWN_SCHEMAS) { findings += "$label: unknown receipt schema"; previous = r; continue }
            val keyId = body.optString("signerKeyId").takeIf { it.isNotEmpty() && !body.isNull("signerKeyId") }
            val key = if (keyId != null) keysById[keyId] else currentKey
            if (key == null) { findings += "$label: signing key epoch $keyId is not included in the export"; previous = r; continue }
            if (keyId != null) usedKeys += keyId
            if (trustedKeys != null && keyId != null && provenance == Provenance.PINNED_KEY && Sha256.hex(key.encoded) !in trustedKeys.map { Sha256.hex(it) }) {
                findings += "$label: signed by key epoch $keyId which is not pinned"; provenance = Provenance.KEY_MISMATCH
            }
            val sigOk = try {
                Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(r.sha256.toByteArray(Charsets.US_ASCII)); verify(Base64.getDecoder().decode(r.deviceSignature)) }
            } catch (_: Exception) { false }
            if (!sigOk) { findings += "$label: device signature invalid"; previous = r; continue }
            val schemaProblem = schemaFinding(body)
            if (schemaProblem != null) { findings += "$label: $schemaProblem"; previous = r; continue }
            if (chainClaimContradictsSignature(body.optString("payment"), body.optBoolean("broadcast"), if (body.isNull("solanaSignature")) "" else body.optString("solanaSignature"))) {
                findings += "$label: claims a chain outcome without a signature"; previous = r; continue
            }
            when (val w = walletCheck(body)) {
                WalletCheck.Absent -> Unit
                WalletCheck.Verified -> walletOk++
                is WalletCheck.Invalid -> { findings += "$label: ${w.reason}"; previous = r; continue }
            }
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

        var completeness = Completeness.NOT_ESTABLISHED
        if (v2) {
            when (doc.optString("mode")) {
                "FULL_CHAIN" -> {
                    val chainProblem = fullChainFinding(parsed)
                    val cpProblem = checkpointFinding(doc, parsed, shas, keysById)
                    if (chainProblem != null) findings += "Full chain: $chainProblem"
                    if (cpProblem != null) findings += "Checkpoint: $cpProblem"
                    if (chainProblem == null && cpProblem == null) completeness = Completeness.FULL_CHAIN_CHECKPOINTED
                    doc.optJSONArray("damagedLines")?.takeIf { it.length() > 0 }?.let { findings += "Exporter reported ${it.length()} damaged log line(s); history is not complete" }
                }
                "SELECTED" -> completeness = Completeness.SELECTED_SUBSET
                else -> findings += "Unknown export mode"
            }
        }
        var envelopeOk = false
        doc.optJSONObject("envelope")?.let { env ->
            val problem = envelopeFinding(doc, env, keysById)
            if (problem != null) findings += "Envelope: $problem" else envelopeOk = true
        }
        val accepted = findings.isEmpty() && ok == arr.length() && ok > 0
        if (ok == 0 && arr.length() == 0) findings += "Export contains no receipts"
        return Report(accepted, ok, findings, provenance, if (accepted) completeness else Completeness.NOT_ESTABLISHED, walletOk, accepted && envelopeOk)
    }

    private fun ecKey(b64: String): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(b64)))

    private val REQUIRED = listOf("id", "sequence", "previousSha256", "createdAt", "kind", "outcome", "payment")

    /** Mandatory fields and impossible state combinations. */
    private fun schemaFinding(b: JSONObject): String? {
        REQUIRED.firstOrNull { !b.has(it) || b.isNull(it) }?.let { return "missing required field $it" }
        if (runCatching { ReceiptKind.valueOf(b.getString("kind")) }.isFailure) return "unknown kind"
        if (b.optLong("sequence", -1) < 1) return "sequence must be positive"
        if (!b.getString("previousSha256").matches(Regex("[0-9a-f]{64}"))) return "malformed previousSha256"
        if (!b.isNull("lamports") && b.has("lamports") && b.optLong("lamports", -1) < 0) return "negative amount"
        val outcome = b.getString("outcome")
        val payment = b.getString("payment")
        if (outcome == "REFUSED" && (payment.startsWith("OBSERVED") || payment.startsWith("SUBMITTED"))) return "a refused review cannot carry a payment observation"
        if (b.getString("kind") != ReceiptKind.REVIEW.name && (payment.startsWith("OBSERVED") || payment.startsWith("SUBMITTED"))) return "only wallet reviews can carry a payment observation"
        if (b.has("solanaSignature") && !b.isNull("solanaSignature")) {
            val sig = b.getString("solanaSignature")
            val decoded = runCatching { Base58.decode(sig) }.getOrNull()
            if (decoded == null || decoded.size != 64) return "solanaSignature is not a 64-byte base58 signature"
        }
        return null
    }

    private sealed interface WalletCheck {
        data object Absent : WalletCheck
        data object Verified : WalletCheck
        data class Invalid(val reason: String) : WalletCheck
    }

    /** Re-checks the wallet's Ed25519 signature over the exact reviewed message carried in the receipt. */
    private fun walletCheck(b: JSONObject): WalletCheck {
        val w = b.optJSONObject("wallet") ?: return WalletCheck.Absent
        val message = runCatching { Base64.getDecoder().decode(w.getString("messageBase64")) }.getOrNull() ?: return WalletCheck.Invalid("wallet message is not base64")
        val signer = runCatching { Base58.decode(w.getString("signer")) }.getOrNull()?.takeIf { it.size == 32 } ?: return WalletCheck.Invalid("wallet signer is not a 32-byte key")
        val sig = runCatching { Base58.decode(w.getString("signature")) }.getOrNull()?.takeIf { it.size == 64 } ?: return WalletCheck.Invalid("wallet signature is not 64 bytes")
        val digest = b.optJSONObject("digests")?.optString("messageSha256").orEmpty()
        if (digest != Sha256.hex(message)) return WalletCheck.Invalid("wallet message does not match the recorded message digest")
        if (!b.isNull("solanaSignature") && b.has("solanaSignature") && b.getString("solanaSignature") != w.getString("signature")) return WalletCheck.Invalid("transaction signature differs from the wallet signature")
        if (!Ed25519.verify(signer, message, sig)) return WalletCheck.Invalid("wallet Ed25519 signature does not verify over the recorded message")
        return WalletCheck.Verified
    }

    private fun fullChainFinding(list: List<StoredReceipt?>): String? {
        if (list.isEmpty()) return null
        var prev: StoredReceipt? = null
        for ((i, r) in list.withIndex()) {
            r ?: return "entry ${i + 1} unreadable"
            val seq = runCatching { r.sequence }.getOrNull() ?: return "entry ${i + 1} has no sequence"
            val link = runCatching { r.json.getString("previousSha256") }.getOrNull()
            if (prev == null) {
                if (seq != 1L || link != ReceiptLog.GENESIS) return "does not start at sequence 1 from genesis"
            } else {
                if (seq != prev.sequence + 1) return "sequence gap before entry ${i + 1}"
                if (link != prev.sha256) return "link broken before entry ${i + 1}"
            }
            prev = r
        }
        return null
    }

    /** A present envelope must verify; editing any descriptive field after export is then detected. */
    private fun envelopeFinding(doc: JSONObject, env: JSONObject, keys: Map<String, PublicKey>): String? {
        if (env.optString("domain") != ReceiptLog.ENVELOPE_DOMAIN) return "unknown domain"
        val key = keys[env.optString("keyId")] ?: return "signed by a key not present in the export"
        val payload = envelopePayload(doc)
        if (env.optString("fieldsSha256") != Sha256.hex(payload)) return "descriptive fields changed after export"
        val ok = try {
            Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(payload.toByteArray(Charsets.UTF_8)); verify(Base64.getDecoder().decode(env.optString("signature"))) }
        } catch (_: Exception) { false }
        return if (ok) null else "signature invalid"
    }

    private fun checkpointFinding(doc: JSONObject, list: List<StoredReceipt?>, shas: List<String>, keys: Map<String, PublicKey>): String? {
        val cp = doc.optJSONObject("checkpoint") ?: return "missing"
        val key = keys[cp.optString("keyId")] ?: return "signed by a key not present in the export"
        val last = list.lastOrNull()
        val count = list.size
        val first = list.firstOrNull()?.let { runCatching { it.sequence }.getOrNull() } ?: 0L
        val lastSeq = last?.let { runCatching { it.sequence }.getOrNull() } ?: 0L
        val lastSha = last?.sha256 ?: ReceiptLog.GENESIS
        val bundle = bundleDigest(shas)
        if (cp.optInt("receiptCount", -1) != count || cp.optLong("firstSequence", -1) != first || cp.optLong("lastSequence", -1) != lastSeq ||
            cp.optString("lastSha256") != lastSha || cp.optString("bundleSha256") != bundle) return "does not describe the receipts in this file"
        val payload = checkpointPayload(count, first, lastSeq, lastSha, bundle, cp.optString("exportedAt"))
        val ok = try {
            Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(payload.toByteArray(Charsets.UTF_8)); verify(Base64.getDecoder().decode(cp.optString("signature"))) }
        } catch (_: Exception) { false }
        return if (ok) null else "signature invalid"
    }
}
