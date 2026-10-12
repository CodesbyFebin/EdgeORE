package com.edgeore.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Marks the window FLAG_SECURE while a wallet-adjacent surface is composed (review, receipts, pairing, vault,
 * wallet): no screenshots, no screen recording, blank in the recents thumbnail. Reference-counted so nested or
 * overlapping secure surfaces only clear the flag when the last one leaves.
 */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        SecureWindowCount.acquire(activity)
        onDispose { SecureWindowCount.release(activity) }
    }
}

internal object SecureWindowCount {
    private val counts = java.util.WeakHashMap<Activity, Int>()

    @Synchronized fun acquire(a: Activity) {
        val n = (counts[a] ?: 0) + 1
        counts[a] = n
        if (n == 1 && !com.edgeore.app.BuildConfig.SCREEN_CAPTURE_ALLOWED) a.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Synchronized fun release(a: Activity) {
        val n = ((counts[a] ?: 1) - 1).coerceAtLeast(0)
        if (n == 0) { counts.remove(a); a.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } else counts[a] = n
    }

    @Synchronized fun count(a: Activity): Int = counts[a] ?: 0
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
