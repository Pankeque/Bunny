package com.bunny.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunny.domain.model.Message
import com.bunny.ui.common.MessageStatus
import com.bunny.ui.common.MessageStatusIcon
import com.bunny.ui.common.UserAvatar
import com.bunny.ui.theme.BunnyAccent
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A single chat message row.
 *
 * Others' messages use a grid layout: a 40dp avatar on the left and the
 * username (tinted with the sender's role color, when available), a clear-text
 * timestamp, and the content on the right. Consecutive messages from the same
 * author only render the header/avatar on the first row.
 *
 * The current user's messages are rendered as a right-aligned bubble with the
 * send status and timestamp.
 */
@Composable
fun MessageItem(
    message: Message,
    isCurrentUser: Boolean,
    showHeader: Boolean,
    status: MessageStatus? = null,
    onRetry: () -> Unit = {}
) {
    if (isCurrentUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.widthIn(max = 320.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    status?.let {
                        MessageStatusIcon(status = it, onRetry = onRetry, modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(
                        text = shortTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "you",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BunnyAccent
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (showHeader) 4.dp else 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (showHeader) {
            UserAvatar(
                imageUrl = message.user?.avatarUrl,
                username = message.user?.username ?: "?",
                size = 40.dp,
                modifier = Modifier.padding(top = 2.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.user?.username ?: "Unknown",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = roleColorOf(message.roleColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    status?.let {
                        Spacer(modifier = Modifier.width(6.dp))
                        MessageStatusIcon(status = it, onRetry = onRetry)
                    }
                }
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (showHeader) 2.dp else 0.dp)
            )
        }
    }
}

// Parses a "#RRGGBB" role color, falling back to the theme's on-surface color.
private fun roleColorOf(hex: String?): Color {
    if (hex.isNullOrBlank()) return MaterialTheme.colorScheme.onSurface
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: IllegalArgumentException) {
        MaterialTheme.colorScheme.onSurface
    }
}

// Formats a timestamp in clear text, e.g. "Today at 14:30" or "Aug 18 at 09:12".
private fun formatMessageTime(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = OffsetDateTime.parse(raw)
        val local = parsed.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        val now = java.time.LocalDateTime.now()
        val time = local.format(DateTimeFormatter.ofPattern("HH:mm"))
        when {
            local.toLocalDate() == now.toLocalDate() -> "Today at $time"
            local.toLocalDate() == now.minusDays(1).toLocalDate() -> "Yesterday at $time"
            local.year == now.year -> local.format(DateTimeFormatter.ofPattern("MMM d 'at' HH:mm", Locale.getDefault()))
            else -> local.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm", Locale.getDefault()))
        }
    } catch (e: Exception) {
        raw
    }
}

// Formats a short timestamp (HH:mm) for the current user's bubble.
private fun shortTime(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = OffsetDateTime.parse(raw)
        parsed.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        if (raw.length > 5) raw.takeLast(5) else raw
    }
}
