package com.bunny.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bunny.data.remote.socket.ConnectionState
import com.bunny.ui.channels.ChannelViewModel
import com.bunny.ui.common.ConnectionDot
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.MembersPanelContent
import com.bunny.ui.common.PresenceDot
import com.bunny.ui.common.SystemMessage
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
    val isInitialLoading by viewModel.isInitialLoading.collectAsStateWithLifecycle()
    val initialError by viewModel.initialError.collectAsStateWithLifecycle()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val bottomPoke by viewModel.bottomPoke.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var channelName by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("Text channel") }
    var currentUser by remember { mutableStateOf<Int?>(null) }
    var serverIcon by remember { mutableStateOf<String?>(null) }
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
            serversViewModel.loadServers { result ->
                result.onSuccess { servers ->
                    servers.find { it.id == serverId }?.let { serverIcon = it.iconUrl }
                }
            }
        }
    }

    // Scroll to the newest message once the initial page has loaded.
    LaunchedEffect(isInitialLoading) {
        if (!isInitialLoading && messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Scroll to the bottom whenever a new message arrives at the end.
    LaunchedEffect(bottomPoke) {
        if (bottomPoke > 0) listState.animateScrollToItem(0)
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
            // Channel header: server/channel avatar + name + presence + actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!embedded) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
                UserAvatar(
                    imageUrl = serverIcon,
                    username = channelName.ifBlank { "#" }.take(1),
                    size = 36.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
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
                    com.bunny.ui.common.TypingIndicator()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Typing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                isInitialLoading -> {
                    MessageListSkeleton(modifier = Modifier.weight(1f))
                }
                initialError != null && messages.isEmpty() -> {
                    ChatErrorState(
                        message = initialError ?: "Failed to load messages",
                        onRetry = { viewModel.loadMessages(channelId) {} },
                        modifier = Modifier.weight(1f)
                    )
                }
                messages.isEmpty() -> {
                    EmptyChatState(
                        channelName = if (channelName.isBlank()) "$channelId" else channelName,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    // Chronological list rendered newest-first against the bottom.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(messages.asReversed(), key = { _, message -> message.id }) { reversedIndex, message ->
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
                            // Composed only when scrolled near the top, which
                            // triggers loading the next (older) page.
                            LaunchedEffect(Unit) {
                                if (hasMore) viewModel.loadOlderMessages()
                            }
                            if (isLoadingOlder) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else if (!hasMore) {
                                SystemMessage(
                                    text = "Start of conversation in #${if (channelName.isBlank()) channelId else channelName}"
                                )
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
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
private fun MessageListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(6) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.35f else 0.28f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.8f else 0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Forum,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Couldn't load messages",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
            Text("Retry", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyChatState(channelName: String, modifier: Modifier = Modifier) {
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
