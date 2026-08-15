package com.bunny.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bunny.ui.common.BreathingGradientBackground
import com.bunny.ui.common.BunnyLogoMark
import com.bunny.ui.common.BunnyWordmark
import com.bunny.ui.common.DownloadApkButton
import com.bunny.ui.common.ErrorDialog
import com.bunny.ui.common.GradientButton
import com.bunny.ui.theme.AppTheme
import com.bunny.util.Constants
import com.bunny.util.ThemeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController, modifier: Modifier = Modifier) {
    val viewModel: AuthViewModel = hiltViewModel()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    var currentTheme by remember { mutableStateOf(AppTheme.DARK) }

    LaunchedEffect(Unit) {
        currentTheme = ThemeUtils.getThemeFromString(prefs.getString(Constants.KEY_THEME, "dark"))
        val token = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
        if (!token.isNullOrEmpty()) {
            navController.navigate("servers") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BreathingGradientBackground(theme = currentTheme, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .padding(top = 64.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BunnyLogoMark(size = 72.dp)
                Spacer(modifier = Modifier.height(14.dp))
                BunnyWordmark(fontSize = 34.sp)
                Text(
                    text = "Welcome back",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(36.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Outlined.Face, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        GradientButton(
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
                            icon = if (isLoading) null else Icons.Outlined.Login,
                            text = if (isLoading) "Logging in…" else "Log in",
                            theme = currentTheme
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = { navController.navigate("register") }) {
                    Text("Don't have an account? Create account", textAlign = TextAlign.Center)
                }

                Spacer(modifier = Modifier.height(8.dp))
                DownloadApkButton()
            }
        }
    }

    errorMessage?.let { msg ->
        ErrorDialog(message = msg, onDismiss = { errorMessage = null })
    }
}
