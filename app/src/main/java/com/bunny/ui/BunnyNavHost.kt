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
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bunny.data.local.UnreadStoreEntryPoint
import com.bunny.ui.auth.LoginScreen
import com.bunny.ui.auth.ProfileEditScreen
import com.bunny.ui.auth.ProfileScreen
import com.bunny.ui.auth.RegisterScreen
import com.bunny.ui.auth.SplashScreen
import com.bunny.ui.chat.ChatScreen
import com.bunny.ui.channels.ChannelSettingScreen
import com.bunny.ui.dms.DirectMessagesScreen
import com.bunny.ui.dms.DmChatScreen
import com.bunny.ui.friends.FriendsScreen
import com.bunny.ui.servers.RoleManagementScreen
import com.bunny.ui.servers.ServerSettingScreen
import dagger.hilt.android.EntryPointAccessors

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val matchRoute: (String?) -> Boolean) {
    object Servers : Screen(
        "servers", "Servers", Icons.Outlined.Dns,
        { route -> route == "servers" || route?.startsWith("chat/") == true }
    )
    object Friends : Screen(
        "friends", "Friends", Icons.Outlined.PersonAdd,
        { route -> route == "friends" }
    )
    object Messages : Screen(
        "dms", "Messages", Icons.Outlined.ChatBubbleOutline,
        { route -> route == "dms" || route?.startsWith("dms/") == true }
    )
    object Profile : Screen(
        "profile", "Profile", Icons.Outlined.Person,
        { route -> route == "profile" || route == "profile/edit" }
    )
}

@Composable
fun BunnyNavHost() {
    val navController = rememberNavController()

    var selectedServerId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedChannelId by rememberSaveable { mutableStateOf<Int?>(null) }

    NavHost(
        navController = navController,
        startDestination = "splash",
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut() }
    ) {
        composable("splash") { SplashScreen(navController, Modifier.fillMaxSize()) }
        composable("login") { LoginScreen(navController, Modifier.fillMaxSize()) }
        composable("register") { RegisterScreen(navController, Modifier.fillMaxSize()) }

        composable("servers") {
            ServerWorkspace(
                navController = navController,
                selectedServerId = selectedServerId,
                selectedChannelId = selectedChannelId,
                onServerSelected = {
                    selectedServerId = it
                    selectedChannelId = null
                },
                onChannelSelected = { selectedChannelId = it }
            )
        }

        composable(
            route = "chat/{channelId}?serverId={serverId}",
            arguments = listOf(
                navArgument("channelId") { type = NavType.IntType },
                navArgument("serverId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getInt("channelId") ?: 0
            val serverId = backStackEntry.arguments?.getInt("serverId") ?: -1
            ChatScreen(
                navController = navController,
                channelId = channelId,
                serverId = if (serverId > 0) serverId else (selectedServerId ?: -1),
                modifier = Modifier.fillMaxSize(),
                embedded = false,
                onMembersClick = null
            )
        }

        composable("dms") {
            DirectMessagesScreen(navController, Modifier.fillMaxSize())
        }

        composable(
            route = "dms/{conversationId}/{userId}?username={username}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.IntType },
                navArgument("userId") { type = NavType.IntType },
                navArgument("username") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getInt("conversationId") ?: 0
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            val username = backStackEntry.arguments?.getString("username") ?: ""
            DmChatScreen(
                navController = navController,
                conversationId = conversationId,
                userId = userId,
                username = username,
                modifier = Modifier.fillMaxSize()
            )
        }

        composable("friends") {
            FriendsScreen(navController, Modifier.fillMaxSize())
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

// The three primary destinations shown in both the bottom bar (portrait)
// and the right navigation rail (landscape/tablet).
private val PrimaryNavItems: List<Screen> = listOf(Screen.Servers, Screen.Friends, Screen.Profile)

/**
 * Read the current unread count from the [UnreadStore] so we can badge
 * the Profile item when the user has pending messages.
 */
@Composable
private fun rememberUnreadCount(): Int {
    val context = LocalContext.current
    val unreadStore = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, UnreadStoreEntryPoint::class.java)
            .unreadStore()
    }
    return unreadStore.total.collectAsStateWithLifecycle().value
}

/**
 * Resolve the current [NavController] destination route for matching
 * against the [Screen.matchRoute] predicate.
 */
@Composable
private fun rememberCurrentRoute(navController: NavController): String? {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    return navBackStackEntry.value?.destination?.route
}

private fun navigateToTopLevel(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId)
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunnyBottomNav(navController: NavController) {
    val totalUnread = rememberUnreadCount()
    val currentRoute = rememberCurrentRoute(navController)

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        PrimaryNavItems.forEach { screen ->
            val selected = screen.matchRoute(currentRoute)
            val showBadge = screen == Screen.Profile && totalUnread > 0
            NavigationBarItem(
                icon = {
                    if (showBadge) {
                        BadgedBox(badge = { Badge { Text(if (totalUnread > 99) "99+" else "$totalUnread") } }) {
                            Icon(screen.icon, contentDescription = screen.title)
                        }
                    } else {
                        Icon(screen.icon, contentDescription = screen.title)
                    }
                },
                label = { Text(screen.title, style = MaterialTheme.typography.labelMedium) },
                selected = selected,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = { navigateToTopLevel(navController, screen.route) }
            )
        }
    }
}

/**
 * Vertical navigation rail used in landscape/tablet layouts. Renders the
 * same three primary destinations (Servers / Friends / Profile) stacked
 * on the right edge of the screen instead of at the bottom. The host
 * decides which one to use based on the available width.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunnySideNavRail(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val totalUnread = rememberUnreadCount()
    val currentRoute = rememberCurrentRoute(navController)

    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        PrimaryNavItems.forEach { screen ->
            val selected = screen.matchRoute(currentRoute)
            val showBadge = screen == Screen.Profile && totalUnread > 0
            NavigationRailItem(
                icon = {
                    if (showBadge) {
                        BadgedBox(badge = { Badge { Text(if (totalUnread > 99) "99+" else "$totalUnread") } }) {
                            Icon(screen.icon, contentDescription = screen.title)
                        }
                    } else {
                        Icon(screen.icon, contentDescription = screen.title)
                    }
                },
                label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                selected = selected,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = { navigateToTopLevel(navController, screen.route) }
            )
        }
    }
}
