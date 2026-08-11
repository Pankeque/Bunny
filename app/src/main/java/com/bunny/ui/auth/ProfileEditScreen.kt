package com.bunny.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.bunny.ui.theme.AppTheme
import com.bunny.ui.theme.BunnyTheme
import com.bunny.util.Constants
import com.bunny.util.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(navController: NavController, modifier: Modifier = Modifier) {
    val viewModel: AuthViewModel = hiltViewModel()
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarMime by remember { mutableStateOf<String?>(null) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTheme by remember { mutableStateOf(AppTheme.DARK) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    LaunchedEffect(Unit) {
        username = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
        avatarUrl = prefs.getString(Constants.KEY_AVATAR_URL, "") ?: ""
        val themeStr = prefs.getString(Constants.KEY_THEME, "dark")
        selectedTheme = com.bunny.util.ThemeUtils.getThemeFromString(themeStr)
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val (bytes, mime) = ImageUtils.prepareImage(context, uri)
                avatarBytes = bytes
                avatarMime = mime
                avatarUri = uri
                avatarError = null
            } catch (e: Exception) {
                avatarError = "Could not load image"
            }
        }
    }

    BunnyTheme(theme = selectedTheme) {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Edit Profile") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        val displayModel: Any = avatarUri ?: avatarUrl
                        if (avatarUri != null || avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = displayModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(onClick = { avatarPicker.launch("image/*") }) {
                        Text("Change Avatar")
                    }
                    avatarError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; usernameError = null },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = usernameError != null,
                        supportingText = usernameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text("Theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOption(
                            name = "Dark",
                            color = Color(0xFF1E1F22),
                            selected = selectedTheme == AppTheme.DARK,
                            onClick = { selectedTheme = AppTheme.DARK },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            name = "Light",
                            color = Color(0xFFFFFFFF),
                            selected = selectedTheme == AppTheme.LIGHT,
                            onClick = { selectedTheme = AppTheme.LIGHT },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            name = "Yellow",
                            color = Color(0xFFFFD700),
                            selected = selectedTheme == AppTheme.YELLOW,
                            onClick = { selectedTheme = AppTheme.YELLOW },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            usernameError = null
                            avatarError = null
                            var hasError = false
                            if (username.isBlank()) {
                                usernameError = "Username cannot be empty"
                                hasError = true
                            } else if (username.length < 2) {
                                usernameError = "Username must be at least 2 characters"
                                hasError = true
                            } else if (username.length > 50) {
                                usernameError = "Username must be at most 50 characters"
                                hasError = true
                            }
                            if (hasError) return@Button
                            isLoading = true
                            val theme = selectedTheme.name.lowercase()
                            if (avatarBytes != null && avatarMime != null) {
                                viewModel.uploadAvatar(avatarBytes!!, avatarMime!!) { avatarResult ->
                                    avatarResult.onSuccess {
                                        viewModel.updateProfile(username, null, theme) { result ->
                                            isLoading = false
                                            result.onSuccess {
                                                navController.popBackStack()
                                            }.onFailure { e ->
                                                errorMessage = e.message
                                            }
                                        }
                                    }.onFailure { e ->
                                        isLoading = false
                                        errorMessage = e.message
                                    }
                                }
                            } else {
                                viewModel.updateProfile(username, null, theme) { result ->
                                    isLoading = false
                                    result.onSuccess {
                                        navController.popBackStack()
                                    }.onFailure { e ->
                                        errorMessage = e.message
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }
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
fun ThemeOption(name: String, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}