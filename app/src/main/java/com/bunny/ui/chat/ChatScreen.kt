package com.bunny.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bunny.data.remote.socket.ConnectionState
import com.bunny.domain.model.Message
import com.bunny.ui.common.ConnectionDot
import com.bunny.ui.common.MessageStatus
import com.bunny.ui.common.MessageStatusIcon
import com.bunny.ui.common.TypingIndicator
import com.bunny.ui.common.UserAvatar
import com.bunny.util.ThemeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    channelId: Int,
    modifier: Modifier = Modifier,
    embedded: Boolean = false
) {
    val viewModel: ChatViewModel = hiltViewModel()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageStatuses by viewModel.messageStatuses.collectAsStateWithLifecycle()
    val onlineUserIds by viewModel.onlineUserIds.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var channelName by remember { mutableStateOf("Channel $channelId") }
    var currentUser by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val prefs = navController.context.getSharedPreferences(com.bunny.util.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val currentTheme = ThemeUtils.getThemeFromString(prefs.getString(com.bunny.util.Constants.KEY_THEME, "dark"))

    LaunchedEffect(channelId) {
        viewModel.currentUser { user ->
            currentUser = user.id.let { if (it == 0) null else it }
        }
        viewModel.loadMessages(channelId) {}
        viewModel.subscribeToChannel(channelId)
        channelName = "Channel $channelId"
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(channelName, fontWeight = FontWeight.SemiBold)
                        val onlineCount = onlineUserIds.count { it != currentUser }
                        Text(
                            text = if (onlineCount > 0) "$onlineCount online"
                            else when (connectionState) {
                                is ConnectionState.Connected -> "Online"
                                is ConnectionState.Reconnecting -> "Reconnecting"
                                is ConnectionState.Connecting -> "Connecting"
                                is ConnectionState.Disconnected -> "Offline"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (!embedded) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    ConnectionDot(
                        state = connectionState,
                        modifier = Modifier.padding(end = 18.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )

            if (inputText.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TypingIndicator()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Typing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(messages.reversed(), key = { _, message -> message.id }) { reversedIndex, message ->
                    val chronologicalIndex = messages.size - 1 - reversedIndex
                    val isFirstInSequence = chronologicalIndex == 0 ||
                        messages[chronologicalIndex - 1].userId != message.userId
                    MessageItem(
                        message = message,
                        isCurrentUser = message.userId == (currentUser ?: 0),
                        showSender = isFirstInSequence,
                        status = messageStatuses[message.id],
                        onRetry = {
                            viewModel.retryMessage(channelId, message)
                        }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message #$channelName") },
                        modifier = Modifier.weight(1f),
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material.ripple.rememberRipple(
                                    color = Color.White.copy(alpha = 0.3f)
                                )
                            ) {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(channelId, inputText)
                                    inputText = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    isCurrentUser: Boolean,
    showSender: Boolean,
    status: MessageStatus? = null,
    onRetry: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isCurrentUser) {
            UserAvatar(
                imageUrl = message.user?.avatarUrl,
                username = message.user?.username ?: "?",
                size = 32.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        ) {
            if (!isCurrentUser && showSender) {
                Text(
                    text = message.user?.username ?: "Unknown",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
            ) {
                if (isCurrentUser) {
                    status?.let {
                        MessageStatusIcon(status = it, onRetry = onRetry, modifier = Modifier.padding(end = 6.dp, bottom = 2.dp))
                    }
                }
                Column(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomEnd = if (isCurrentUser) 4.dp else 16.dp,
                                bottomStart = if (isCurrentUser) 16.dp else 4.dp
                            )
                        )
                        .background(
                            if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (message.createdAt.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = message.createdAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!isCurrentUser) {
                    status?.let {
                        MessageStatusIcon(status = it, onRetry = onRetry, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
                    }
                }
            }
        }
    }
}
