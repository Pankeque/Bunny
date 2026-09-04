package com.bunny.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
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
import com.bunny.ui.common.BunnyImage
import com.bunny.ui.common.GradientButton
import com.bunny.ui.common.SectionHeader
import com.bunny.ui.theme.BunnyAccent
import com.bunny.ui.theme.BunnyDialogGray
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
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    LaunchedEffect(Unit) {
        username = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
        avatarUrl = prefs.getString(Constants.KEY_AVATAR_URL, "") ?: ""
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

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Edit profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SectionHeader("Avatar", modifier = Modifier.fillMaxWidth())

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(BunnyAccent),
                    contentAlignment = Alignment.Center
                ) {
                    val displayModel: Any = avatarUri ?: avatarUrl
                    if (avatarUri != null || avatarUrl.isNotBlank()) {
                        BunnyImage(
                            model = displayModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { avatarPicker.launch("image/*") },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change photo")
                }
                avatarError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(28.dp))

                SectionHeader("General", modifier = Modifier.fillMaxWidth())

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Username", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it; usernameError = null },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = usernameError != null,
                            supportingText = usernameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Your current theme is preserved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                GradientButton(
                    onClick = {
                        usernameError = null
                        avatarError = null
                        var hasError = false
                        if (username.isBlank()) {
                            usernameError = "Username cannot be empty"
                            hasError = true
                        } else if (username.length < 2) {
                            usernameError = "Name must be at least 2 characters"
                            hasError = true
                        } else if (username.length > 50) {
                            usernameError = "Name must be at most 50 characters"
                            hasError = true
                        }
                        if (hasError) return@GradientButton
                        isLoading = true
                        val currentTheme = prefs.getString(Constants.KEY_THEME, "dark") ?: "dark"
                        if (avatarBytes != null && avatarMime != null) {
                            viewModel.uploadAvatar(avatarBytes!!, avatarMime!!) { avatarResult ->
                                avatarResult.onSuccess {
                                    viewModel.updateProfile(username, null, currentTheme) { result ->
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
                            viewModel.updateProfile(username, null, currentTheme) { result ->
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
                    icon = Icons.Outlined.Check,
                    text = if (isLoading) "Saving…" else "Save changes"
                )
            }
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            containerColor = BunnyDialogGray,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }
}
