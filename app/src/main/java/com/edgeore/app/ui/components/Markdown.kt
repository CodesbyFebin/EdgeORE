package com.edgeore.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/*
 * Inline Markdown for model output: **bold**, *italic* / _italic_, `code` and [label](url).
 *
 * Adapted from OptimAI Agentic for Android, app/src/main/java/com/test/agenttrade/ui/components/Markdown.kt
 * (MIT License, Copyright (c) 2026 OptimAI Agentic contributors; see NOTICE). Change for EdgeORE: links are NOT made
 * clickable. Model output is untrusted text, so a link renders as "label (url)" with the full destination visible and
 * nothing opens from a tap (design.md §3: external links must show their destination before leaving). Anything
 * unmatched stays literal, so a parse problem never hides part of an answer.
 */
fun markdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(markdown(text.substring(i + 2, end))) }
                    i = end + 2
                    continue
                }
            }
            rest.startsWith("`") -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                    continue
                }
            }
            rest.startsWith("[") -> {
                val match = LINK.find(rest)
                if (match != null) {
                    val (label, url) = match.destructured
                    append(label)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(" ($url)") }
                    i += match.value.length
                    continue
                }
            }
            (rest.startsWith("*") || rest.startsWith("_")) && rest.length > 1 && !rest[1].isWhitespace() -> {
                val marker = rest[0]
                val end = text.indexOf(marker, i + 1)
                if (end > i + 1 && !text[end - 1].isWhitespace()) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                    continue
                }
            }
        }
        append(text[i])
        i++
    }
}

private val LINK = Regex("^\\[([^\\]]+)]\\(([^)\\s]+)\\)")
