package com.edgeore.app.storage

import android.annotation.SuppressLint
import com.edgeore.app.crypto.Sha256
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SPKI SHA-256 pins (the hash of a certificate's DER SubjectPublicKeyInfo, as used by OkHttp's CertificatePinner and
 * HPKP), applied to the platform HttpsURLConnection that EdgeORE already uses. No extra dependency.
 *
 * Accepted pin forms: `sha256/<base64 of 32 bytes>` or 64 hex characters (colons allowed).
 *
 * Rules:
 * - No pins: platform CA trust only. A self-signed or private-CA host is refused during the handshake.
 * - Pins and the chain validates against platform CAs: some certificate in the chain must match a pin.
 * - Pins and the chain does NOT validate (owner-run self-signed host): only the leaf may match, and it must be inside its
 *   validity period. A pinned CA in an unvalidated chain is not accepted, since nothing proves it signed the leaf.
 * - Hostname: platform hostname check, or a pinned leaf (owned hosts are often reached by IP literal).
 * Every refusal happens in the TLS handshake, before any request header or body is written.
 */
class SpkiPinning(
    pins: Collection<String>,
) {
    private val pins: List<ByteArray> = pins.map(::parse)

    val isEmpty: Boolean get() = pins.isEmpty()

    private fun pinned(cert: X509Certificate): Boolean {
        val spki = spkiSha256(cert)
        return pins.any { MessageDigest.isEqual(it, spki) }
    }

    private val platform: X509TrustManager =
        TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    @SuppressLint("CustomX509TrustManager") // Delegates to the platform and only ever narrows what it accepts.
    private val trustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ): Unit = throw CertificateException("Client certificates are not accepted")

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {
                if (chain.isNullOrEmpty()) throw CertificateException("No server certificate")
                val platformTrusted =
                    try {
                        platform.checkServerTrusted(chain, authType)
                        true
                    } catch (e: CertificateException) {
                        if (pins.isEmpty()) {
                            throw CertificateException(
                                "Server certificate is not trusted by a public CA and no SPKI pin was configured",
                                e,
                            )
                        }
                        false
                    }
                if (pins.isEmpty()) return
                if (platformTrusted) {
                    if (chain.none(::pinned)) throw CertificateException("No certificate in the chain matches a configured SPKI pin")
                } else {
                    val leaf = chain[0]
                    if (!pinned(leaf)) throw CertificateException("Self-signed or private certificate does not match a configured SPKI pin")
                    leaf.checkValidity()
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers
        }

    val socketFactory: SSLSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory

    val hostnameVerifier =
        HostnameVerifier { host, session ->
            if (HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)) return@HostnameVerifier true
            val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
            pins.isNotEmpty() && leaf != null && pinned(leaf)
        }

    fun apply(connection: HttpsURLConnection) {
        connection.sslSocketFactory = socketFactory
        connection.hostnameVerifier = hostnameVerifier
    }

    companion object {
        private val HEX = Regex("[0-9a-f]{64}")

        fun spkiSha256(cert: X509Certificate): ByteArray = Sha256.digest(cert.publicKey.encoded)

        fun format(cert: X509Certificate): String = "sha256/" + Base64.getEncoder().encodeToString(spkiSha256(cert))

        fun parse(pin: String): ByteArray {
            val p = pin.trim()
            if (p.startsWith("sha256/")) {
                val bytes = runCatching { Base64.getDecoder().decode(p.removePrefix("sha256/")) }.getOrNull()
                require(bytes != null && bytes.size == 32) { "SPKI pin must be sha256/ followed by base64 of 32 bytes" }
                return bytes
            }
            val hex = p.lowercase().replace(":", "")
            require(HEX.matches(hex)) { "SPKI pin must be sha256/<base64> or 64 hex characters" }
            return ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}
