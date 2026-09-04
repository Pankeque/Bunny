package com.bunny.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.bunny.domain.model.Channel
import com.bunny.domain.model.Server
import com.bunny.ui.channels.ChannelViewModel
import com.bunny.ui.channels.CreateChannelDialog
import com.bunny.ui.chat.ChatScreen
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.servers.ChannelPane
import com.bunny.ui.servers.CreateServerDialog
import com.bunny.ui.servers.InviteCodeDialog
import com.bunny.ui.servers.JoinServerDialog
import com.bunny.ui.servers.RailCircleButton
import com.bunny.ui.servers.ServerRail
import com.bunny.ui.servers.ServerViewModel
import kotlinx.coroutines.launch

// Master-detail workspace:
//   - Portrait/mobile (< 600dp): server rail + channel list, full-screen chat
//     when a channel is selected (navigation happens via the route stack).
//   - Landscape / tablet (>= 600dp): server rail + channel list + chat in
//     a static three-column layout, no overlay panels.
@Composable
fun ServerWorkspace(
    navController: NavHostController,
    selectedServerId: Int?,
    selectedChannelId: Int?,
    onServerSelected: (Int) -> Unit,
    onChannelSelected: (Int) -> Unit
) {
    val serversViewModel: ServerViewModel = hiltViewModel()
    val channelsViewModel: ChannelViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600

    var servers by remember { mutableStateOf<List<Server>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var channelServerId by remember { mutableStateOf<Int?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var intentToLeaveServer by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<Channel?>(null) }
    var createdServer by remember { mutableStateOf<Server?>(null) }

    // Load servers once on first composition.
    LaunchedEffect(Unit) {
        serversViewModel.loadServers { result ->
            result.onSuccess { loaded ->
                servers = loaded
                // Auto-select the first server if none is selected yet.
                if (selectedServerId == null) {
                    loaded.firstOrNull()?.let { onServerSelected(it.id) }
                }
            }
        }
    }

    // Keep the active server id in sync with the navigation state. The active
    // server id is what we actually load channels for, regardless of whether
    // a channel is selected (the deep-link case is handled below).
    LaunchedEffect(selectedServerId) {
        val serverId = selectedServerId
        if (serverId != null && serverId != channelServerId) {
            channelServerId = serverId
        }
    }

    // Load channels whenever the active server changes.
    LaunchedEffect(channelServerId) {
        val serverId = channelServerId
        if (serverId != null) {
            channelsViewModel.loadChannels(serverId) { result ->
                result.onSuccess { channels = it }
            }
        } else {
            channels = emptyList()
        }
    }

    val activeServer = servers.find { it.id == channelServerId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // In wide layouts the rail replaces the bottom nav, so hide it.
            if (!isWide) BunnyBottomNav(navController)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ServerRail(
                servers = servers,
                selectedServerId = selectedServerId,
                onServerClick = { server ->
                    onServerSelected(server.id)
                },
                onDmClick = {
                    navController.navigate("dms") {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                },
                onCreateClick = { showCreateDialog = true },
                onJoinClick = { showJoinDialog = true },
                modifier = Modifier.width(72.dp),
                bottomExtra = {
                    Spacer(modifier = Modifier.height(8.dp))
                    RailCircleButton(
                        icon = Icons.Outlined.Person,
                        description = "Profile",
                        onClick = {
                            navController.navigate("profile") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            ChannelPane(
                server = activeServer,
                channels = channels,
                selectedChannelId = selectedChannelId,
                onChannelClick = { channel ->
                    onChannelSelected(channel.id)
                    if (!isWide) {
                        // In portrait, navigate to the dedicated chat screen so
                        // the chat occupies the full viewport.
                        val serverId = activeServer?.id ?: channel.serverId
                        navController.navigate("chat/${channel.id}?serverId=$serverId") {
                            launchSingleTop = true
                        }
                    }
                },
                onChannelSettings = { channel ->
                    val serverId = activeServer?.id ?: channel.serverId
                    navController.navigate("channels/$serverId/${channel.id}/settings")
                },
                onChannelDelete = { channel -> channelToDelete = channel },
                onCreateChannel = { showCreateChannelDialog = true },
                onServerSettings = {
                    val serverId = activeServer?.id
                    if (serverId != null) {
                        navController.navigate("servers/$serverId/settings")
                    }
                },
                onLeaveServer = { intentToLeaveServer = true },
                modifier = Modifier.width(260.dp)
            )
            if (isWide) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                if (selectedChannelId != null && activeServer != null) {
                    ChatScreen(
                        navController = navController,
                        channelId = selectedChannelId,
                        serverId = activeServer.id,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        embedded = true,
                        onMembersClick = null
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Tag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Select a channel to start chatting",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateServerDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                serversViewModel.createServer(name) { result ->
                    showCreateDialog = false
                    result.onSuccess { server ->
                        createdServer = server
                        onServerSelected(server.id)
                        serversViewModel.loadServers { r -> r.onSuccess { servers = it } }
                    }
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinServerDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { code ->
                serversViewModel.joinServer(code) { result ->
                    result.onSuccess { joined ->
                        showJoinDialog = false
                        onServerSelected(joined.id)
                        scope.launch { snackbarHostState.showSnackbar("You joined ${joined.name}") }
                        serversViewModel.loadServers { r -> r.onSuccess { servers = it } }
                    }.onFailure { e ->
                        scope.launch {
                            snackbarHostState.showSnackbar(e.message ?: "Invalid or expired invite code")
                        }
                    }
                }
            }
        )
    }

    if (showCreateChannelDialog) {
        val serverId = activeServer?.id
        if (serverId != null) {
            CreateChannelDialog(
                onDismiss = { showCreateChannelDialog = false },
                onConfirm = { name ->
                    channelsViewModel.createChannel(serverId, name, "text") { result ->
                        showCreateChannelDialog = false
                        result.onSuccess {
                            channelsViewModel.loadChannels(serverId) { r -> r.onSuccess { channels = it } }
                        }
                    }
                }
            )
        } else {
            showCreateChannelDialog = false
        }
    }

    channelToDelete?.let { channel ->
        ConfirmDialog(
            title = "Delete channel",
            message = "Delete channel ${channel.name}? All messages will be removed.",
            onConfirm = {
                channelsViewModel.deleteChannel(channel.id) { result ->
                    result.onSuccess {
                        val serverId = activeServer?.id
                        if (serverId != null) {
                            channelsViewModel.loadChannels(serverId) { r -> r.onSuccess { channels = it } }
                        }
                    }
                }
                channelToDelete = null
            },
            onDismiss = { channelToDelete = null }
        )
    }

    if (intentToLeaveServer) {
        ConfirmDialog(
            title = "Leave server",
            message = "Are you sure you want to leave this server?",
            onConfirm = {
                intentToLeaveServer = false
                val serverId = activeServer?.id
                if (serverId != null) {
                    serversViewModel.leaveServer(serverId) { _ ->
                        serversViewModel.loadServers { r -> r.onSuccess { servers = it } }
                        navController.navigate("servers") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            },
            onDismiss = { intentToLeaveServer = false }
        )
    }

    createdServer?.let { server ->
        InviteCodeDialog(
            server = server,
            onDismiss = { createdServer = null }
        )
    }
}
