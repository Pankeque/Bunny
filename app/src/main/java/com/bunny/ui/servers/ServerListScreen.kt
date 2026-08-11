package com.bunny.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.bunny.domain.model.Server
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.SectionHeader
import com.bunny.util.ThemeUtils

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
    var createdServer by remember { mutableStateOf<Server?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = navController.context.getSharedPreferences(com.bunny.util.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val currentTheme = ThemeUtils.getThemeFromString(prefs.getString(com.bunny.util.Constants.KEY_THEME, "dark"))

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

    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Bunny", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "${servers.size} server${if (servers.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    actions = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Create", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            IconButton(onClick = { showJoinDialog = true }) {
                                Icon(Icons.Rounded.Group, contentDescription = "Join", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                )
            },
            bottomBar = { com.bunny.ui.BunnyBottomNav(navController) },
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
                    .padding(horizontal = 20.dp)
            ) {
                if (servers.isEmpty() && !isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No servers yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Create or join a server to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            SectionHeader("Your Servers")
                        }
                        items(servers) { server ->
                            ServerCard(
                                server = server,
                                currentTheme = currentTheme,
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
                            Spacer(modifier = Modifier.height(12.dp))
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
                    result.onSuccess { server ->
                        createdServer = server
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

    createdServer?.let { server ->
        InviteCodeDialog(
            server = server,
            onDismiss = { createdServer = null }
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
fun ServerCard(
    server: Server,
    currentTheme: com.bunny.ui.theme.AppTheme,
    onClick: () -> Unit,
    onSettings: () -> Unit,
    onLeave: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!server.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = server.iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            com.bunny.ui.common.brandGradientBrush(currentTheme)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = server.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = server.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Open server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onLeave,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Leave", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CreateServerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Create Server", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Server Name") },
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Join Server", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Invite Code") },
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Server Created", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Share this invite code so friends can join ${server.name}:")
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
