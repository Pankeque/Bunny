package com.bunny.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
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
import android.net.Uri
import com.bunny.domain.model.FriendUser
import com.bunny.domain.model.FriendshipStatus
import com.bunny.domain.model.User
import com.bunny.ui.common.BreathingGradientBackground
import com.bunny.ui.common.ErrorDialog
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.PresenceDot
import com.bunny.ui.common.UserAvatar
import com.bunny.ui.dms.DmInboxViewModel
import com.bunny.util.Constants
import com.bunny.util.ThemeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(navController: NavController, modifier: Modifier = Modifier) {
    val viewModel: FriendsViewModel = hiltViewModel()
    val dmInboxViewModel: DmInboxViewModel = hiltViewModel()
    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val currentTheme = ThemeUtils.getThemeFromString(prefs.getString(Constants.KEY_THEME, "dark"))

    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoing.collectAsStateWithLifecycle()
    val onlineUserIds by viewModel.onlineUserIds.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<FriendUser?>(null) }
    var pendingBlock by remember { mutableStateOf<FriendUser?>(null) }

    val tabs = listOf("Online", "All", "Pending")
    val myUserId = prefs.getInt(Constants.KEY_USER_ID, 0)

    BreathingGradientBackground(theme = currentTheme, modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Friends", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = "Add Friend")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            },
            bottomBar = { com.bunny.ui.BunnyBottomNav(navController) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        val count = when (index) {
                            0 -> friends.count { onlineUserIds.contains(it.id) }
                            1 -> friends.size
                            else -> incoming.size + outgoing.size
                        }
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text("$title ($count)", maxLines = 1)
                            }
                        )
                    }
                }

                val list = when (selectedTab) {
                    0 -> friends.filter { onlineUserIds.contains(it.id) }
                    1 -> friends
                    else -> incoming + outgoing
                }

                if (list.isEmpty()) {
                    EmptyFriendsState(tabTitle = tabs[selectedTab], modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(list, key = { it.id }) { friend ->
                            FriendRow(
                                friend = friend,
                                isIncoming = friend.isIncoming,
                                online = onlineUserIds.contains(friend.id),
                                myUserId = myUserId,
                                onAccept = { viewModel.acceptRequest(friend) },
                                onDecline = { viewModel.declineRequest(friend) },
                                onCancel = { viewModel.cancelRequest(friend) },
                                onMessage = {
                                    dmInboxViewModel.openConversation(friend.id) { result ->
                                        result.onSuccess { conv ->
                                            navController.navigate(
                                                "dms/${conv.id}/${conv.user.id}?username=${Uri.encode(conv.user.username)}"
                                            )
                                        }
                                    }
                                },
                                onRemove = { pendingRemove = friend },
                                onBlock = { pendingBlock = friend }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFriendDialog(
            onDismiss = { showAddDialog = false },
            onSendRequest = { username, onDone ->
                viewModel.sendRequest(username, onDone)
            },
            searchUsers = { query -> viewModel.search(query) },
            searchResults = viewModel.searchResults.collectAsStateWithLifecycle().value,
            isSearching = viewModel.isSearching.collectAsStateWithLifecycle().value,
            myUserId = myUserId
        )
    }

    pendingRemove?.let { friend ->
        ConfirmDialog(
            title = "Remove friend",
            message = "Remove ${friend.username} from your friends?",
            onConfirm = {
                viewModel.removeFriend(friend)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null }
        )
    }

    pendingBlock?.let { friend ->
        ConfirmDialog(
            title = "Block user",
            message = "Block ${friend.username}? You will no longer be friends and they cannot message you.",
            onConfirm = {
                viewModel.blockUser(friend.id)
                pendingBlock = null
            },
            onDismiss = { pendingBlock = null }
        )
    }

    errorMessage?.let { ErrorDialog(message = it, onDismiss = { viewModel.dismissError() }) }
}

@Composable
private fun FriendRow(
    friend: FriendUser,
    isIncoming: Boolean,
    online: Boolean,
    myUserId: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onMessage: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            UserAvatar(
                imageUrl = friend.avatarUrl,
                username = friend.username,
                size = 40.dp
            )
            if (friend.status == FriendshipStatus.Accepted) {
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
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.username,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = statusLabel(friend, online),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (friend.status == FriendshipStatus.Pending) {
            if (isIncoming) {
                IconButton(onClick = onAccept, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Accept",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDecline, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Decline",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = onCancel, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Cancel request",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            IconButton(
                onClick = onMessage,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Message",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun statusLabel(friend: FriendUser, online: Boolean): String = when {
    friend.status == FriendshipStatus.Pending && friend.isIncoming -> "Incoming friend request"
    friend.status == FriendshipStatus.Pending -> "Awaiting response"
    online -> "Online"
    else -> "Offline"
}

@Composable
private fun EmptyFriendsState(tabTitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No friends here yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (tabTitle) {
                "Online" -> "No friends online right now.\nThey will appear here when they come online."
                "Pending" -> "No pending friend requests.\nUse the + button to add a friend."
                else -> "Your friends will appear here.\nUse the + button to add a friend."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun AddFriendDialog(
    onDismiss: () -> Unit,
    onSendRequest: (String, (Result<Unit>) -> Unit) -> Unit,
    searchUsers: (String) -> Unit,
    searchResults: List<User>,
    isSearching: Boolean,
    myUserId: Int
) {
    var username by remember { mutableStateOf("") }
    var sentUsername by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Add Friend", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        sentUsername = null
                        searchUsers(it)
                    },
                    placeholder = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Type a username and select a result to send a friend request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(28.dp)
                    )
                } else if (username.isNotBlank() && searchResults.isEmpty()) {
                    Text(
                        text = "No users found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    searchResults
                        .filter { it.id != myUserId }
                        .take(6)
                        .forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSendRequest(user.username) { result ->
                                            if (result.isSuccess) {
                                                sentUsername = user.username
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    imageUrl = user.avatarUrl,
                                    username = user.username,
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = user.username,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                }

                sentUsername?.let { name ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Friend request sent to $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
