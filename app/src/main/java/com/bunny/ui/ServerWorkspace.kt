package com.bunny.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.bunny.domain.model.Channel
import com.bunny.domain.model.Server
import com.bunny.ui.channels.ChannelViewModel
import com.bunny.ui.channels.CreateChannelDialog
import com.bunny.ui.chat.ChatScreen
import com.bunny.ui.chat.ChatViewModel
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.MembersPanelContent
import com.bunny.ui.common.OverlappingPanelsHost
import com.bunny.ui.common.rememberOverlappingPanelsState
import com.bunny.ui.servers.ChannelPane
import com.bunny.ui.servers.CreateServerDialog
import com.bunny.ui.servers.InviteCodeDialog
import com.bunny.ui.servers.JoinServerDialog
import com.bunny.ui.servers.RailCircleButton
import com.bunny.ui.servers.ServerRail
import com.bunny.ui.servers.ServerViewModel
import com.bunny.util.isLandscape
import com.bunny.util.isMasterDetail
import kotlinx.coroutines.launch

// Three-panel Discord-style workspace:
//   start panel  -> server rail + channel list (slides in from the left)
//   center panel -> chat (full-screen base layer)
//   end panel    -> members / settings (slides in from the right)
// Swiping horizontally anywhere opens and closes the side panels with a
// spring animation. The back button closes the panels first.
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
    val chatViewModel: ChatViewModel = hiltViewModel()
    val connectionState by chatViewModel.connectionState.collectAsStateWithLifecycle()
    val wide = isMasterDetail()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var servers by remember { mutableStateOf<List<Server>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var channelServerId by remember { mutableStateOf<Int?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var intentToLeaveServer by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<Channel?>(null) }
    var createdServer by remember { mutableStateOf<Server?>(null) }

    val panelsState = rememberOverlappingPanelsState(
        startPanelWidth = 333.dp,
        endPanelWidth = 272.dp,
        startInitiallyOpen = wide
    )

    LaunchedEffect(wide) {
        panelsState.startPinned = wide
        panelsState.snapTo(startOpen = wide, endOpen = false)
    }

    LaunchedEffect(Unit) {
        serversViewModel.loadServers { result ->
            result.onSuccess { servers = it }
        }
    }

    LaunchedEffect(selectedServerId) {
        val serverId = selectedServerId
        if (serverId != null) {
            channelServerId = serverId
            channelsViewModel.loadChannels(serverId) { result ->
                result.onSuccess { channels = it }
            }
        }
    }

    LaunchedEffect(selectedChannelId, servers) {
        val channelId = selectedChannelId
        if (channelId != null && channelServerId == null && servers.isNotEmpty()) {
            resolveServerForChannel(channelId, servers, channelsViewModel) { serverId ->
                if (serverId != null) {
                    channelServerId = serverId
                    channelsViewModel.loadChannels(serverId) { result ->
                        result.onSuccess { channels = it }
                    }
                }
            }
        }
    }

    val activeServer = servers.find { it.id == channelServerId }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { if (!isLandscape()) BunnyBottomNav(navController) },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            OverlappingPanelsHost(
                state = panelsState,
                wide = wide,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                startPanel = {
                    Row(modifier = Modifier.fillMaxSize()) {
                        ServerRail(
                            servers = servers,
                            selectedServerId = selectedServerId,
                            onServerClick = { server ->
                                onServerSelected(server.id)
                                channelServerId = server.id
                                channelsViewModel.loadChannels(server.id) { r -> r.onSuccess { channels = it } }
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
                                if (!wide) {
                                    scope.launch { panelsState.closePanels() }
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
                    }
                },
                centerPanel = {
                    if (selectedChannelId != null) {
                        ChatScreen(
                            navController = navController,
                            channelId = selectedChannelId,
                            serverId = activeServer?.id ?: -1,
                            modifier = Modifier.fillMaxSize(),
                            embedded = true,
                            onMembersClick = { scope.launch { panelsState.openEndPanel() } }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Swipe right to open the channel list",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                },
                endPanel = {
                    val prefs = navController.context.getSharedPreferences(
                        com.bunny.util.Constants.PREFS_NAME,
                        android.content.Context.MODE_PRIVATE
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        MembersPanelContent(
                            myUsername = prefs.getString(com.bunny.util.Constants.KEY_USERNAME, "") ?: "",
                            myAvatar = prefs.getString(com.bunny.util.Constants.KEY_AVATAR_URL, "") ?: "",
                            connectionState = connectionState,
                            onlineCount = 0,
                            serverId = activeServer?.id ?: -1,
                            onEditProfile = { navController.navigate("profile/edit") },
                            onServerSettings = {
                                val serverId = activeServer?.id
                                if (serverId != null) navController.navigate("servers/$serverId/settings")
                            },
                            onLeaveServer = { intentToLeaveServer = true },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 20.dp, bottom = 20.dp)
                        )
                    }
                }
            )
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
                        channelServerId = joined.id
                        channelsViewModel.loadChannels(joined.id) { r -> r.onSuccess { channels = it } }
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

private fun resolveServerForChannel(
    channelId: Int,
    servers: List<Server>,
    viewModel: ChannelViewModel,
    onResolved: (Int?) -> Unit
) {
    fun tryNext(index: Int) {
        if (index >= servers.size) {
            onResolved(null)
            return
        }
        viewModel.loadChannels(servers[index].id) { result ->
            val hasChannel = result.getOrNull()?.any { it.id == channelId } == true
            if (hasChannel) {
                onResolved(servers[index].id)
            } else {
                tryNext(index + 1)
            }
        }
    }
    tryNext(0)
}
