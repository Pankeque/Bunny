package com.bunny.ui.dms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import android.net.Uri
import com.bunny.domain.model.DirectConversation
import com.bunny.ui.common.BreathingGradientBackground
import com.bunny.ui.common.ErrorDialog
import com.bunny.ui.common.PresenceDot
import com.bunny.ui.common.UserAvatar
import com.bunny.ui.theme.BunnyAccent
import com.bunny.util.Constants
import com.bunny.util.ThemeUtils
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectMessagesScreen(navController: NavController, modifier: Modifier = Modifier) {
    val viewModel: DmInboxViewModel = hiltViewModel()
    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val currentTheme = ThemeUtils.getThemeFromString(prefs.getString(Constants.KEY_THEME, "dark"))

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val onlineUserIds by viewModel.onlineUserIds.collectAsStateWithLifecycle()
    val unreadByConversation by viewModel.unreadByConversation.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val myUserId = prefs.getInt(Constants.KEY_USER_ID, 0)

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    BreathingGradientBackground(theme = currentTheme, modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Messages", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            },
            bottomBar = { com.bunny.ui.BunnyBottomNav(navController) }
        ) { padding ->
            if (conversations.isEmpty()) {
                EmptyInbox(modifier = Modifier.fillMaxSize().padding(padding))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            online = onlineUserIds.contains(conversation.user.id),
                            unread = unreadByConversation[conversation.id] ?: 0,
                            myUserId = myUserId,
                            onClick = {
                                navController.navigate("dms/${conversation.id}/${conversation.user.id}?username=${Uri.encode(conversation.user.username)}")
                            }
                        )
                    }
                }
            }
        }
    }

    errorMessage?.let { ErrorDialog(message = it, onDismiss = { viewModel.dismissError() }) }
}

@Composable
private fun ConversationRow(
    conversation: DirectConversation,
    online: Boolean,
    unread: Int,
    myUserId: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            UserAvatar(
                imageUrl = conversation.user.avatarUrl,
                username = conversation.user.username,
                size = 44.dp
            )
            PresenceDot(
                online = online,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF0a0a0b))
                    .padding(2.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.user.username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                conversation.lastMessage?.let { last ->
                    Text(
                        text = shortTime(last.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = previewText(conversation, myUserId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (unread > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BunnyAccent)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (unread > 99) "99+" else "$unread",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    Divider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

private fun previewText(conversation: DirectConversation, myUserId: Int): String {
    val last = conversation.lastMessage ?: return "Start a conversation"
    val isMine = last.user?.id == myUserId || last.senderId == myUserId
    val senderPrefix = if (isMine) "You: " else ""
    return "$senderPrefix${last.content}"
}

@Composable
private fun EmptyInbox(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No conversations yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Send a message to a friend from your Friends tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

private fun shortTime(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = OffsetDateTime.parse(raw)
        parsed.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    } catch (e: Exception) {
        if (raw.length > 5) raw.takeLast(5) else raw
    }
}
