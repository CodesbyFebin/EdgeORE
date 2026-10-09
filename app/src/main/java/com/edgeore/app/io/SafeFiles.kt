package com.edgeore.app.io

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** Thrown when an input exceeds its hard byte limit. Nothing beyond limit + one chunk was buffered. */
class InputTooLargeException(val limitBytes: Long) : IOException("Input is larger than ${limitBytes} bytes")

/**
 * Bounded reads. Every caller that turns a stream into bytes goes through here so the size limit is
 * enforced while reading, not after the whole stream was already allocated in memory.
 */
object BoundedInput {
    private const val CHUNK = 8192

    /** Reads at most [maxBytes]. Throws [InputTooLargeException] as soon as byte maxBytes + 1 is seen. */
    fun readAtMost(stream: InputStream, maxBytes: Long): ByteArray {
        require(maxBytes in 0..Int.MAX_VALUE - CHUNK) { "limit out of range" }
        val out = ByteArrayOutputStream(minOf(maxBytes, 64L * 1024).toInt())
        val buf = ByteArray(CHUNK)
        var total = 0L
        while (true) {
            val want = minOf(CHUNK.toLong(), maxBytes - total + 1).toInt()
            val n = stream.read(buf, 0, want)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw InputTooLargeException(maxBytes)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Streams a SHA-256 digest without buffering the whole input. */
    fun sha256Hex(stream: InputStream, maxBytes: Long = Long.MAX_VALUE): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(CHUNK)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw InputTooLargeException(maxBytes)
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    sealed interface TextResult {
        data class Ok(val text: String, val bytes: ByteArray, val truncatedChars: Boolean) : TextResult
        data class Refused(val reason: String) : TextResult
    }

    /**
     * Byte limit first, then strict UTF-8 decoding (malformed input is refused, not silently
     * replaced), then a separate character limit.
     */
    fun readUtf8Text(stream: InputStream, maxBytes: Long, maxChars: Int): TextResult {
        val bytes = try { readAtMost(stream, maxBytes) } catch (e: InputTooLargeException) {
            return TextResult.Refused("Document is larger than ${maxBytes / 1024} KB")
        }
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            return TextResult.Refused("Document is not valid UTF-8 text")
        }
        return if (text.length > maxChars) TextResult.Ok(text.take(maxChars), bytes, true) else TextResult.Ok(text, bytes, false)
    }
}

/** Crash-safe file publication: write a sibling temp file, fsync it, rename over the target, fsync the directory. */
object AtomicFiles {
    const val TEMP_SUFFIX = ".part"

    fun write(target: File, bytes: ByteArray) = write(target) { it.write(bytes) }

    fun write(target: File, writer: (FileOutputStream) -> Unit) {
        val dir = target.absoluteFile.parentFile ?: throw IOException("No parent directory")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create ${dir.name}")
        val tmp = File(dir, target.name + "." + java.util.UUID.randomUUID().toString().take(8) + TEMP_SUFFIX)
        try {
            FileOutputStream(tmp).use { out ->
                writer(out)
                out.flush()
                out.fd.sync()
            }
            // Atomic within one filesystem: readers see either the old or the complete new file.
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            syncDirectory(dir)
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    /** Best effort: directory fsync makes the rename durable on Linux; unsupported platforms ignore it. */
    fun syncDirectory(dir: File) {
        try { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } } catch (_: Exception) { }
    }

    /** Appends one record and fsyncs before returning. */
    fun appendDurably(file: File, bytes: ByteArray) {
        file.absoluteFile.parentFile?.mkdirs()
        FileOutputStream(file, true).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
    }

    /** Leftover temp files are abandoned writes from a process that died; they are never published objects. */
    fun abandonedTemps(dir: File): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(TEMP_SUFFIX) }?.toList().orEmpty()
}
