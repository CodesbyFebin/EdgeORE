package com.edgeore.app.device

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.receipts.ReceiptSigner
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

/** P-256 receipt-signing key that never leaves Android Keystore. */
class KeystoreReceiptSigner(private val alias: String = "edgeore.receipts.v1") : ReceiptSigner {
    override val algorithm = "SHA256withECDSA"
    override val protection = "Android Keystore P-256"
    private val ks: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    init {
        if (!ks.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build(),
                )
            }.generateKeyPair()
        }
    }

    override fun publicKeySpki(): ByteArray = ks.getCertificate(alias).publicKey.encoded
    override fun sign(data: ByteArray): ByteArray = Signature.getInstance(algorithm).run {
        initSign(ks.getKey(alias, null) as PrivateKey); update(data); sign()
    }
}

/**
 * Stores the node-pairing Ed25519 seed encrypted with an AES-GCM key held in Android Keystore.
 * The Ed25519 operation itself runs in software (BouncyCastle); this is stated in the UI.
 */
class NodeKeyVault(context: Context, private val alias: String = "edgeore.nodekey.wrap.v1") {
    private val prefs = context.getSharedPreferences("edgeore.nodekey", Context.MODE_PRIVATE)
    private val ks: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun wrapKey(): SecretKey {
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    fun createFresh(): Ed25519.KeyPair {
        val kp = Ed25519.generate()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapKey()) }
        val ct = cipher.doFinal(kp.seed)
        prefs.edit()
            .putString("iv", Base64.getEncoder().encodeToString(cipher.iv))
            .putString("seed", Base64.getEncoder().encodeToString(ct))
            .apply()
        return kp
    }

    fun load(): Ed25519.KeyPair? {
        val iv = prefs.getString("iv", null) ?: return null
        val ct = prefs.getString("seed", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, Base64.getDecoder().decode(iv)))
            }
            Ed25519.KeyPair(cipher.doFinal(Base64.getDecoder().decode(ct)))
        } catch (_: Exception) { null }
    }

    fun destroy() { prefs.edit().clear().apply() }
}
