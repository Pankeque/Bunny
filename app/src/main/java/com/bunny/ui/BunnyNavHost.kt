package com.bunny.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
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
import com.bunny.ui.servers.RoleManagementScreen
import com.bunny.ui.servers.ServerListScreen
import com.bunny.ui.servers.ServerSettingScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Servers : Screen("servers", "Servers", Icons.Default.Home)
    object Channels : Screen("channels/{serverId}", "Channels", Icons.Default.Email)
    object Chat : Screen("chat/{channelId}", "Chat", Icons.Default.Email)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
}

@Composable
fun BunnyNavHost(isMasterDetail: Boolean = false) {
    val navController = rememberNavController()

    if (isMasterDetail) {
        MasterDetailNavHost(navController = navController)
    } else {
        PortraitNavHost(navController = navController)
    }
}

@Composable
fun PortraitNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(navController, Modifier.fillMaxSize()) }
        composable("register") { RegisterScreen(navController, Modifier.fillMaxSize()) }
        composable("servers") { ServerListScreen(navController, Modifier.fillMaxSize()) }
        composable("channels/{serverId}") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            ChannelListScreen(navController, serverId, Modifier.fillMaxSize())
        }
        composable("chat/{channelId}") { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId")?.toIntOrNull() ?: 0
            ChatScreen(navController, channelId, Modifier.fillMaxSize())
        }
        composable("profile") { ProfileScreen(navController, Modifier.fillMaxSize()) }
        composable("profile/edit") { ProfileEditScreen(navController, Modifier.fillMaxSize()) }
        composable("servers/{serverId}/settings") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            ServerSettingScreen(navController, serverId, Modifier.fillMaxSize())
        }
        composable("channels/{channelId}/settings") { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId")?.toIntOrNull() ?: 0
            ChannelSettingScreen(navController, channelId, Modifier.fillMaxSize())
        }
        composable("servers/{serverId}/roles") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            RoleManagementScreen(navController, serverId, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun MasterDetailNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(navController, Modifier.fillMaxSize()) }
        composable("register") { RegisterScreen(navController, Modifier.fillMaxSize()) }

        composable("servers") {
            MasterDetailLayout(
                navController = navController,
                serverContent = { serverId, mod ->
                    ServerListScreen(navController, mod)
                },
                chatContent = { channelId, mod ->
                    ChatScreen(navController, channelId, mod)
                }
            )
        }

        composable("channels/{serverId}") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            ChannelListScreen(navController, serverId, Modifier.fillMaxSize())
        }

        composable("chat/{channelId}") { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId")?.toIntOrNull() ?: 0
            ChatScreen(navController, channelId, Modifier.fillMaxSize())
        }

        composable("profile") { ProfileScreen(navController, Modifier.fillMaxSize()) }
        composable("profile/edit") { ProfileEditScreen(navController, Modifier.fillMaxSize()) }
        composable("servers/{serverId}/settings") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            ServerSettingScreen(navController, serverId, Modifier.fillMaxSize())
        }
        composable("channels/{channelId}/settings") { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId")?.toIntOrNull() ?: 0
            ChannelSettingScreen(navController, channelId, Modifier.fillMaxSize())
        }
        composable("servers/{serverId}/roles") { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")?.toIntOrNull() ?: 0
            RoleManagementScreen(navController, serverId, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun MasterDetailLayout(
    navController: NavHostController,
    serverContent: @Composable (Int, Modifier) -> Unit,
    chatContent: @Composable (Int, Modifier) -> Unit
) {
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route ?: ""

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            NavigationRailItem(
                icon = { Icon(Icons.Default.Home, contentDescription = "Servers") },
                label = { Text("Servers", style = MaterialTheme.typography.labelMedium) },
                selected = currentRoute == "servers",
                onClick = {
                    navController.navigate("servers") {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            )
            NavigationRailItem(
                icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                label = { Text("Profile", style = MaterialTheme.typography.labelMedium) },
                selected = currentRoute == "profile",
                onClick = {
                    navController.navigate("profile") {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                currentRoute == "servers" -> {
                    serverContent(0, Modifier.fillMaxSize())
                }
                currentRoute.startsWith("servers/") && currentRoute.contains("/settings") -> {
                    val serverId = currentRoute.split("/")[1].toIntOrNull() ?: 0
                    ServerSettingScreen(navController, serverId, Modifier.fillMaxSize())
                }
                currentRoute.startsWith("servers/") && currentRoute.contains("/roles") -> {
                    val serverId = currentRoute.split("/")[1].toIntOrNull() ?: 0
                    RoleManagementScreen(navController, serverId, Modifier.fillMaxSize())
                }
                currentRoute.startsWith("channels/") && currentRoute.contains("/settings") -> {
                    val channelId = currentRoute.split("/")[1].toIntOrNull() ?: 0
                    ChannelSettingScreen(navController, channelId, Modifier.fillMaxSize())
                }
                currentRoute.startsWith("chat/") -> {
                    val channelId = currentRoute.split("/")[1].toIntOrNull() ?: 0
                    chatContent(channelId, Modifier.fillMaxSize())
                }
                currentRoute == "profile" -> {
                    ProfileScreen(navController, Modifier.fillMaxSize())
                }
                currentRoute == "profile/edit" -> {
                    ProfileEditScreen(navController, Modifier.fillMaxSize())
                }
                else -> {
                    serverContent(0, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun BunnyBottomNav(navController: NavHostController) {
    val items = listOf(Screen.Servers, Screen.Profile)
    NavigationBar {
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry.value?.destination
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title, style = MaterialTheme.typography.labelMedium) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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