package com.edgeore.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.edgeore.app.crypto.Hex
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.io.AtomicFiles
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Specific vault failures. None of them is ever shown as an empty vault or a successful save. */
sealed class VaultException(message: String) : Exception(message) {
    class TooLarge(limit: Long) : VaultException("File is larger than ${limit / 1_000_000} MB")
    class OverAllowance(needed: Long, available: Long) : VaultException("Vault allowance has ${available} bytes free; this file needs ${needed}")
    class NoSpace : VaultException("Not enough free space on this phone")
    class InvalidId : VaultException("Not a vault object identifier")
    class NotFound : VaultException("That file is not in the vault")
    class Corrupt(why: String) : VaultException("Vault object is damaged: $why")
    class Tampered : VaultException("Authentication failed: the object or its metadata was modified, or the key changed")
    class KeyUnavailable : VaultException("The vault key is not available on this phone; these files cannot be decrypted")
}

/** One listed object. Display names are metadata; identity is the random object id. */
data class VaultEntry(val id: String, val displayName: String, val plainBytes: Long, val storedBytes: Long, val legacy: Boolean)

/**
 * AES-256-GCM vault with a versioned, authenticated envelope:
 *
 *   "EOV2" | version(1)=2 | alg(1)=1 AES-256-GCM | ivLen(1)=12 | iv(12) | objectId(16) |
 *   nameLen(2) | name(UTF-8) | plainLen(8) | ciphertext+tag
 *
 * The whole header is GCM additional authenticated data, so changing the id, name, length or
 * version fails decryption. Objects are written to a temp file, fsynced, then renamed into place;
 * an interrupted write never appears as a stored object. The key is device-bound (Android Keystore):
 * losing the key, uninstalling, or moving files to another phone makes them unrecoverable.
 */
