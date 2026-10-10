package com.edgeore.app.ai.ondevice

import android.annotation.SuppressLint
import org.json.JSONObject
import java.io.File

/**
 * One downloadable on-device model. The allowlist format follows Google AI Edge Gallery's
 * model_allowlist.json (Apache-2.0): a Hugging Face repo, a file and a commit pin. The download URL is
 * built the way the gallery builds it, but always pinned to the commit (never "main").
 */
data class OnDeviceModel(
    val id: String,
    val name: String,
    val repo: String,
    val file: String,
    val commit: String,
    val sizeBytes: Long,
    /** SHA-256 Hugging Face reports for the pinned file. Null only for gated entries, which this build does not download. */
    val sha256: String?,
    val license: String,
    /** Gated on Hugging Face: needs a signed-in account that accepted the license. This build has no Hugging Face sign-in. */
    val gated: Boolean,
    val minDeviceMemoryGb: Int?,
    val topK: Int,
    val topP: Double,
    val temperature: Double,
    val maxTokens: Int,
) {
    val downloadUrl: String get() = "https://huggingface.co/$repo/resolve/$commit/$file?download=true"
    val pageUrl: String get() = "https://huggingface.co/$repo"
    val downloadable: Boolean get() = !gated && sha256 != null
}

class AllowlistException(message: String) : Exception(message)

object OnDeviceCatalog {
    const val ASSET = "ai/ondevice-model-allowlist.json"
    private val ID = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    private val REPO = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*$")
    private val FILE = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*\\.litertlm$")
    private val COMMIT = Regex("^[0-9a-f]{40}$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    /** Strict parse: a malformed entry rejects the whole list rather than being shown half-checked. */
    fun parse(json: String): List<OnDeviceModel> {
        val root = try { JSONObject(json) } catch (e: Exception) { throw AllowlistException("Allowlist is not JSON") }
        val arr = root.optJSONArray("models") ?: throw AllowlistException("Allowlist has no models array")
        val out = (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: throw AllowlistException("Entry $i is not an object")
            fun str(k: String): String = o.optString(k, "").takeIf { o.has(k) && !o.isNull(k) && it.isNotBlank() } ?: throw AllowlistException("Entry $i: $k missing")
            val id = str("id").also { if (!ID.matches(it)) throw AllowlistException("Entry $i: bad id") }
            val repo = str("modelId").also { if (!REPO.matches(it) || ".." in it) throw AllowlistException("$id: bad modelId") }
            val file = str("modelFile").also { if (!FILE.matches(it) || ".." in it) throw AllowlistException("$id: modelFile must be a plain .litertlm name") }
            val commit = str("commitHash").also { if (!COMMIT.matches(it)) throw AllowlistException("$id: commitHash must be a 40-hex commit pin") }
            val size = o.optLong("sizeInBytes", -1).also { if (it <= 0) throw AllowlistException("$id: sizeInBytes must be positive") }
            val gated = o.optBoolean("gated", false)
            val sha = if (o.isNull("sha256") || !o.has("sha256")) null else o.getString("sha256").lowercase().also { if (!SHA256.matches(it)) throw AllowlistException("$id: bad sha256") }
            if (!gated && sha == null) throw AllowlistException("$id: an ungated entry needs a sha256 pin")
            val cfg = o.optJSONObject("defaultConfig") ?: JSONObject()
            OnDeviceModel(
                id, str("name"), repo, file, commit, size, sha, str("license"), gated,
                o.optInt("minDeviceMemoryInGb", 0).takeIf { it > 0 },
                cfg.optInt("topK", 40).coerceIn(1, 256), cfg.optDouble("topP", 0.95).coerceIn(0.0, 1.0),
                cfg.optDouble("temperature", 0.8).coerceIn(0.0, 2.0), cfg.optInt("maxTokens", 1024).coerceIn(64, 8192),
            )
        }
        if (out.map { it.id }.toSet().size != out.size) throw AllowlistException("Duplicate model id")
        return out
    }
}

/** Downloaded weights live in app-private storage: filesDir/models/<id>/<file>. Nothing is written to shared storage. */
class ModelStore(val root: File) {
    fun dirFor(m: OnDeviceModel) = File(root, m.id)
    fun fileFor(m: OnDeviceModel) = File(dirFor(m), m.file)
    fun partFor(m: OnDeviceModel) = File(dirFor(m), m.file + ".part")

    /** Present only when the file exists at the exact allowlisted size. The SHA-256 is checked once, at download time. */
    fun isDownloaded(m: OnDeviceModel): Boolean = fileFor(m).let { it.isFile && it.length() == m.sizeBytes }

    fun delete(m: OnDeviceModel): Boolean = dirFor(m).let { d -> !d.exists() || d.deleteRecursively() }

    /** Deliberately conservative: cache Android could clear is not counted, so a download never relies on it. */
    @SuppressLint("UsableSpace")
    fun usableBytes(): Long = (root.takeIf { it.exists() } ?: root.parentFile ?: root).usableSpace
}
