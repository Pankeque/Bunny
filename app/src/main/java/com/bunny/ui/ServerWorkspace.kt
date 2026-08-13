package com.bunny.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.bunny.domain.model.Channel
import com.bunny.domain.model.Server
import com.bunny.ui.channels.ChannelViewModel
import com.bunny.ui.chat.ChatScreen
import com.bunny.ui.common.UnreadDot
import com.bunny.ui.common.UserAvatar
import com.bunny.ui.servers.ServerViewModel
import com.bunny.util.ThemeUtils

@Composable
fun ServerWorkspace(
    navController: NavHostController,
    selectedServerId: Int?,
    selectedChannelId: Int?,
    onServerSelected: (Int) -> Unit,
    onChannelSelected: (Int) -> Unit
) {
    val serversViewModel: ServerViewModel = hiltViewModel()
    val channelsViewModel: ChannelViewModel = hiltViewModel()
    var servers by remember { mutableStateOf<List<Server>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var channelServerId by remember { mutableStateOf<Int?>(null) }

    val prefs = navController.context.getSharedPreferences(
        com.bunny.util.Constants.PREFS_NAME,
        android.content.Context.MODE_PRIVATE
    )
    val currentTheme = ThemeUtils.getThemeFromString(prefs.getString(com.bunny.util.Constants.KEY_THEME, "dark"))

    LaunchedEffect(Unit) {
        serversViewModel.loadServers { result ->
            result.onSuccess { servers = it }
        }
    }

    LaunchedEffect(selectedServerId) {
        val serverId = selectedServerId
        if (serverId != null) {
            channelServerId = serverId
            channelsViewModel.loadChannels(serverId) { result ->
                result.onSuccess { channels = it }
            }
        }
    }

    LaunchedEffect(selectedChannelId, servers) {
        val channelId = selectedChannelId
        if (channelId != null && channelServerId == null && servers.isNotEmpty()) {
            resolveServerForChannel(channelId, servers, channelsViewModel) { serverId ->
                if (serverId != null) {
                    channelServerId = serverId
                    onServerSelected(serverId)
                    channelsViewModel.loadChannels(serverId) { result ->
                        result.onSuccess { channels = it }
                    }
                }
            }
        }
    }

    val backStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry.value?.destination?.route ?: ""

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            NavigationRailItem(
                icon = { Icon(Icons.Outlined.Dns, contentDescription = "Servers") },
                label = { Text("Servers", style = MaterialTheme.typography.labelMedium) },
                selected = currentRoute == "servers" || currentRoute.startsWith("channels/") || currentRoute.startsWith("chat/"),
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                onClick = {
                    navController.navigate("servers") {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            NavigationRailItem(
                icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Messages") },
                label = { Text("Messages", style = MaterialTheme.typography.labelMedium) },
                selected = currentRoute == "dms",
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                onClick = {
                    navController.navigate("dms") {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            NavigationRailItem(
                icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
                label = { Text("Profile", style = MaterialTheme.typography.labelMedium) },
                selected = currentRoute == "profile",
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                onClick = {
                    navController.navigate("profile") {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        WorkspaceServerPane(
            servers = servers,
            currentTheme = currentTheme,
            selectedServerId = selectedServerId,
            onServerClick = { server ->
                onServerSelected(server.id)
                navController.navigate("channels/${server.id}") {
                    launchSingleTop = true
                }
            }
        )

        WorkspaceChannelPane(
            channels = channels,
            selectedChannelId = selectedChannelId,
            onChannelClick = { channel ->
                onChannelSelected(channel.id)
                navController.navigate("chat/${channel.id}") {
                    launchSingleTop = true
                }
            }
        )

        if (selectedChannelId != null) {
            ChatScreen(
                navController = navController,
                channelId = selectedChannelId,
                modifier = Modifier.weight(1f),
                embedded = true
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Tag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select a channel to start chatting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun resolveServerForChannel(
    channelId: Int,
    servers: List<Server>,
    viewModel: ChannelViewModel,
    onResolved: (Int?) -> Unit
) {
    fun tryNext(index: Int) {
        if (index >= servers.size) {
            onResolved(null)
            return
        }
        viewModel.loadChannels(servers[index].id) { result ->
            val hasChannel = result.getOrNull()?.any { it.id == channelId } == true
            if (hasChannel) {
                onResolved(servers[index].id)
            } else {
                tryNext(index + 1)
            }
        }
    }
    tryNext(0)
}

@Composable
private fun WorkspaceServerPane(
    servers: List<Server>,
    currentTheme: com.bunny.ui.theme.AppTheme,
    selectedServerId: Int?,
    onServerClick: (Server) -> Unit
) {
    Column(
        modifier = Modifier
            .width(264.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Servers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Create / Join",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp)
        ) {
            items(servers) { server ->
                WorkspaceServerRow(
                    server = server,
                    selected = server.id == selectedServerId,
                    currentTheme = currentTheme,
                    onClick = { onServerClick(server) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun WorkspaceServerRow(
    server: Server,
    selected: Boolean,
    currentTheme: com.bunny.ui.theme.AppTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            UserAvatar(
                imageUrl = server.iconUrl,
                username = server.name,
                size = 40.dp
            )
            UnreadDot(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 0.dp, y = (-2).dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = if (selected) "Open" else "Channels",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkspaceChannelPane(
    channels: List<Channel>,
    selectedChannelId: Int?,
    onChannelClick: (Channel) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
            Text(
                text = "Channels",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
        ) {
            items(channels) { channel ->
                val isVoice = channel.type == "voice"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (channel.id == selectedChannelId) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onChannelClick(channel) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isVoice) Icons.Outlined.Videocam else Icons.Outlined.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (channel.id == selectedChannelId)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (channel.id == selectedChannelId) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
