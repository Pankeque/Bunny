package com.bunny.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

object ResponsiveUtil {
    fun isLandscape(context: Context): Boolean {
        val config = context.resources.configuration
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun isTablet(context: Context): Boolean {
        val screenWidthDp = context.resources.configuration.screenWidthDp
        return screenWidthDp >= 600
    }

    fun isLandscapeOrTablet(context: Context): Boolean {
        return isLandscape(context) || isTablet(context)
    }
}

@Composable
fun isLandscape(): Boolean {
    val config = LocalConfiguration.current
    return config.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@Composable
fun isTablet(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp >= 600
}

@Composable
fun isMasterDetail(): Boolean {
    return isLandscape() || isTablet()
}
