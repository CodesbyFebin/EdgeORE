package com.edgeore.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeType

/*
 * A plain-language first line for a technical failure, with the exact original text one tap away.
 *
 * The "is this sentence fit for a user?" test (short, and free of URLs, paths, braces, underscores and words such as
 * "exception" or "json") is adapted from OptimAI Agentic for Android, app/src/main/java/com/test/agenttrade/data/
 * UserFacingError.kt (MIT License, Copyright (c) 2026 OptimAI Agentic contributors; see NOTICE). Change for EdgeORE:
 * the technical text is not thrown away. EdgeORE users self-host, so the original message (for example a TLS pin
 * mismatch) stays available under "Show details", capped in length. Only the display changes; the controllers' error
 * values, codes and receipts are untouched.
 */
object PlainError {
    enum class Area(val target: String, val fallback: String) {
        ON_DEVICE_AI("the on-device model", "The on-device model could not run. Nothing was generated."),
        OWNED_HOST("your AI host", "Your AI host did not complete the request. No answer was produced."),
        NODE("your node", "The node request did not complete. Last known data is kept."),
    }

    const val MAX_DETAIL = 300

    private val technicalSymbols = listOf("://", "/", "{", "[", "_", "\\")
    private val technicalWords = listOf("http", "https", "tls", "ssl", "exception", "json", "localhost", "traceback", "stack", "null", "errno", "status code", "error code")

    /** True when the text already reads as plain language: short and without markers of an internal detail. */
    fun isPlain(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.isEmpty() || t.length > 140) return false
        val lowered = t.lowercase()
        if (technicalSymbols.any { lowered.contains(it) }) return false
        if (Regex("[A-Za-z]+(Exception|Error)\\b").containsMatchIn(t)) return false
        return technicalWords.none { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lowered) }
    }

    /** The sentence to show first. */
    fun headline(raw: String, area: Area): String {
        if (isPlain(raw)) return raw.trim()
        val l = raw.lowercase()
        return when {
            listOf("tls", "ssl", "certificate", "handshake").any { l.contains(it) } ->
                "The secure connection to ${area.target} was refused. Check the certificate SHA-256."
            listOf("timeout", "timed out").any { l.contains(it) } -> "${area.target.replaceFirstChar { it.uppercase() }} took too long to answer. Try again."
            listOf("unknownhost", "connectexception", "unreachable", "network error", "connection refused", "no route").any { l.contains(it) } ->
                "Couldn't reach ${area.target}. Check the network and the address."
            l.contains("allowlist") -> "The model list failed its check, so no on-device models are offered."
            else -> area.fallback
        }
    }

    /** The original text for "Show details", or null when the headline already is the original. */
    fun detail(raw: String): String? {
        if (isPlain(raw)) return null
        val t = raw.trim()
        return if (t.length <= MAX_DETAIL) t else t.take(MAX_DETAIL) + "… (${t.length - MAX_DETAIL} more characters not shown)"
    }
}

/** Error notice: plain headline, then an optional disclosure with the exact original text. */
@Composable
fun PlainNotice(raw: String, area: PlainError.Area) {
    var open by rememberSaveable(raw) { mutableStateOf(false) }
    val detail = PlainError.detail(raw)
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(dp4)) {
        Notice(PlainError.headline(raw, area), error = true)
        if (detail != null) {
            TextButton(onClick = { open = !open }) { Text(if (open) "Hide details" else "Show details", color = EdgeColors.textMuted) }
            if (open) Text(detail, style = EdgeType.mono(12), color = EdgeColors.textMuted)
        }
    }
}

private val dp4 = androidx.compose.ui.unit.Dp(4f)
