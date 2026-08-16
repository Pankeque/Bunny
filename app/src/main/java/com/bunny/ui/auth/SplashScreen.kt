package com.bunny.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bunny.ui.common.BunnyLogoMark
import com.bunny.ui.common.BunnyWordmark
import com.bunny.util.Constants
import kotlinx.coroutines.delay

// Simple splash: centered geometric rabbit logo, wordmark, tagline.
// Auto-redirects to login (or servers when a token already exists).
@Composable
fun SplashScreen(navController: NavController, modifier: Modifier = Modifier) {
    val prefs = navController.context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        delay(1500)
        val token = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
        if (!token.isNullOrEmpty()) {
            navController.navigate("servers") {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BunnyLogoMark(size = 72.dp)
            Spacer(modifier = Modifier.height(16.dp))
            BunnyWordmark(fontSize = 34.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Real-time text conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
