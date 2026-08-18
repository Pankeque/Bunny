package com.bunny.ui.dms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bunny.data.remote.socket.ConnectionState
import com.bunny.domain.model.DirectMessage
import com.bunny.ui.common.ConnectionDot
import com.bunny.ui.common.ErrorDialog
import com.bunny.ui.common.MessageStatus
import com.bunny.ui.common.MessageStatusIcon
import com.bunny.ui.common.PresenceDot
import com.bunny.ui.common.TypingIndicator
import com.bunny.ui.common.UserAvatar
import com.bunny.ui.theme.BunnyAccent
import com.bunny.util.Constants
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmChatScreen(
    navController: NavController,
    conversationId: Int,
    userId: Int,
    username: String = "",
    modifier: Modifier = Modifier
) {
    val viewModel: DmChatViewModel = hiltViewModel()

    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageStatuses by viewModel.messageStatuses.collectAsStateWithLifecycle()
    val peerTyping by viewModel.peerTyping.collectAsStateWithLifecycle()
    val peerOnline by viewModel.peerOnline.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val myUserId = navController.context
        .getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        .getInt(Constants.KEY_USER_ID, 0)

    val peerName = conversation?.user?.username?.takeIf { it.isNotBlank() } ?: username.ifBlank { "User $userId" }
    val peerAvatar = conversation?.user?.avatarUrl

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId, userId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val presenceText = when {
        peerTyping -> "Typing…"
        peerOnline -> "Online"
        connectionState is ConnectionState.Reconnecting -> "Reconnecting…"
        connectionState is ConnectionState.Connecting -> "Connecting…"
        connectionState is ConnectionState.Disconnected -> "Offline"
        else -> "Offline"
    }

    fun send() {
        val text = inputText.trim()
        if (text.isEmpty()) return
        viewModel.sendMessage(text)
        inputText = ""
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                }
                UserAvatar(
                    imageUrl = peerAvatar,
                    username = peerName,
                    size = 34.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PresenceDot(
                            online = peerOnline,
                            modifier = Modifier.size(8.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = presenceText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                ConnectionDot(
                    state = connectionState,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )

            if (peerTyping) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TypingIndicator()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$peerName is typing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (messages.isEmpty()) {
                EmptyDmState(peerName = peerName, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages.reversed(), key = { it.id }) { message ->
                        DirectMessageItem(
                            message = message,
                            isCurrentUser = message.senderId == myUserId,
                            status = messageStatuses[message.id],
                            onRetry = { viewModel.retryMessage(message) }
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            viewModel.notifyTyping(it.isNotBlank())
                        },
                        placeholder = { Text("Message $peerName") },
                        modifier = Modifier.weight(1f),
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BunnyAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = BunnyAccent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { send() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BunnyAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Send,
                            contentDescription = "Send",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
        }
    }

    errorMessage?.let { ErrorDialog(message = it, onDismiss = { viewModel.dismissError() }) }
}

@Composable
private fun EmptyDmState(peerName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Forum,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Say hi to $peerName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "This is the beginning of your direct message history with $peerName.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp)
        )
    }
}

@Composable
fun DirectMessageItem(
    message: DirectMessage,
    isCurrentUser: Boolean,
    status: MessageStatus? = null,
    onRetry: () -> Unit = {}
) {
    if (isCurrentUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
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
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            UserAvatar(
                imageUrl = message.user?.avatarUrl,
                username = message.user?.username ?: "?",
                size = 30.dp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.user?.username ?: "Unknown",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shortTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    status?.let {
                        Spacer(modifier = Modifier.width(6.dp))
                        MessageStatusIcon(status = it, onRetry = onRetry)
                    }
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
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
