package com.bunny.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.bunny.domain.model.Channel
import com.bunny.domain.model.Server
import com.bunny.ui.channels.ChannelViewModel
import com.bunny.ui.channels.CreateChannelDialog
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.ShimmerBox
import com.bunny.ui.theme.BunnyAccent
import com.bunny.ui.theme.BunnyDialogGray
import kotlinx.coroutines.launch

// Mobile-first home: server sidebar (rail) + channels of the active server.
@Composable
fun ServerListScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    initialServerId: Int? = null
) {
    ServersHome(navController = navController, modifier = modifier, initialServerId = initialServerId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersHome(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    initialServerId: Int? = null
) {
    val serversViewModel: ServerViewModel = hiltViewModel()
    val channelsViewModel: ChannelViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var servers by remember { mutableStateOf<List<Server>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedServerId by remember { mutableStateOf<Int?>(initialServerId) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var intentToLeave by remember { mutableStateOf<Server?>(null) }
    var channelToDelete by remember { mutableStateOf<Channel?>(null) }
    var createdServer by remember { mutableStateOf<Server?>(null) }

    LaunchedEffect(Unit) {
        serversViewModel.loadServers { result ->
            result.onSuccess { loaded ->
                servers = loaded
                val current = selectedServerId
                if (loaded.isEmpty()) {
                    selectedServerId = null
                } else if (current == null || loaded.none { it.id == current }) {
                    selectedServerId = loaded.first().id
                }
            }.onFailure { e ->
                errorMessage = e.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedServerId) {
        val serverId = selectedServerId
        if (serverId != null) {
            channelsViewModel.loadChannels(serverId) { result ->
                result.onSuccess { channels = it }
                    .onFailure { e -> errorMessage = e.message }
            }
        } else {
            channels = emptyList()
        }
    }

    val activeServer = servers.find { it.id == selectedServerId }

    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { com.bunny.ui.BunnyBottomNav(navController) },
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
                    onServerClick = { selectedServerId = it.id },
                    onCreateClick = { showCreateDialog = true },
                    onJoinClick = { showJoinDialog = true },
                    modifier = Modifier.width(64.dp)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                if (isLoading) {
                    ServerPaneSkeleton(modifier = Modifier.weight(1f))
                } else if (activeServer == null) {
                    EmptyServersPane(
                        onCreate = { showCreateDialog = true },
                        onJoin = { showJoinDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ChannelPane(
                        server = activeServer,
                        channels = channels,
                        selectedChannelId = null,
                        onChannelClick = { channel ->
                            navController.navigate("chat/${channel.id}?serverId=${activeServer.id}")
                        },
                        onChannelSettings = { channel ->
                            navController.navigate("channels/${activeServer.id}/${channel.id}/settings")
                        },
                        onChannelDelete = { channel -> channelToDelete = channel },
                        onCreateChannel = { showCreateChannelDialog = true },
                        onServerSettings = { navController.navigate("servers/${activeServer.id}/settings") },
                        onLeaveServer = { intentToLeave = activeServer },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateServerDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                isLoading = true
                serversViewModel.createServer(name) { result ->
                    showCreateDialog = false
                    isLoading = false
                    result.onSuccess { server ->
                        createdServer = server
                        selectedServerId = server.id
                        serversViewModel.loadServers { r -> r.onSuccess { servers = it } }
                    }.onFailure { e ->
                        errorMessage = e.message
                    }
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinServerDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { code ->
                isLoading = true
                serversViewModel.joinServer(code) { result ->
                    isLoading = false
                    result.onSuccess { joined ->
                        showJoinDialog = false
                        selectedServerId = joined.id
                        scope.launch { snackbarHostState.showSnackbar("You joined ${joined.name}") }
                        serversViewModel.loadServers { r -> r.onSuccess { servers = it } }
                    }.onFailure { e ->
                        scope.launch { snackbarHostState.showSnackbar(e.message ?: "Invalid or expired invite code") }
                    }
                }
            }
        )
    }

    if (showCreateChannelDialog) {
        CreateChannelDialog(
            onDismiss = { showCreateChannelDialog = false },
            onConfirm = { name ->
                val serverId = selectedServerId
                if (serverId != null) {
                    channelsViewModel.createChannel(serverId, name, "text") { result ->
                        showCreateChannelDialog = false
                        result.onSuccess {
                            channelsViewModel.loadChannels(serverId) { r -> r.onSuccess { channels = it } }
                        }.onFailure { e ->
                            errorMessage = e.message
                        }
                    }
                } else {
                    showCreateChannelDialog = false
                }
            }
        )
    }

    intentToLeave?.let { server ->
        ConfirmDialog(
            title = "Leave server",
            message = "Are you sure you want to leave ${server.name}?",
            onConfirm = {
                serversViewModel.leaveServer(server.id) { result ->
                    result.onSuccess {
                        serversViewModel.loadServers { r ->
                            r.onSuccess { loaded ->
                                servers = loaded
                                selectedServerId = loaded.firstOrNull()?.id
                            }
                        }
                    }
                }
                intentToLeave = null
            },
            onDismiss = { intentToLeave = null }
        )
    }

    channelToDelete?.let { channel ->
        ConfirmDialog(
            title = "Delete channel",
            message = "Delete channel ${channel.name}? All messages will be removed.",
            onConfirm = {
                channelsViewModel.deleteChannel(channel.id) { result ->
                    result.onSuccess {
                        val serverId = selectedServerId
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

    createdServer?.let { server ->
        InviteCodeDialog(
            server = server,
            onDismiss = { createdServer = null }
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            containerColor = BunnyDialogGray,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun EmptyServersPane(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Group,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("No servers yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Create a server or join with an invite code",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCreate,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BunnyAccent,
                contentColor = androidx.compose.ui.graphics.Color.White
            )
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create server", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onJoin, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.Group, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Join with code")
        }
    }
}

@Composable
private fun ServerPaneSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
        repeat(4) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.bunny.ui.common.ShimmerBox(
                    modifier = Modifier.size(16.dp),
                    cornerRadius = 5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                com.bunny.ui.common.ShimmerBox(
                    modifier = Modifier.width(120.dp).height(14.dp),
                    cornerRadius = 6.dp
                )
            }
        }
    }
}

@Composable
fun CreateServerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BunnyDialogGray,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Create server", fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Server name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Create", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun JoinServerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, errorMessage: String? = null) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BunnyDialogGray,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Join server", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Invite code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                    shape = RoundedCornerShape(16.dp)
                )
                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = code.isNotBlank()) {
                Text("Join", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun InviteCodeDialog(server: Server, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BunnyDialogGray,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Server created", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Share this invite code for friends to join ${server.name}:")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = server.inviteCode,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
