package com.bunny.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bunny.ui.auth.LoginScreen
import com.bunny.ui.auth.ProfileEditScreen
import com.bunny.ui.auth.ProfileScreen
import com.bunny.ui.auth.RegisterScreen
import com.bunny.ui.channels.ChannelListScreen
import com.bunny.ui.channels.ChannelSettingScreen
import com.bunny.ui.chat.ChatScreen
import com.bunny.ui.dms.DirectMessagesScreen
import com.bunny.ui.servers.RoleManagementScreen
import com.bunny.ui.servers.ServerListScreen
import com.bunny.ui.servers.ServerSettingScreen
import com.bunny.util.isMasterDetail

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val matchRoute: (String?) -> Boolean) {
    object Servers : Screen(
        "servers", "Servers", Icons.Outlined.Dns,
        { route -> route == "servers" || route?.startsWith("channels/") == true || route?.startsWith("chat/") == true }
    )
    object Messages : Screen(
        "dms", "Messages", Icons.Outlined.ChatBubbleOutline,
        { route -> route == "dms" }
    )
    object Profile : Screen(
        "profile", "Profile", Icons.Outlined.Person,
        { route -> route == "profile" || route == "profile/edit" }
    )
}

@Composable
fun BunnyNavHost() {
    val navController = rememberNavController()
    val wide = isMasterDetail()

    var selectedServerId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedChannelId by rememberSaveable { mutableStateOf<Int?>(null) }

    NavHost(
        navController = navController,
        startDestination = "login",
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut() }
    ) {
        composable("login") { LoginScreen(navController, Modifier.fillMaxSize()) }
        composable("register") { RegisterScreen(navController, Modifier.fillMaxSize()) }

        composable("servers") {
            if (wide) {
                ServerWorkspace(
                    navController = navController,
                    selectedServerId = selectedServerId,
                    selectedChannelId = selectedChannelId,
                    onServerSelected = { selectedServerId = it },
                    onChannelSelected = { selectedChannelId = it }
                )
            } else {
                ServerListScreen(navController, Modifier.fillMaxSize())
            }
        }

        composable("channels/{serverId}") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            if (wide) {
                ServerWorkspace(
                    navController = navController,
                    selectedServerId = serverId,
                    selectedChannelId = selectedChannelId,
                    onServerSelected = { selectedServerId = it },
                    onChannelSelected = { selectedChannelId = it }
                )
            } else {
                ChannelListScreen(navController, serverId, Modifier.fillMaxSize())
            }
        }

        composable("chat/{channelId}") { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId")?.toIntOrNull() ?: 0
            if (wide) {
                ServerWorkspace(
                    navController = navController,
                    selectedServerId = selectedServerId,
                    selectedChannelId = channelId,
                    onServerSelected = { selectedServerId = it },
                    onChannelSelected = { selectedChannelId = it }
                )
            } else {
                ChatScreen(navController, channelId, Modifier.fillMaxSize())
            }
        }

        composable("dms") {
            if (wide) {
                ServerWorkspace(
                    navController = navController,
                    selectedServerId = selectedServerId,
                    selectedChannelId = selectedChannelId,
                    onServerSelected = { selectedServerId = it },
                    onChannelSelected = { selectedChannelId = it }
                )
            } else {
                DirectMessagesScreen(navController, Modifier.fillMaxSize())
            }
        }

        composable("profile") { ProfileScreen(navController, Modifier.fillMaxSize()) }
        composable("profile/edit") { ProfileEditScreen(navController, Modifier.fillMaxSize()) }
        composable("servers/{serverId}/settings") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            ServerSettingScreen(navController, serverId, Modifier.fillMaxSize())
        }
        composable("channels/{serverId}/{channelId}/settings") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            val channelId = backStackEntry.arguments?.getString("channelId")?.toIntOrNull() ?: 0
            ChannelSettingScreen(navController, serverId, channelId, Modifier.fillMaxSize())
        }
        composable("servers/{serverId}/roles") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            RoleManagementScreen(navController, serverId, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun BunnyBottomNav(navController: NavHostController) {
    val items = listOf(Screen.Servers, Screen.Messages, Screen.Profile)
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry.value?.destination
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title, style = MaterialTheme.typography.labelMedium) },
                selected = screen.matchRoute(currentDestination?.route),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
