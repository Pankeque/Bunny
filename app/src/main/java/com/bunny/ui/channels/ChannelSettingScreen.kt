package com.bunny.ui.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.GradientButton
import com.bunny.ui.common.SectionHeader
import com.bunny.util.Constants
import com.bunny.util.ThemeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingScreen(navController: NavController, serverId: Int, channelId: Int, modifier: Modifier = Modifier) {
    val viewModel: ChannelViewModel = hiltViewModel()
    var channelName by remember { mutableStateOf("") }
    var channelType by remember { mutableStateOf("text") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var intentToDelete by remember { mutableStateOf<Boolean>(false) }
    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val currentTheme = ThemeUtils.getThemeFromString(prefs.getString(Constants.KEY_THEME, "dark"))

    LaunchedEffect(serverId, channelId) {
        viewModel.loadChannels(serverId) { result ->
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
                title = { Text("Channel Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SectionHeader("General", modifier = Modifier.fillMaxWidth())

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Channel Name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = channelName,
                            onValueChange = { channelName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Channel Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("text", "voice").forEach { type ->
                                FilterChip(
                                    selected = channelType == type,
                                    onClick = { channelType = type },
                                    label = { Text(type.replaceFirstChar { it.uppercase() }) },
                                    leadingIcon = {
                                        Icon(
                                            if (type == "voice") Icons.Outlined.Videocam else Icons.Outlined.Tag,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                GradientButton(
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
                    icon = Icons.Outlined.Check,
                    text = if (isLoading) "Saving…" else "Save Changes",
                    theme = currentTheme
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { intentToDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Channel")
                }
            }
        }
    }

    if (intentToDelete) {
        ConfirmDialog(
            title = "Delete Channel",
            message = "Are you sure you want to delete this channel? All messages will be permanently lost.",
            onConfirm = {
                isLoading = true
                intentToDelete = false
                viewModel.deleteChannel(channelId) { result ->
                    isLoading = false
                    result.onSuccess {
                        navController.navigate("channels/$serverId") {
                            popUpTo("channels/$serverId") { inclusive = true }
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
