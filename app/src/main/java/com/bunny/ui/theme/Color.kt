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
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF241A44),
    onPrimaryContainer = Color(0xFFE9E2FF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF00303A),
    secondaryContainer = Color(0xFF00333F),
    onSecondaryContainer = Color(0xFFC2EEF9),
    tertiary = Color(0xFF3DDC97),
    onTertiary = Color(0xFF003B26),
    tertiaryContainer = Color(0xFF005238),
    onTertiaryContainer = Color(0xFFA5F3CE),
    background = Color(0xFF0D0F14),
    onBackground = Color(0xFFE4E7EE),
    surface = Color(0xFF14181F),
    onSurface = Color(0xFFECEEF4),
    surfaceVariant = Color(0xFF1E2530),
    onSurfaceVariant = Color(0xFF99A1B4),
    error = Color(0xFFFF5C7A),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF5C0E1E),
    onErrorContainer = Color(0xFFFFDADF),
    outline = Color(0xFF3C4554),
    outlineVariant = Color(0xFF2A3140),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFECEEF4),
    inverseOnSurface = Color(0xFF14181F),
    inversePrimary = Color(0xFFB4A2FF)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6D4FE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DFFF),
    onPrimaryContainer = Color(0xFF1F0B5E),
    secondary = Color(0xFF0089A6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC3EEFF),
    onSecondaryContainer = Color(0xFF00252F),
    tertiary = Color(0xFF008F5F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA5F3CE),
    onTertiaryContainer = Color(0xFF003B26),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF17181F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C24),
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF4E5466),
    error = Color(0xFFC6283B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410007),
    outline = Color(0xFF7E8494),
    outlineVariant = Color(0xFFD3D6E0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF1B1C24),
    inverseOnSurface = Color(0xFFEDEFF5),
    inversePrimary = Color(0xFFB4A2FF)
)

private val YellowColorScheme = darkColorScheme(
    primary = Color(0xFFFFB020),
    onPrimary = Color(0xFF3B2E00),
    primaryContainer = Color(0xFF5B4A00),
    onPrimaryContainer = Color(0xFFFFDFA1),
    secondary = Color(0xFFF0883A),
    onSecondary = Color(0xFF3B1C00),
    secondaryContainer = Color(0xFF5C3500),
    onSecondaryContainer = Color(0xFFFFDCBF),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF241A44),
    onTertiaryContainer = Color(0xFFE9E2FF),
    background = Color(0xFF191307),
    onBackground = Color(0xFFF1E7CF),
    surface = Color(0xFF211A0C),
    onSurface = Color(0xFFF6EDD9),
    surfaceVariant = Color(0xFF2C2414),
    onSurfaceVariant = Color(0xFFB9A880),
    error = Color(0xFFFF5C7A),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF5C0E1E),
    onErrorContainer = Color(0xFFFFDADF),
    outline = Color(0xFF564B31),
    outlineVariant = Color(0xFF3E3620),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF6EDD9),
    inverseOnSurface = Color(0xFF211A0C),
    inversePrimary = Color(0xFFFFCF72)
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

    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = AppShapes, content = content)
}
