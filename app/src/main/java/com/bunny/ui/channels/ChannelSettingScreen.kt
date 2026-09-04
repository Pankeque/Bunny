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
import com.bunny.ui.theme.BunnyDialogGray
import com.bunny.util.Constants
import com.bunny.util.ThemeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingScreen(navController: NavController, serverId: Int, channelId: Int, modifier: Modifier = Modifier) {
    val viewModel: ChannelViewModel = hiltViewModel()
    var channelName by remember { mutableStateOf("") }
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
                }
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Channel settings", fontWeight = FontWeight.Bold) },
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
                        Text("Channel name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = channelName,
                            onValueChange = { channelName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Tag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Text channel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                GradientButton(
                    onClick = {
                        isLoading = true
                        viewModel.updateChannel(channelId, channelName, null) { result ->
                            isLoading = false
                            result.onSuccess { navController.popBackStack() }
                                .onFailure { e -> errorMessage = e.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && channelName.isNotBlank(),
                    icon = Icons.Outlined.Check,
                    text = if (isLoading) "Saving…" else "Save changes",
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
                    Text("Delete channel")
                }
            }
        }
    }

    if (intentToDelete) {
        ConfirmDialog(
            title = "Delete channel",
            message = "Are you sure you want to delete this channel? All messages will be permanently lost.",
            onConfirm = {
                isLoading = true
                intentToDelete = false
                viewModel.deleteChannel(channelId) { result ->
                    isLoading = false
                    result.onSuccess {
                        navController.popBackStack()
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
