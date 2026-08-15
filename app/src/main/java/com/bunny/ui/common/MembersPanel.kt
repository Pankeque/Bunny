package com.bunny.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunny.data.remote.socket.ConnectionState

// Profile/members panel: avatar, status, presence, and quick actions.
// Reused as a bottom sheet (mobile) and side panel (tablet/landscape).
@Composable
fun MembersPanelContent(
    myUsername: String,
    myAvatar: String,
    connectionState: ConnectionState,
    onlineCount: Int,
    serverId: Int,
    onEditProfile: () -> Unit,
    onServerSettings: () -> Unit,
    onLeaveServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = connectionState is ConnectionState.Connected
    val statusText = when (connectionState) {
        is ConnectionState.Connected -> if (onlineCount > 0) "Connected • $onlineCount online" else "Connected"
        is ConnectionState.Reconnecting -> "Reconnecting…"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Disconnected -> "Offline"
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(imageUrl = myAvatar, username = myUsername, size = 56.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = myUsername.ifBlank { "You" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PresenceDot(online = isOnline)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "ACTIONS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        MemberActionRow(icon = Icons.Outlined.Edit, label = "Edit profile", onClick = onEditProfile)
        if (serverId > 0) {
            MemberActionRow(icon = Icons.Outlined.Settings, label = "Server settings", onClick = onServerSettings)
            MemberActionRow(
                icon = Icons.Outlined.Key,
                label = "Leave server",
                destructive = true,
                onClick = onLeaveServer
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "MEMBERS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(imageUrl = myAvatar, username = myUsername, size = 32.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = myUsername.ifBlank { "You" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            PresenceDot(online = isOnline)
        }
        Text(
            text = if (onlineCount > 0) "…and $onlineCount more person${if (onlineCount == 1) "" else "s"} online"
            else "Online members appear here as they connect",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun MemberActionRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
