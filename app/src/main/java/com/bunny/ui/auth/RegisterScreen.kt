package com.bunny.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
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
import com.bunny.ui.common.ErrorDialog
import com.bunny.ui.common.GradientButton
import com.bunny.ui.theme.AppTheme
import com.bunny.util.Constants
import com.bunny.util.ThemeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController, modifier: Modifier = Modifier) {
    val viewModel: AuthViewModel = hiltViewModel()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    var currentTheme by remember { mutableStateOf(AppTheme.DARK) }

    LaunchedEffect(Unit) {
        currentTheme = ThemeUtils.getThemeFromString(prefs.getString(Constants.KEY_THEME, "dark"))
    }

    Box(modifier = modifier.fillMaxSize()) {
        BreathingGradientBackground(theme = currentTheme, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BunnyLogoMark(size = 56.dp)
                Spacer(modifier = Modifier.height(10.dp))
                BunnyWordmark(fontSize = 30.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create your account",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Join the real-time conversation platform",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))

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
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm password") },
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
                                if (password != confirmPassword) {
                                    errorMessage = "Passwords do not match"
                                    return@GradientButton
                                }
                                isLoading = true
                                viewModel.register(username, password) { result ->
                                    isLoading = false
                                    result.onSuccess {
                                        navController.navigate("servers") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }.onFailure { e ->
                                        errorMessage = e.message ?: "Sign up failed"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                            icon = Icons.Outlined.PersonAdd,
                            text = if (isLoading) "Creating account…" else "Create account",
                            theme = currentTheme
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Already have an account? Log in", textAlign = TextAlign.Center)
                }
            }
        }
    }

    errorMessage?.let { msg ->
        ErrorDialog(message = msg, onDismiss = { errorMessage = null })
    }
}
