package com.edgeore.app.ai.ondevice

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Fetches an allowlisted model file into app-private storage, only after the user consented to
 * that file and size. HTTPS only; redirects (Hugging Face sends the bytes from its CDN) must stay
 * on HTTPS. Bytes go to a .part file and are renamed into place only when the size and the pinned
 * SHA-256 both match. A partial or wrong file is never reported as downloaded.
 *
 * This downloads weights. It does not send prompts anywhere; inference runs in-process.
 */
class ModelDownloader(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    sealed interface Result {
        data class Ok(val file: File, val sha256: String) : Result
        data class Failed(val reason: String) : Result
        data object Cancelled : Result
    }

    fun download(model: OnDeviceModel, store: ModelStore, progress: (Long, Long) -> Unit, cancelled: () -> Boolean): Result {
        if (model.gated) return Result.Failed("${model.name} is gated on Hugging Face (accept the license while signed in). This build has no Hugging Face sign-in, so it does not download gated files.")
        val sha = model.sha256 ?: return Result.Failed("No SHA-256 pin for ${model.name}; refusing an unverifiable download.")
        val url = model.downloadUrl
        if (!url.startsWith("https://huggingface.co/")) return Result.Failed("Only allowlisted huggingface.co URLs are fetched")
        store.dirFor(model).mkdirs()
        val need = model.sizeBytes + FREE_SPACE_MARGIN
        if (store.usableBytes() < need) return Result.Failed("Not enough free space: needs ${model.sizeBytes} bytes plus margin.")
        val conn = try { open(URL(url)) } catch (e: Exception) { return Result.Failed("Could not open connection: ${e.message}") }
        return try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "EdgeORE-model-download")
            val code = conn.responseCode
            if (code !in 200..299) return Result.Failed("Download HTTP $code")
            if (conn.url.protocol != "https") return Result.Failed("Download was redirected off HTTPS; refused")
            val declared = conn.getHeaderFieldLong("Content-Length", -1)
            if (declared >= 0 && declared != model.sizeBytes) return Result.Failed("Server size $declared does not match allowlisted ${model.sizeBytes}")
            conn.inputStream.use { copyVerified(it, model.sizeBytes, sha, store.partFor(model), store.fileFor(model), progress, cancelled) }
        } catch (e: IOException) {
            store.partFor(model).delete()
            if (cancelled()) Result.Cancelled else Result.Failed("Download failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val FREE_SPACE_MARGIN = 256L * 1024 * 1024

        /** Streams [input] into [part], then renames it to [dest] only if exactly [expectedSize] bytes with [expectedSha256] arrived. */
        fun copyVerified(input: InputStream, expectedSize: Long, expectedSha256: String, part: File, dest: File, progress: (Long, Long) -> Unit, cancelled: () -> Boolean): Result {
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            part.parentFile?.mkdirs()
            try {
                part.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var lastReport = 0L
                    while (true) {
                        if (cancelled()) { out.close(); part.delete(); return Result.Cancelled }
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > expectedSize) { out.close(); part.delete(); return Result.Failed("More bytes than the allowlisted size; refused") }
                        md.update(buf, 0, n)
                        out.write(buf, 0, n)
                        if (total - lastReport >= 4L * 1024 * 1024 || total == expectedSize) { progress(total, expectedSize); lastReport = total }
                    }
                    out.fd.sync()
                }
            } catch (e: IOException) {
                part.delete(); throw e
            }
            if (total != expectedSize) { part.delete(); return Result.Failed("Incomplete download: $total of $expectedSize bytes") }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (hex != expectedSha256.lowercase()) { part.delete(); return Result.Failed("SHA-256 mismatch: got $hex") }
            Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            return Result.Ok(dest, hex)
        }
    }
}
