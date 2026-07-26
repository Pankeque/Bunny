package com.bunny.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bunny.domain.model.Role
import com.bunny.ui.theme.BunnyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleManagementScreen(navController: NavController, serverId: Int, modifier: Modifier = Modifier) {
    val viewModel: ServerViewModel = hiltViewModel()
    var roles by remember { mutableStateOf<List<Role>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var roleToDelete by remember { mutableStateOf<Role?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverId) {
        viewModel.loadRoles(serverId) { result ->
            result.onSuccess { roles = it }
        }
    }

    BunnyTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Roles") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Create Role")
                        }
                    }
                )

                if (roles.isEmpty() && !isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("No roles yet", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(roles) { role ->
                        RoleCard(
                            role = role,
                            onDelete = { roleToDelete = role }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoleDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color ->
                isLoading = true
                viewModel.createRole(serverId, name, color) { result ->
                    showCreateDialog = false
                    isLoading = false
                    result.onSuccess {
                        viewModel.loadRoles(serverId) { r -> r.onSuccess { roles = it } }
                    }.onFailure { e ->
                        errorMessage = e.message
                    }
                }
            }
        )
    }

    roleToDelete?.let { role ->
        ConfirmDialog(
            title = "Delete Role",
            message = "Delete role ${role.name}?",
            onConfirm = {
                viewModel.deleteRole(role.id) { result ->
                    result.onSuccess {
                        viewModel.loadRoles(serverId) { r -> r.onSuccess { roles = it } }
                    }
                }
                roleToDelete = null
            },
            onDismiss = { roleToDelete = null }
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
fun RoleCard(role: Role, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(role.color)),
                        shape = MaterialTheme.shapes.small
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = role.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = role.color, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CreateRoleDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("#99AAB5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Role") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Role Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color (hex)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Try: #FF0000, #00FF00, #0000FF", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank() && color.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}