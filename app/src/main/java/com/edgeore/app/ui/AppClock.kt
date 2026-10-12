package com.edgeore.app.ui

/** Only formats help observation time; never used for eligibility, expiry, balances or transaction decisions. */
internal object AppClock {
    @androidx.annotation.VisibleForTesting
    @Volatile var testNow: (() -> Long)? = null
    fun nowMs(): Long = testNow?.invoke() ?: System.currentTimeMillis()
}
