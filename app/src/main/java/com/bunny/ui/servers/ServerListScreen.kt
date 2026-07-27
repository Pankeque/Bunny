package com.bunny.ui.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.bunny.domain.model.Server
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.theme.BunnyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val viewModel: ServerViewModel = hiltViewModel()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var intentToLeave by remember { mutableStateOf<Server?>(null) }
    var servers by remember { mutableStateOf<List<Server>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var joinedServer by remember { mutableStateOf<Server?>(null) }
    var joinMessage by remember { mutableStateOf<String?>(null) }

    // Crie o SnackbarHostState AQUI, antes do Scaffold
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(joinedServer) {
        joinedServer?.let {
            joinMessage = "Joined ${it.name}"
            joinedServer = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadServers { result ->
            result.onSuccess { servers = it }.onFailure { e ->
                errorMessage = e.message
            }
        }
    }

    BunnyTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Bunny") },
                        actions = {
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Create")
                            }
                            IconButton(onClick = { showJoinDialog = true }) {
                                Icon(Icons.Default.Public, contentDescription = "Join")
                            }
                        }
                    )
                },
                bottomBar = { com.bunny.ui.BunnyBottomNav(navController) },
                // Passe o estado correto aqui
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                
                LaunchedEffect(joinMessage) {
                    joinMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        joinMessage = null
                    }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                ) {
                    if (servers.isEmpty() && !isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No servers yet", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Create or join a server to get started", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(servers) { server ->
                            ServerCard(
                                server = server,
                                onClick = {
                                    navController.navigate("channels/${server.id}")
                                },
                                onSettings = {
                                    navController.navigate("servers/${server.id}/settings")
                                },
                                onLeave = {
                                    intentToLeave = server
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
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
                isLoading = true
                viewModel.createServer(name) { result ->
                    showCreateDialog = false
                    isLoading = false
                    result.onSuccess {
                        viewModel.loadServers { r -> r.onSuccess { servers = it } }
                    }.onFailure { e ->
                        errorMessage = e.message
                    }
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinServerDialog(
            onDismiss = {
                showJoinDialog = false
                joinMessage = null
            },
            onConfirm = { code ->
                isLoading = true
                viewModel.joinServer(code) { result ->
                    isLoading = false
                    result.onSuccess { joined ->
                        showJoinDialog = false
                        joinedServer = joined
                        joinMessage = "Joined ${joined.name}"
                        viewModel.loadServers { r -> r.onSuccess { servers = it } }
                        navController.navigate("channels/${joined.id}")
                    }.onFailure { e ->
                        joinMessage = e.message ?: "Invalid or expired invite code"
                    }
                }
            },
            errorMessage = joinMessage
        )
    }

    intentToLeave?.let { server ->
        ConfirmDialog(
            title = "Leave Server",
            message = "Are you sure you want to leave ${server.name}?",
            onConfirm = {
                viewModel.leaveServer(server.id) { result ->
                    result.onSuccess {
                        viewModel.loadServers { r -> r.onSuccess { servers = it } }
                    }
                }
                intentToLeave = null
            },
            onDismiss = { intentToLeave = null }
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
fun ServerCard(server: Server, onClick: () -> Unit, onSettings: () -> Unit, onLeave: () -> Unit) {
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
                imageVector = Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = server.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "Press to open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onLeave) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Leave", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CreateServerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Server") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Server Name") },
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

@Composable
fun JoinServerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, errorMessage: String? = null) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Server") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Invite Code") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null
                )
                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = code.isNotBlank()) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
