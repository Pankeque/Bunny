package com.bunny.ui.servers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.bunny.ui.common.ConfirmDialog
import com.bunny.ui.common.GradientButton
import com.bunny.ui.common.SectionHeader
import com.bunny.ui.common.brandGradientBrush
import com.bunny.util.Constants
import com.bunny.util.ImageUtils
import com.bunny.util.ThemeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingScreen(navController: NavController, serverId: Int, modifier: Modifier = Modifier) {
    val viewModel: ServerViewModel = hiltViewModel()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var serverName by remember { mutableStateOf("") }
    var serverIcon by remember { mutableStateOf("") }
    var iconBytes by remember { mutableStateOf<ByteArray?>(null) }
    var iconMime by remember { mutableStateOf<String?>(null) }
    var iconUri by remember { mutableStateOf<Uri?>(null) }
    var inviteCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var inviteMessage by remember { mutableStateOf<String?>(null) }
    var intentToDelete by remember { mutableStateOf<Boolean>(false) }
    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val currentTheme = ThemeUtils.getThemeFromString(prefs.getString(Constants.KEY_THEME, "dark"))

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val (bytes, mime) = ImageUtils.prepareImage(context, uri)
                iconBytes = bytes
                iconMime = mime
                iconUri = uri
            } catch (e: Exception) {
                errorMessage = "Could not load image"
            }
        }
    }

    LaunchedEffect(serverId) {
        viewModel.loadServers { result ->
            result.onSuccess { servers ->
                servers.find { it.id == serverId }?.let {
                    serverName = it.name
                    serverIcon = it.iconUrl ?: ""
                    inviteCode = it.inviteCode
                }
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Server Settings", fontWeight = FontWeight.Bold) },
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
                SectionHeader("Server Icon", modifier = Modifier.fillMaxWidth())

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(brandGradientBrush(currentTheme)),
                    contentAlignment = Alignment.Center
                ) {
                    val displayModel: Any = iconUri ?: serverIcon
                    if (iconUri != null || serverIcon.isNotBlank()) {
                        AsyncImage(
                            model = displayModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = serverName.take(1).uppercase().ifBlank { "S" },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { iconPicker.launch("image/*") },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Icon")
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
                        Text("Server Name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                if (inviteCode.isNotBlank()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionHeader("Invite", modifier = Modifier.fillMaxWidth())

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Invite Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = inviteCode,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(inviteCode))
                                        inviteMessage = "Invite code copied"
                                    },
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Invite", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            inviteMessage?.let { msg ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.regenerateInviteCode(serverId) { result ->
                                        result.onSuccess {
                                            inviteCode = it
                                            inviteMessage = "New invite code generated"
                                        }.onFailure { e ->
                                            errorMessage = e.message
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate Invite Code")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                GradientButton(
                    onClick = {
                        isLoading = true
                        if (iconBytes != null && iconMime != null) {
                            viewModel.uploadServerIcon(serverId, iconBytes!!, iconMime!!) { iconResult ->
                                iconResult.onSuccess {
                                    viewModel.updateServer(serverId, serverName, null) { result ->
                                        isLoading = false
                                        result.onSuccess { navController.popBackStack() }
                                            .onFailure { e -> errorMessage = e.message }
                                    }
                                }.onFailure { e ->
                                    isLoading = false
                                    errorMessage = e.message
                                }
                            }
                        } else {
                            viewModel.updateServer(serverId, serverName, null) { result ->
                                isLoading = false
                                result.onSuccess { navController.popBackStack() }
                                    .onFailure { e -> errorMessage = e.message }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && serverName.isNotBlank(),
                    icon = Icons.Outlined.Check,
                    text = if (isLoading) "Saving…" else "Save Changes",
                    theme = currentTheme
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { navController.navigate("servers/$serverId/roles") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.Shield, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Roles")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { intentToDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Server")
                }
            }
        }
    }

    if (intentToDelete) {
        ConfirmDialog(
            title = "Delete Server",
            message = "Are you sure you want to delete this server? All channels, messages, and members will be permanently lost.",
            onConfirm = {
                isLoading = true
                intentToDelete = false
                viewModel.deleteServer(serverId) { result ->
                    isLoading = false
                    result.onSuccess {
                        navController.navigate("servers") {
                            popUpTo("servers") { inclusive = true }
                        }
                    }.onFailure { e ->
                        errorMessage = e.message ?: "Failed to delete server"
                    }
                }
            },
            onDismiss = { intentToDelete = false }
        )
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
