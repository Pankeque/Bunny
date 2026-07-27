package com.bunny.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.theme.BunnyTheme
import com.bunny.util.Constants

@Composable
fun ProfileScreen(navController: NavController, modifier: Modifier = Modifier) {
    val viewModel: AuthViewModel = hiltViewModel()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }

    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    LaunchedEffect(Unit) {
        username = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
    }

    BunnyTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = username, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = { navController.navigate("profile/edit") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Profile")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout")
                }
            }
        }
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            title = "Logout",
            message = "Are you sure you want to logout?",
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}