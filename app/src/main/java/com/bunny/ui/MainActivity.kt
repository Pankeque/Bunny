package com.bunny.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.bunny.ui.theme.AppTheme
import com.bunny.ui.theme.BunnyTheme
import com.bunny.util.Constants
import com.bunny.util.ThemeUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            val themeStr = prefs.getString("theme", "dark")
            val initialTheme = remember { mutableStateOf(ThemeUtils.getThemeFromString(themeStr)) }
            val configuration = LocalConfiguration.current
            val isMasterDetail by remember(configuration) {
                mutableStateOf(
                    configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
                        configuration.screenWidthDp >= 600
                )
            }

            BunnyTheme(theme = initialTheme.value) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BunnyNavHost(isMasterDetail = isMasterDetail)
                }
            }
        }
    }
}
