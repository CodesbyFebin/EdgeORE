package com.edgeore.app.ui

/** Only formats help observation time and the Settings build label; never used for eligibility, expiry, balances or transaction decisions. */
internal object AppClock {
    @androidx.annotation.VisibleForTesting
    @Volatile var testNow: (() -> Long)? = null
    @androidx.annotation.VisibleForTesting
    @Volatile var testBuildStamp: String? = null
    fun buildStamp(): String = testBuildStamp ?: com.edgeore.app.BuildConfigInfo.COMMIT
    fun nowMs(): Long = testNow?.invoke() ?: System.currentTimeMillis()
}
