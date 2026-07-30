package com.bunny.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bunny.ui.common.ErrorDialog
import com.bunny.ui.theme.BunnyTheme
import com.bunny.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController, modifier: Modifier = Modifier) {
    val viewModel: AuthViewModel = hiltViewModel()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val discoveryUiState by viewModel.discoveryUiState.collectAsState()

    LaunchedEffect(Unit) {
        val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val token = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
        if (!token.isNullOrEmpty()) {
            navController.navigate("servers") {
                popUpTo("login") { inclusive = true }
            }
        }
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
                Text("Bunny", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(40.dp))

                DiscoveryStatusBanner(discoveryUiState) {
                    viewModel.retryDiscovery()
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        isLoading = true
                        viewModel.login(username, password) { result ->
                            isLoading = false
                            result.onSuccess {
                                navController.navigate("servers") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }.onFailure { e ->
                                errorMessage = e.message ?: "Login failed"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Login")
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = {
                    navController.navigate("register")
                }) {
                    Text("Don't have an account? Register")
                }

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    ErrorDialog(message = msg, onDismiss = { errorMessage = null })
                }
            }
        }
    }
}

@Composable
fun DiscoveryStatusBanner(state: DiscoveryUiState, onRetry: () -> Unit) {
    when (state) {
        is DiscoveryUiState.Discovering -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Discovering backend...", style = MaterialTheme.typography.bodySmall)
            }
        }
        is DiscoveryUiState.Found -> {
            Text("Backend connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        is DiscoveryUiState.NotFound -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onRetry) {
                    Text("Retry Discovery")
                }
            }
        }
        is DiscoveryUiState.Unknown -> {
            Text("Initializing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}