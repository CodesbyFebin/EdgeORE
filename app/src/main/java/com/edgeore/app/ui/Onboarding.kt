package com.edgeore.app.ui

import android.content.Context

/** First-run introduction state. A UI preference only: no wallet, receipt or vault data lives here. */
object Onboarding {
    private const val PREFS = "edgeore.ui"
    private const val KEY = "onboarding.v1.done"
    fun done(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun set(ctx: Context, value: Boolean) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply() }
}
