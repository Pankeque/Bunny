package com.bunny.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.bunny.ui.theme.BunnyTheme
import com.bunny.util.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by themeManager.theme.collectAsState()
            BunnyTheme(theme = theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BunnyNavHost(isMasterDetail = isMasterDetail())
                }
            }
        }
    }

    @Composable
    private fun isMasterDetail(): Boolean {
        val configuration = LocalConfiguration.current
        return configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp >= 600
    }
}
