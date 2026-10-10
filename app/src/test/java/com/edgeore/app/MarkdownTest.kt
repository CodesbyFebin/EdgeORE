package com.edgeore.app

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.edgeore.app.ui.components.markdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Inline Markdown for model output: styling, literal fallback, and links that are shown but not clickable. */
class MarkdownTest {
    @Test fun markdownStylesInlineMarkupAndKeepsUnmatchedText() {
        val a = markdown("Use **bold** and `code` and *it* but 2 * 3 stays")
        assertEquals("Use bold and code and it but 2 * 3 stays", a.text)
        assertTrue(a.spanStyles.any { it.item.fontWeight == FontWeight.Bold && a.text.substring(it.start, it.end) == "bold" })
        assertTrue(a.spanStyles.any { it.item.fontFamily == FontFamily.Monospace && a.text.substring(it.start, it.end) == "code" })
        assertEquals("**unclosed", markdown("**unclosed").text)
    }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    @Test fun modelLinksShowTheirDestinationAndAreNotClickable() {
        val a = markdown("see [docs](https://example.org/x)")
        assertEquals("see docs (https://example.org/x)", a.text)
        assertTrue(a.getLinkAnnotations(0, a.length).isEmpty())
        assertTrue(a.getUrlAnnotations(0, a.length).isEmpty())
    }
}
