package com.edgeore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeRadius
import com.edgeore.app.ui.theme.EdgeSpacing

/*
 * Chat rows for the Private AI screen: the model's reply on the left next to a small avatar, the user's message on the
 * right in a high-contrast bubble, and a static "working" row.
 *
 * Layout adapted from OptimAI Agentic for Android, app/src/main/java/com/test/agenttrade/ui/agent/AgentChatScreen.kt
 * (AgentAvatar, AgentBubble, UserBubble, ThinkingRow; MIT License, Copyright (c) 2026 OptimAI Agentic contributors;
 * see NOTICE). Changes for EdgeORE: the avatar is the native E mark, every bubble names its source in text ("You",
 * "On-device model", "Owned host") so it never relies on side or colour, the working row does not animate
 * (design.md §12: no looping ornament), and these rows only display messages the real controllers produced.
 * There is no greeting or canned answer.
 */

private val AVATAR = 32.dp

@Composable
private fun ModelAvatar() {
    Box(Modifier.size(AVATAR).background(EdgeColors.surfaceInset, CircleShape), contentAlignment = Alignment.Center) { EMark(16.dp) }
}

/** A model reply. [source] names where it ran ("On-device model", "Owned host · <model>"). */
@Composable
fun ModelBubble(text: String, source: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$source said: $text" }, horizontalArrangement = Arrangement.spacedBy(EdgeSpacing.sm)) {
        ModelAvatar()
        Column(
            Modifier.weight(1f, fill = false).background(EdgeColors.surfaceInset, RoundedCornerShape(EdgeRadius.card)).padding(horizontal = EdgeSpacing.md, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(source, style = MaterialTheme.typography.labelSmall, color = EdgeColors.mint)
            Text(markdown(text), style = MaterialTheme.typography.bodyLarge, color = EdgeColors.textPrimary)
        }
        Spacer(Modifier.width(EdgeSpacing.xl))
    }
}

@Composable
fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "You said: $text" }) {
        Spacer(Modifier.weight(1f).widthIn(min = 48.dp))
        Column(
            Modifier.background(EdgeColors.textPrimary, RoundedCornerShape(EdgeRadius.card)).padding(horizontal = EdgeSpacing.md, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("You", style = MaterialTheme.typography.labelSmall, color = EdgeColors.background.copy(alpha = 0.75f))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = EdgeColors.background)
        }
    }
}

/** Shown only while a real request is running; announced once, not animated. */
@Composable
fun WorkingRow(text: String) {
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(EdgeSpacing.sm), verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelAvatar()
        Text(text, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted,
            modifier = Modifier.background(EdgeColors.surfaceInset, RoundedCornerShape(EdgeRadius.card)).padding(horizontal = EdgeSpacing.md, vertical = 10.dp))
    }
}

/** What the conversation area shows before anything has been sent. */
@Composable
fun EmptyChat(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(EdgeSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        IconTile(EdgeIcons.Ai, tint = EdgeColors.textMuted)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted, modifier = Modifier.weight(1f))
    }
}
