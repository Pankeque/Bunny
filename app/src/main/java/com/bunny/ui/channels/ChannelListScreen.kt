package com.bunny.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bunny.domain.model.Channel
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.theme.BunnyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    navController: NavController,
    serverId: Int,
    modifier: Modifier = Modifier
) {
    val viewModel: ChannelViewModel = hiltViewModel()
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var serverName by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<Channel?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverId) {
        viewModel.loadChannels(serverId) { result ->
            result.onSuccess { channels = it }
        }
        viewModel.getServerName(serverId) { name ->
            serverName = name
        }
    }

    BunnyTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(serverName.ifEmpty { "Channels" }) },
                        actions = {
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Create")
                            }
                        }
                    )
                },
                bottomBar = { com.bunny.ui.BunnyBottomNav(navController) }
            ) { padding ->
                Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                    if (channels.isEmpty() && !isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No channels", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(channels) { channel ->
                            ChannelCard(
                                channel = channel,
                                onClick = { navController.navigate("chat/${channel.id}") },
                                onSettings = { navController.navigate("channels/${channel.id}/settings") },
                                onDelete = { channelToDelete = channel }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateChannelDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createChannel(serverId, name, "text") { result ->
                    showCreateDialog = false
                    result.onSuccess {
                        viewModel.loadChannels(serverId) { r -> r.onSuccess { channels = it } }
                    }.onFailure { e ->
                        errorMessage = e.message
                    }
                }
            }
        )
    }

    channelToDelete?.let { channel ->
        ConfirmDialog(
            title = "Delete Channel",
            message = "Delete ${channel.name}?",
            onConfirm = {
                viewModel.deleteChannel(channel.id) { result ->
                    result.onSuccess {
                        viewModel.loadChannels(serverId) { r -> r.onSuccess { channels = it } }
                    }
                }
                channelToDelete = null
            },
            onDismiss = { channelToDelete = null }
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }
}

@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit, onSettings: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "# ${channel.name}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${channel.type} channel • ${channel.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CreateChannelDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Channel") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Channel Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
