package com.bunny.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bunny.data.remote.socket.ConnectionState
import com.bunny.domain.model.Message
import com.bunny.ui.channels.ChannelViewModel
import com.bunny.ui.common.ConnectionDot
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.MembersPanelContent
import com.bunny.ui.common.MessageStatus
import com.bunny.ui.common.MessageStatusIcon
import com.bunny.ui.common.PresenceDot
import com.bunny.ui.common.SystemMessage
import com.bunny.ui.common.TypingIndicator
import com.bunny.ui.common.UserAvatar
import com.bunny.ui.servers.ServerViewModel
import com.bunny.ui.theme.BunnyAccent
import com.bunny.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    channelId: Int,
    serverId: Int = -1,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    onMembersClick: (() -> Unit)? = null
) {
    val viewModel: ChatViewModel = hiltViewModel()
    val channelsViewModel: ChannelViewModel = hiltViewModel()
    val serversViewModel: ServerViewModel = hiltViewModel()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageStatuses by viewModel.messageStatuses.collectAsStateWithLifecycle()
    val onlineUserIds by viewModel.onlineUserIds.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var channelName by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("Text channel") }
    var currentUser by remember { mutableStateOf<Int?>(null) }
    var showMembers by remember { mutableStateOf(false) }
    var intentToLeaveServer by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val myUsername = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
    val myAvatar = prefs.getString(Constants.KEY_AVATAR_URL, "") ?: ""

    LaunchedEffect(channelId) {
        viewModel.currentUser { user ->
            currentUser = user.id.let { if (it == 0) null else it }
        }
        viewModel.loadMessages(channelId) {}
        viewModel.subscribeToChannel(channelId)

        channelName = "Channel #$channelId"
        if (serverId > 0) {
            channelsViewModel.loadChannels(serverId) { result ->
                result.onSuccess { channels ->
                    channels.find { it.id == channelId }?.let {
                        channelName = it.name
                        channelDescription = "Text channel"
                    }
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val onlineCount = onlineUserIds.count { it != currentUser }
    val presenceText = when (connectionState) {
        is ConnectionState.Connected -> if (onlineCount > 0) "$onlineCount online" else "Connected"
        is ConnectionState.Reconnecting -> "Reconnecting…"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Disconnected -> "Offline"
    }

    fun send() {
        val text = inputText.trim()
        if (text.isEmpty()) return
        viewModel.sendMessage(channelId, text)
        inputText = ""
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Channel header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!embedded) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (channelName.isBlank()) "Channel #$channelId" else "#$channelName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PresenceDot(
                            online = connectionState is ConnectionState.Connected,
                            modifier = Modifier.size(8.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$presenceText • $channelDescription",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (onMembersClick != null) {
                    IconButton(onClick = onMembersClick) {
                        Icon(Icons.Outlined.Group, contentDescription = "Members")
                    }
                } else {
                    IconButton(onClick = { showMembers = true }) {
                        Icon(Icons.Outlined.Group, contentDescription = "Members")
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

            if (messages.isEmpty()) {
                EmptyChatState(
                    channelName = if (channelName.isBlank()) "$channelId" else channelName,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    itemsIndexed(messages.reversed(), key = { _, message -> message.id }) { reversedIndex, message ->
                        val chronologicalIndex = messages.size - 1 - reversedIndex
                        if (message.userId <= 0) {
                            SystemMessage(text = message.content)
                        } else {
                            MessageItem(
                                message = message,
                                isCurrentUser = message.userId == (currentUser ?: 0),
                                showHeader = chronologicalIndex == 0 ||
                                    messages[chronologicalIndex - 1].userId != message.userId,
                                status = messageStatuses[message.id],
                                onRetry = {
                                    viewModel.retryMessage(channelId, message)
                                }
                            )
                        }
                    }
                    item(key = "start") {
                        SystemMessage(
                            text = "Start of conversation in #${if (channelName.isBlank()) channelId else channelName}"
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
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message in #${if (channelName.isBlank()) channelId else channelName}") },
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
                    Spacer(modifier = Modifier.width(10.dp))
                    SendButton(enabled = inputText.isNotBlank(), onClick = { send() })
                }
            }
        }
    }

    if (showMembers) {
        ModalBottomSheet(
            onDismissRequest = { showMembers = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            MembersPanelContent(
                myUsername = myUsername,
                myAvatar = myAvatar,
                connectionState = connectionState,
                onlineCount = onlineCount,
                serverId = serverId,
                onEditProfile = {
                    showMembers = false
                    navController.navigate("profile/edit")
                },
                onServerSettings = {
                    showMembers = false
                    navController.navigate("servers/$serverId/settings")
                },
                onLeaveServer = {
                    showMembers = false
                    intentToLeaveServer = true
                },
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }
    }

    if (intentToLeaveServer && serverId > 0) {
        ConfirmDialog(
            title = "Leave server",
            message = "Are you sure you want to leave this server?",
            onConfirm = {
                intentToLeaveServer = false
                serversViewModel.leaveServer(serverId) { _ ->
                    navController.navigate("servers") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
            onDismiss = { intentToLeaveServer = false }
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "sendScale"
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) BunnyAccent else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(
                    color = Color.White.copy(alpha = 0.25f)
                ),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Send,
            contentDescription = "Enviar",
            tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun EmptyChatState(channelName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        com.bunny.ui.common.BunnyLogoMark(size = 48.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Start a conversation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "This channel has no messages yet.\nBe the first person to write in #$channelName.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp)
        )
    }
}

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
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.Top
        ) {
            UserAvatar(
                imageUrl = message.user?.avatarUrl,
                username = message.user?.username ?: "?",
                size = 34.dp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.user?.username ?: "Desconhecido",
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


// Formats a short timestamp (HH:mm) or returns the raw tail segment
private fun shortTime(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = java.time.OffsetDateTime.parse(raw)
        parsed.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        if (raw.length > 5) raw.takeLast(5) else raw
    }
}
