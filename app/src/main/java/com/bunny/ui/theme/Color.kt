package com.bunny.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NightColorScheme = darkColorScheme(
    primary = Color(0xFF00D4FF),
    onPrimary = Color(0xFF00343D),
    primaryContainer = Color(0xFF0A3A4A),
    onPrimaryContainer = Color(0xFFC6F3FF),
    secondary = Color(0xFF8FB0CC),
    onSecondary = Color(0xFF0E2233),
    secondaryContainer = Color(0xFF27455E),
    onSecondaryContainer = Color(0xFFDCEEFF),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF402600),
    tertiaryContainer = Color(0xFF5A3B00),
    onTertiaryContainer = Color(0xFFFFE2B8),
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFE2E8F5),
    surface = Color(0xFF111726),
    onSurface = Color(0xFFE6EBF7),
    surfaceVariant = Color(0xFF1A2333),
    onSurfaceVariant = Color(0xFF9AA6C0),
    error = Color(0xFFFF5C7A),
    onError = Color(0xFF3B0012),
    errorContainer = Color(0xFF5C0E24),
    onErrorContainer = Color(0xFFFFDADF),
    outline = Color(0xFF3B4A63),
    outlineVariant = Color(0xFF232E44),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6EBF7),
    inverseOnSurface = Color(0xFF111726),
    inversePrimary = Color(0xFF005A6B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00779C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC2ECFF),
    onPrimaryContainer = Color(0xFF00333F),
    secondary = Color(0xFF5A7894),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E9FF),
    onSecondaryContainer = Color(0xFF0F1E2E),
    tertiary = Color(0xFFA95F00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCB3),
    onTertiaryContainer = Color(0xFF3A2500),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF171B24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2029),
    surfaceVariant = Color(0xFFE7EBF4),
    onSurfaceVariant = Color(0xFF4B5A70),
    error = Color(0xFFC6283B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410007),
    outline = Color(0xFF7A8AA0),
    outlineVariant = Color(0xFFD3DAE8),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF1B2029),
    inverseOnSurface = Color(0xFFEDF0F8),
    inversePrimary = Color(0xFF6ED8F8)
)

private val AmberColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF3B2800),
    primaryContainer = Color(0xFF5A3B00),
    onPrimaryContainer = Color(0xFFFFE2B8),
    secondary = Color(0xFF00D4FF),
    onSecondary = Color(0xFF00343D),
    secondaryContainer = Color(0xFF0A3A4A),
    onSecondaryContainer = Color(0xFFC6F3FF),
    tertiary = Color(0xFF8FB0CC),
    onTertiary = Color(0xFF0E2233),
    tertiaryContainer = Color(0xFF27455E),
    onTertiaryContainer = Color(0xFFDCEEFF),
    background = Color(0xFF140F08),
    onBackground = Color(0xFFF5EBD8),
    surface = Color(0xFF1B150B),
    onSurface = Color(0xFFF8F0E0),
    surfaceVariant = Color(0xFF2B2414),
    onSurfaceVariant = Color(0xFFC4B894),
    error = Color(0xFFFF5C7A),
    onError = Color(0xFF3B0012),
    errorContainer = Color(0xFF5C0E24),
    onErrorContainer = Color(0xFFFFDADF),
    outline = Color(0xFF5A4E33),
    outlineVariant = Color(0xFF3A3321),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF8F0E0),
    inverseOnSurface = Color(0xFF1B150B),
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
        AppTheme.DARK -> NightColorScheme
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.YELLOW -> AmberColorScheme
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