class LocalVault(
    private val dir: File,
    private val keyProvider: () -> SecretKey,
    private val maxPlainBytes: Long = MAX_PLAIN_BYTES,
    private val freeSpace: () -> Long,
) {
    private val reservations = HashMap<String, Long>()
    private val lock = Any()
    private val random = SecureRandom()

    init { dir.mkdirs() }

    /** Removes temp files left by a process that died mid-write. Returns how many were reclaimed. */
    fun recoverAbandonedWrites(): Int = AtomicFiles.abandonedTemps(dir).count { it.delete() }

    fun usedBytes(): Long = objectFiles().sumOf { it.length() }

    fun entries(): List<VaultEntry> = objectFiles().mapNotNull { f ->
        if (f.name.endsWith(LEGACY_SUFFIX)) VaultEntry(f.name, f.name.removeSuffix(LEGACY_SUFFIX), -1, f.length(), legacy = true)
        else runCatching { readHeader(f) }.getOrNull()?.let { h -> VaultEntry(h.id, h.name, h.plainLen, f.length(), legacy = false) }
            ?: VaultEntry(f.name.removeSuffix(SUFFIX), "(unreadable header)", -1, f.length(), legacy = false)
    }.sortedBy { it.displayName.lowercase() }

    /** Kept for callers that only need identifiers. */
    fun names(): List<String> = entries().map { it.id }

    /**
     * Encrypts and publishes one object. [allowanceBytes] <= 0 means no allowance is configured (only
     * the per-file cap and phone free space apply). The reservation is taken before writing and
     * released on every outcome.
     */
    fun put(displayName: String, plain: ByteArray, allowanceBytes: Long = 0): VaultEntry {
        if (plain.size > maxPlainBytes) throw VaultException.TooLarge(maxPlainBytes)
        val id = Hex.encode(ByteArray(16).also(random::nextBytes))
        val name = displayName.replace(Regex("[\\p{Cntrl}]"), "").take(MAX_NAME_CHARS).ifBlank { "file" }
        val nameBytes = name.toByteArray(Charsets.UTF_8).let { if (it.size > MAX_NAME_BYTES) it.copyOf(MAX_NAME_BYTES) else it }
        val stored = HEADER_FIXED + nameBytes.size + plain.size + TAG_BYTES.toLong()
        synchronized(lock) {
            if (allowanceBytes > 0) {
                val available = allowanceBytes - usedBytes() - reservations.values.sum()
                if (stored > available) throw VaultException.OverAllowance(stored, available.coerceAtLeast(0))
            }
            if (stored + reservations.values.sum() > freeSpace()) throw VaultException.NoSpace()
            reservations[id] = stored
        }
        try {
            val key = try { keyProvider() } catch (e: Exception) { throw VaultException.KeyUnavailable() }
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val header = header(iv, Hex.decode(id), nameBytes, plain.size.toLong())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            try { cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, iv)) } catch (_: java.security.InvalidAlgorithmParameterException) {
                // Android Keystore keys generate their own IV (caller-provided IVs are rejected by default).
                cipher.init(Cipher.ENCRYPT_MODE, key)
            }
            val actualIv = cipher.iv
            val finalHeader = if (actualIv.contentEquals(iv)) header else header(actualIv, Hex.decode(id), nameBytes, plain.size.toLong())
            cipher.updateAAD(finalHeader)
            val ct = cipher.doFinal(plain)
            AtomicFiles.write(File(dir, id + SUFFIX)) { out -> out.write(finalHeader); out.write(ct) }
            return VaultEntry(id, name, plain.size.toLong(), finalHeader.size + ct.size.toLong(), legacy = false)
        } catch (e: java.io.IOException) {
            if (e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true) throw VaultException.NoSpace()
            throw e
        } finally {
            synchronized(lock) { reservations.remove(id) }
        }
    }

    /** Decrypts one object after validating its identifier, containment and every length field. */
    fun read(id: String): ByteArray {
        val f = fileFor(id)
        if (!f.isFile) throw VaultException.NotFound()
        if (f.name.endsWith(LEGACY_SUFFIX)) return readLegacy(f)
        val total = f.length()
        if (total > maxPlainBytes + HEADER_FIXED + MAX_NAME_BYTES + TAG_BYTES) throw VaultException.Corrupt("larger than any object this vault writes")
        val h = readHeader(f)
        if (h.id != id) throw VaultException.Corrupt("object id in header does not match file")
        val expected = h.headerBytes.size + h.plainLen + TAG_BYTES
        if (total != expected) throw VaultException.Corrupt(if (total < expected) "truncated" else "unexpected trailing bytes")
        val all = FileInputStream(f).use { s -> ByteArray(total.toInt()).also { DataInputStream(s).readFully(it) } }
        val key = try { keyProvider() } catch (_: Exception) { throw VaultException.KeyUnavailable() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, h.iv))
        cipher.updateAAD(h.headerBytes)
        return try { cipher.doFinal(all, h.headerBytes.size, all.size - h.headerBytes.size) } catch (_: AEADBadTagException) { throw VaultException.Tampered() }
    }

    fun delete(id: String): Boolean {
        val f = try { fileFor(id) } catch (_: VaultException.InvalidId) { return false }
        val ok = f.isFile && f.delete()
        if (ok) AtomicFiles.syncDirectory(dir)
        return ok
    }

    private fun readLegacy(f: File): ByteArray {
        val all = f.readBytes()
        // Legacy (0.2.6 and earlier): iv(12) | ciphertext | tag(16), no header.
        if (all.size < IV_BYTES + TAG_BYTES) throw VaultException.Corrupt("legacy object shorter than IV + tag")
        val key = try { keyProvider() } catch (_: Exception) { throw VaultException.KeyUnavailable() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, all.copyOfRange(0, IV_BYTES)))
        return try { cipher.doFinal(all, IV_BYTES, all.size - IV_BYTES) } catch (_: AEADBadTagException) { throw VaultException.Tampered() }
    }

    private fun objectFiles(): List<File> = dir.listFiles { f -> f.isFile && (f.name.endsWith(SUFFIX) || f.name.endsWith(LEGACY_SUFFIX)) }?.toList().orEmpty()

    /** The only place a caller string becomes a path. */
    private fun fileFor(id: String): File {
        val name = when {
            id.matches(ID_PATTERN) -> id + SUFFIX
            id.matches(LEGACY_PATTERN) -> id
            else -> throw VaultException.InvalidId()
        }
        val f = File(dir, name)
        if (f.canonicalFile.parentFile != dir.canonicalFile) throw VaultException.InvalidId()
        return f
    }

    private class Header(val id: String, val name: String, val iv: ByteArray, val plainLen: Long, val headerBytes: ByteArray)

    private fun header(iv: ByteArray, id: ByteArray, name: ByteArray, plainLen: Long): ByteArray =
        ByteBuffer.allocate(HEADER_FIXED + name.size).apply {
            put(MAGIC); put(VERSION); put(ALG_AES256_GCM); put(IV_BYTES.toByte()); put(iv); put(id)
            putShort(name.size.toShort()); put(name); putLong(plainLen)
        }.array()

    private fun readHeader(f: File): Header {
        FileInputStream(f).use { s ->
            val d = DataInputStream(s)
            val fixedPrefix = ByteArray(4 + 3 + IV_BYTES + 16 + 2)
            try { d.readFully(fixedPrefix) } catch (_: java.io.EOFException) { throw VaultException.Corrupt("truncated header") }
            val b = ByteBuffer.wrap(fixedPrefix)
            val magic = ByteArray(4).also { b.get(it) }
            if (!magic.contentEquals(MAGIC)) throw VaultException.Corrupt("unknown format")
            val version = b.get(); val alg = b.get(); val ivLen = b.get().toInt()
            if (version != VERSION) throw VaultException.Corrupt("unsupported version $version")
            if (alg != ALG_AES256_GCM || ivLen != IV_BYTES) throw VaultException.Corrupt("unsupported algorithm parameters")
            val iv = ByteArray(IV_BYTES).also { b.get(it) }
            val id = ByteArray(16).also { b.get(it) }
            val nameLen = b.short.toInt() and 0xffff
            if (nameLen > MAX_NAME_BYTES) throw VaultException.Corrupt("name length out of range")
            val rest = ByteArray(nameLen + 8)
            try { d.readFully(rest) } catch (_: java.io.EOFException) { throw VaultException.Corrupt("truncated header") }
            val rb = ByteBuffer.wrap(rest)
            val name = ByteArray(nameLen).also { rb.get(it) }
            val plainLen = rb.long
            if (plainLen < 0 || plainLen > maxPlainBytes) throw VaultException.Corrupt("length field out of range")
            return Header(Hex.encode(id), String(name, Charsets.UTF_8), iv, plainLen, fixedPrefix + rest)
        }
    }

    companion object {
        const val MAX_PLAIN_BYTES = 8_000_000L
        const val SUFFIX = ".eov2"
        const val LEGACY_SUFFIX = ".vault"
        private const val ALIAS = "edgeore.vault"
        private val MAGIC = "EOV2".toByteArray(Charsets.US_ASCII)
        private const val VERSION: Byte = 2
        private const val ALG_AES256_GCM: Byte = 1
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val MAX_NAME_CHARS = 120
        private const val MAX_NAME_BYTES = 480
        const val HEADER_FIXED = 4 + 3 + IV_BYTES + 16 + 2 + 8
        private val ID_PATTERN = Regex("[0-9a-f]{32}")
        private val LEGACY_PATTERN = Regex("[A-Za-z0-9._-]{1,60}\\.vault")

        fun forApp(context: Context): LocalVault {
            val dir = File(context.filesDir, "vault").apply { mkdirs() }
            val sm = context.getSystemService(android.os.storage.StorageManager::class.java)
            // Allocatable bytes include cache the system may clear for us (API 26+, our minSdk).
            return LocalVault(dir, { keystoreKey() }, freeSpace = { sm.getAllocatableBytes(sm.getUuidForPath(dir)) })
        }

        private fun keystoreKey(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return gen.generateKey()
        }
    }
}

