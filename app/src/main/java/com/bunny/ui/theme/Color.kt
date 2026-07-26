package com.bunny.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8E9AAF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2A2A2E),
    onPrimaryContainer = Color(0xFFE5E5EA),
    secondary = Color(0xFF9E9EA0),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF3A3A3C),
    onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Color(0xFFC8A84E),
    onTertiary = Color(0xFF1A1A1A),
    tertiaryContainer = Color(0xFF3A3520),
    onTertiaryContainer = Color(0xFFF5F0E8),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFE5E5EA),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    error = Color(0xFFCF6679),
    onError = Color(0xFF0E0E0E),
    errorContainer = Color(0xFF5C1A1E),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF4A4A4C)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3A4A5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE4EC),
    onPrimaryContainer = Color(0xFF1A2A3C),
    secondary = Color(0xFF5A5A5C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF1A1A1E),
    tertiary = Color(0xFFA08030),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5F0E8),
    onTertiaryContainer = Color(0xFF2A2000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0E0E0E),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF0E0E0E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF737373)
)

private val YellowColorScheme = darkColorScheme(
    primary = Color(0xFFC8A84E),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF3A3520),
    onPrimaryContainer = Color(0xFFF5F0E8),
    secondary = Color(0xFFA09080),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF3A3530),
    onSecondaryContainer = Color(0xFFF5F0E8),
    tertiary = Color(0xFF8E9AAF),
    onTertiary = Color(0xFF1A1A1A),
    tertiaryContainer = Color(0xFF2A2A3C),
    onTertiaryContainer = Color(0xFFE5E5EA),
    background = Color(0xFF1A1A1A),
    onBackground = Color(0xFFE8E0D0),
    surface = Color(0xFF262626),
    onSurface = Color(0xFFF5F0E8),
    surfaceVariant = Color(0xFF363636),
    onSurfaceVariant = Color(0xFFA09080),
    error = Color(0xFFCF6679),
    onError = Color(0xFF1A1A1A),
    errorContainer = Color(0xFF5C1A1E),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF5A5040)
)

enum class AppTheme(val id: String) {
    DARK("dark"),
    LIGHT("light"),
    YELLOW("yellow")
}

@Composable
fun BunnyTheme(
    theme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        AppTheme.DARK -> DarkColorScheme
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.YELLOW -> YellowColorScheme
    }
    val darkTheme = theme == AppTheme.DARK || theme == AppTheme.YELLOW
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
