package com.edgeore.app.wallet

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Local user-presence check that runs immediately before a reviewed message is handed to the wallet (MWA dispatch).
 * Biometric, with the device PIN / pattern / password as fallback. It does not replace the wallet's own approval;
 * it stops a borrowed, unlocked phone from reaching the wallet prompt in one tap. With no screen lock set, signing is
 * refused rather than silently skipped.
 */
object UserPresenceGate {
    sealed interface Result {
        data object Passed : Result

        data class Refused(
            val reason: String,
        ) : Result
    }

    /** Authenticator set for this API level: PIN fallback with STRONG needs API 30; API 28-29 pairs it with WEAK. */
    fun authenticators(sdk: Int = Build.VERSION.SDK_INT): Int =
        if (sdk >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun availability(code: Int): Result? =
        when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                Result.Refused(
                    "Set a screen lock (PIN, pattern, password or biometric) before signing.",
                )
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                Result.Refused(
                    "This device needs a security update before it can confirm it is you.",
                )
            else -> Result.Refused("This device cannot confirm it is you right now (code $code). Not signed.")
        }

    fun require(
        activity: FragmentActivity,
        onResult: (Result) -> Unit,
    ) {
        val allowed = authenticators()
        availability(BiometricManager.from(activity).canAuthenticate(allowed))?.let {
            onResult(it)
            return
        }
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(Result.Passed)

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) = onResult(Result.Refused("Not signed: $errString"))
                    // onAuthenticationFailed (one bad attempt) keeps the prompt open; no result yet.
                },
            )
        prompt.authenticate(
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Confirm it's you")
                .setSubtitle("Before EdgeORE opens your wallet to sign the reviewed message")
                .setAllowedAuthenticators(allowed)
                .build(),
        )
    }
}
