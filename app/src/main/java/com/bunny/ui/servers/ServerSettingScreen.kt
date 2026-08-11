package com.bunny.ui.servers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bunny.ui.common.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingScreen(navController: NavController, serverId: Int, modifier: Modifier = Modifier) {
    val viewModel: ServerViewModel = hiltViewModel()
    val clipboardManager = LocalClipboardManager.current
    var serverName by remember { mutableStateOf("") }
    var serverIcon by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var inviteMessage by remember { mutableStateOf<String?>(null) }
    var intentToDelete by remember { mutableStateOf<Boolean>(false) }

    LaunchedEffect(serverId) {
        viewModel.loadServers { result ->
            result.onSuccess { servers ->
                servers.find { it.id == serverId }?.let {
                    serverName = it.name
                    serverIcon = it.iconUrl ?: ""
                    inviteCode = it.inviteCode
                }
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Server Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = serverIcon,
                    onValueChange = { serverIcon = it },
                    label = { Text("Icon URL") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (inviteCode.isNotBlank()) {
                    Text("Invite Code", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        )
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(inviteCode))
                            inviteMessage = "Invite code copied"
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Invite")
                        }
                    }
                    inviteMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = {
                            viewModel.regenerateInviteCode(serverId) { result ->
                                result.onSuccess {
                                    inviteCode = it
                                    inviteMessage = "New invite code generated"
                                }.onFailure { e ->
                                    errorMessage = e.message
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate Invite Code")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        isLoading = true
                        viewModel.updateServer(serverId, serverName, serverIcon) { result ->
                            isLoading = false
                            result.onSuccess { navController.popBackStack() }
                                .onFailure { e -> errorMessage = e.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && serverName.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                OutlinedButton(
                    onClick = { navController.navigate("servers/$serverId/roles") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Roles")
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { intentToDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Server")
                }
            }
        }
    }

    if (intentToDelete) {
        ConfirmDialog(
            title = "Delete Server",
            message = "Are you sure you want to delete this server? All channels, messages, and members will be permanently lost.",
            onConfirm = {
                isLoading = true
                intentToDelete = false
                viewModel.deleteServer(serverId) { result ->
                    isLoading = false
                    result.onSuccess {
                        navController.navigate("servers") {
                            popUpTo("servers") { inclusive = true }
                        }
                    }.onFailure { e ->
                        errorMessage = e.message ?: "Failed to delete server"
                    }
                }
            },
            onDismiss = { intentToDelete = false }
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
