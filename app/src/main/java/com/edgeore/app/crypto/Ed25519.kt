package com.edgeore.app.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/** Thin Ed25519 wrapper. Used for node-agent pairing keys and to verify wallet-returned signatures. */
object Ed25519 {
    class KeyPair(val seed: ByteArray) {
        init { require(seed.size == 32) { "BAD_SEED" } }
        private val priv = Ed25519PrivateKeyParameters(seed, 0)
        val publicKey: ByteArray = priv.generatePublicKey().encoded
        fun sign(message: ByteArray): ByteArray = Ed25519Signer().run {
            init(true, priv); update(message, 0, message.size); generateSignature()
        }
    }

    fun generate(random: SecureRandom = SecureRandom()): KeyPair = KeyPair(ByteArray(32).also(random::nextBytes))

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        return try {
            Ed25519Signer().run {
                init(false, Ed25519PublicKeyParameters(publicKey, 0)); update(message, 0, message.size); verifySignature(signature)
            }
        } catch (_: Exception) { false }
    }
}
