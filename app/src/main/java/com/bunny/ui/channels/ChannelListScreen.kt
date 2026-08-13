package com.bunny.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.bunny.domain.model.Channel
import com.bunny.ui.common.BreathingGradientBackground
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.ShimmerBox
import com.bunny.util.ThemeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    navController: NavHostController,
    serverId: Int,
    modifier: Modifier = Modifier
) {
    val viewModel: ChannelViewModel = hiltViewModel()
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var serverName by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<Channel?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val prefs = navController.context.getSharedPreferences(com.bunny.util.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val currentTheme = ThemeUtils.getThemeFromString(prefs.getString(com.bunny.util.Constants.KEY_THEME, "dark"))

    LaunchedEffect(serverId) {
        isLoading = true
        viewModel.loadChannels(serverId) { result ->
            result.onSuccess { channels = it }.onFailure { e ->
                errorMessage = e.message
            }
            isLoading = false
        }
        viewModel.getServerName(serverId) { name ->
            serverName = name
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(serverName.ifEmpty { "Channels" }, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (isLoading) "Loading…"
                                else "${channels.size} channel${if (channels.size == 1) "" else "s"}",
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
                                Icon(Icons.Outlined.Add, contentDescription = "Create", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                )
            },
            bottomBar = { com.bunny.ui.BunnyBottomNav(navController) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                when {
                    isLoading -> ChannelListSkeleton()
                    channels.isEmpty() -> {
                        BreathingGradientBackground(
                            theme = currentTheme,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Create a channel to start chatting",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            val grouped = channels.groupBy { if (it.type == "voice") "ÁUDIO" else "GERAL" }
                            grouped.forEach { (category, list) ->
                                item(key = "header_$category") {
                                    CategoryHeader(category)
                                }
                                items(list, key = { it.id }) { channel ->
                                    ChannelRow(
                                        channel = channel,
                                        onClick = { navController.navigate("chat/${channel.id}") },
                                        onSettings = { navController.navigate("channels/${channel.serverId}/${channel.id}/settings") },
                                        onDelete = { channelToDelete = channel }
                                    )
                                }
                                item(key = "spacer_$category") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateChannelDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, type ->
                viewModel.createChannel(serverId, name, type) { result ->
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
fun ChannelListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(24.dp))
        ShimmerBox(modifier = Modifier.width(90.dp).height(14.dp), cornerRadius = 6.dp)
        Spacer(modifier = Modifier.height(12.dp))
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(modifier = Modifier.size(14.dp), cornerRadius = 4.dp)
                Spacer(modifier = Modifier.width(10.dp))
                ShimmerBox(modifier = Modifier.width(140.dp).height(14.dp), cornerRadius = 6.dp)
            }
        }
    }
}

@Composable
fun CategoryHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun ChannelRow(channel: Channel, onClick: () -> Unit, onSettings: () -> Unit, onDelete: () -> Unit) {
    val isVoice = channel.type == "voice"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .then(
                if (channel.type == "text") Modifier.padding(start = 12.dp) else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVoice) Icons.Outlined.Videocam else Icons.Outlined.Tag,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(2.dp))
        IconButton(
            onClick = onDelete,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChannelDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("text") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Create Channel", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("text", "voice").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.replaceFirstChar { it.uppercase() }) },
                            leadingIcon = {
                                Icon(
                                    if (t == "voice") Icons.Outlined.Videocam else Icons.Outlined.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, type) }, enabled = name.isNotBlank()) {
                Text("Create", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
