package com.edgeore.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.edgeore.app.crypto.Sha256
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM files. The key stays in Android Keystore and does not leave the phone. */
class LocalVault(context: Context) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val key: SecretKey by lazy { loadOrCreate() }

    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
    fun names(): List<String> = dir.listFiles()?.map { it.name }?.sorted().orEmpty()

    fun put(name: String, plain: ByteArray): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "file" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val body = cipher.iv + cipher.doFinal(plain)
        val file = File(dir, "$safe.vault")
        file.writeBytes(body)
        return file.name
    }

    fun read(name: String): ByteArray? {
        val all = File(dir, name).takeIf { it.isFile }?.readBytes() ?: return null
        if (all.size < 13) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return cipher.doFinal(all.copyOfRange(12, all.size))
    }

    fun delete(name: String): Boolean = File(dir, name).delete()

    private fun loadOrCreate(): SecretKey {
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

    companion object { private const val ALIAS = "edgeore.vault" }
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
        file.appendText(JSONObject().put("prev", prev).put("at", at).put("event", event).put("sha256", sha).toString() + "\n")
    }

    fun verify(): Boolean {
        if (!file.exists()) return true
        var prev = GENESIS
        for (line in file.readLines().filter { it.isNotBlank() }) {
            val o = JSONObject(line)
            if (o.getString("prev") != prev) return false
            val payload = prev + "|" + o.getLong("at") + "|" + o.getString("event")
            if (o.getString("sha256") != Sha256.hex(payload)) return false
            prev = o.getString("sha256")
        }
        return true
    }

    fun text(): String = if (file.exists()) file.readText() else ""

    private fun lastHash(): String {
        val last = file.takeIf { it.exists() }?.readLines()?.lastOrNull { it.isNotBlank() } ?: return GENESIS
        return JSONObject(last).optString("sha256", GENESIS)
    }

    companion object { const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000" }
}
