package com.bunny.ui.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bunny.ui.common.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingScreen(navController: NavController, channelId: Int, modifier: Modifier = Modifier) {
    val viewModel: ChannelViewModel = hiltViewModel()
    var channelName by remember { mutableStateOf("") }
    var channelType by remember { mutableStateOf("text") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var intentToDelete by remember { mutableStateOf<Boolean>(false) }

    LaunchedEffect(channelId) {
        viewModel.loadChannels(0) { result ->
            result.onSuccess { channels ->
                channels.find { it.id == channelId }?.let {
                    channelName = it.name
                    channelType = it.type
                }
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Channel Settings") },
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
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text("Channel Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Channel Type", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("text", "voice").forEach { type ->
                        FilterChip(
                            selected = channelType == type,
                            onClick = { channelType = type },
                            label = { Text(type.capitalize()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isLoading = true
                        viewModel.updateChannel(channelId, channelName, channelType) { result ->
                            isLoading = false
                            result.onSuccess { navController.popBackStack() }
                                .onFailure { e -> errorMessage = e.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && channelName.isNotBlank(),
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

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { intentToDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Channel")
                }
            }
        }
    }

    intentToDelete?.let {
        ConfirmDialog(
            title = "Delete Channel",
            message = "Are you sure you want to delete this channel? All messages will be permanently lost.",
            onConfirm = {
                isLoading = true
                intentToDelete = false
                viewModel.deleteChannel(channelId) { result ->
                    isLoading = false
                    result.onSuccess {
                        navController.navigate("servers") {
                            popUpTo("servers")
                        }
                    }.onFailure { e ->
                        errorMessage = e.message ?: "Failed to delete channel"
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
