package com.edgeore.app.storage

import com.edgeore.app.crypto.Sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.coroutineContext

/** Optional client for services/storage-gateway; not selected by the default app build.
 * Credentials are provided at request time, never put in provider labels, URLs or receipts.
 * Only HTTPS, no redirects, no arbitrary remote download URLs and no automatic write retries.
 */
class OwnedGatewayBackupProvider(
    endpoint: String,
    private val token: () -> String,
    private val label: String = "Configured owned storage gateway",
) : BackupProvider {
    private val base = URI(endpoint).also {
        require(it.scheme == "https" && !it.host.isNullOrBlank()) { "Gateway requires HTTPS" }
        require(it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null) { "No credentials, query or fragment in gateway URL" }
        require(it.path.isNullOrEmpty() || it.path == "/") { "Gateway URL must be an origin" }
    }.toString().trimEnd('/')
    override fun availability(): ProviderAvailability = ProviderAvailability.Configured(label)

    private fun identifier(id: String): String = id.also {
        require(it.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid backup job identifier" }
    }
    private fun digest(ref: String): String = ref.also {
        require(it.matches(Regex("[0-9a-f]{64}"))) { "Invalid remote object reference" }
    }
    private fun connection(path: String, method: String): HttpURLConnection {
        val credential = token()
        require(credential.length >= 32 && credential.all { it.code in 33..126 }) { "Storage credential unavailable" }
        return (URI(base + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $credential")
            setRequestProperty("Accept", "application/json")
        }
    }
    private fun response(c: HttpURLConnection): JSONObject {
        // Do not surface server error bodies, which might contain credentials or private paths.
        if (c.responseCode != 200) throw BackupUnavailableException("Storage gateway HTTP ${c.responseCode}")
        val bytes = c.inputStream.use { com.edgeore.app.io.BoundedInput.readAtMost(it, 16_384L) }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }
    private fun observation(o: JSONObject): ProviderObservation = when (o.getString("state")) {
        "canceled" -> ProviderObservation.Canceled(o.getString("observedAt"))
        "acknowledged" -> ProviderObservation.Acknowledged(digest(o.getString("remoteRef")), digest(o.getString("ciphertextSha256")), o.getString("observedAt"))
        else -> throw BackupUnavailableException("Unrecognized storage observation")
    }
    override suspend fun upload(request: UploadRequest): ProviderObservation = withContext(Dispatchers.IO) {
        require(request.ciphertextBytes == request.ciphertext.size.toLong() && request.ciphertextBytes <= MAX_BYTES)
        require(Sha256.hex(request.ciphertext) == request.ciphertextSha256)
        coroutineContext.ensureActive()
        val c = connection("/v1/jobs/${identifier(request.clientJobId)}", "PUT")
        try {
            c.doOutput = true
            c.setFixedLengthStreamingMode(request.ciphertextBytes)
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setRequestProperty("X-Ciphertext-SHA256", digest(request.ciphertextSha256))
            c.outputStream.use { out ->
                var offset = 0
                while (offset < request.ciphertext.size) {
                    coroutineContext.ensureActive()
                    val n = minOf(8192, request.ciphertext.size - offset)
                    out.write(request.ciphertext, offset, n)
                    offset += n
                }
            }
            coroutineContext.ensureActive()
            observation(response(c))
        } finally { c.disconnect() }
    }
    override suspend fun status(clientJobId: String): ProviderObservation = withContext(Dispatchers.IO) {
        val c = connection("/v1/jobs/${identifier(clientJobId)}", "GET")
        try {
            coroutineContext.ensureActive()
            if (c.responseCode == 404) ProviderObservation.NotFound else observation(response(c))
        } finally { c.disconnect() }
    }
    override suspend fun download(remoteRef: String, maxBytes: Long, sink: OutputStream): Long = withContext(Dispatchers.IO) {
        require(maxBytes in 0..MAX_BYTES)
        val c = connection("/v1/objects/${digest(remoteRef)}", "GET")
        try {
            if (c.responseCode != 200) throw BackupUnavailableException("Storage gateway HTTP ${c.responseCode}")
            if (c.contentLengthLong > maxBytes) throw DownloadTooLargeException(maxBytes)
            var total = 0L
            c.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (total + n > maxBytes) throw DownloadTooLargeException(maxBytes)
                    sink.write(buffer, 0, n)
                    total += n
                }
            }
            total
        } finally { c.disconnect() }
    }
    override suspend fun cancel(clientJobId: String): Boolean = withContext(Dispatchers.IO) {
        val c = connection("/v1/jobs/${identifier(clientJobId)}/cancel", "POST")
        try {
            coroutineContext.ensureActive()
            c.doOutput = true
            c.setFixedLengthStreamingMode(0)
            c.outputStream.use { }
            response(c).getBoolean("canceled")
        } finally { c.disconnect() }
    }
    companion object { private const val MAX_BYTES = 8_001_024L }
}