/** Append-only lines. Each line includes the previous digest. */
class StorageAudit(private val file: File) {
    fun count(): Int = if (!file.exists()) 0 else file.readLines().count { it.isNotBlank() }

    fun append(event: String) {
        file.parentFile?.mkdirs()
        val prev = lastHash()
        val at = System.currentTimeMillis()
        val payload = "$prev|$at|$event"
        val sha = Sha256.hex(payload)
        AtomicFiles.appendDurably(file, (JSONObject().put("prev", prev).put("at", at).put("event", event).put("sha256", sha).toString() + "\n").toByteArray(Charsets.UTF_8))
    }

    fun verify(): Boolean {
        if (!file.exists()) return true
        var prev = GENESIS
        for (line in file.readLines().filter { it.isNotBlank() }) {
            val o = try { JSONObject(line) } catch (_: Exception) { return false }
            if (o.optString("prev") != prev) return false
            val payload = prev + "|" + o.optLong("at") + "|" + o.optString("event")
            if (o.optString("sha256") != Sha256.hex(payload)) return false
            prev = o.getString("sha256")
        }
        return true
    }

    fun text(): String = if (file.exists()) file.readText() else ""

    private fun lastHash(): String {
        val last = file.takeIf { it.exists() }?.readLines()?.lastOrNull { it.isNotBlank() } ?: return GENESIS
        return runCatching { JSONObject(last).optString("sha256", GENESIS) }.getOrDefault(GENESIS)
    }

    companion object { const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000" }
}
