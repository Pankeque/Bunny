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

// Bunny — mobile-first palette. Dark theme is the default.
// Near-black background, dark gray surfaces, white/white-70 text,
// soft muted yellow accent (#D4A847) for CTAs and highlights. The
// yellow is intentionally desaturated so it does not feel neon —
// closer to a warm honey / dark gold than to bright primary yellow.
val BunnyAccent = Color(0xFFD4A847)
val BunnyBackground = Color(0xFF0A0A0B)
val BunnySurface = Color(0xFF111113)
val BunnySurfaceVariant = Color(0xFF1A1A1E)
val BunnySurfaceHigh = Color(0xFF222226)
val BunnyTextPrimary = Color(0xFFF2F2F4)
val BunnyTextSecondary = Color(0xFFB3B3B8) // white/70
val BunnyOnline = Color(0xFF3FB950)
val BunnyOffline = Color(0xFF55555C)
val BunnyError = Color(0xFFFF6B6B)
val BunnyDialogGray = Color(0xFF333338)

private val NightColorScheme = darkColorScheme(
    primary = BunnyAccent,
    onPrimary = Color(0xFF1A1408),
    primaryContainer = Color(0xFF3A2E10),
    onPrimaryContainer = Color(0xFFE8D9B0),
    secondary = Color(0xFFA8A8AD),
    onSecondary = Color(0xFF1C1C1F),
    secondaryContainer = Color(0xFF2B2B30),
    onSecondaryContainer = Color(0xFFE9E9EC),
    tertiary = Color(0xFFC9A84E),
    onTertiary = Color(0xFF1A1408),
    tertiaryContainer = Color(0xFF3A2E10),
    onTertiaryContainer = Color(0xFFE8D9B0),
    background = BunnyBackground,
    onBackground = BunnyTextPrimary,
    surface = BunnySurface,
    onSurface = BunnyTextPrimary,
    surfaceVariant = BunnySurfaceVariant,
    onSurfaceVariant = BunnyTextSecondary,
    error = BunnyError,
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF3A1A1A),
    onErrorContainer = Color(0xFFFFDADA),
    outline = Color(0xFF2E2E33),
    outlineVariant = Color(0xFF222226),
    scrim = Color(0xFF000000),
    inverseSurface = BunnyTextPrimary,
    inverseOnSurface = BunnySurface,
    inversePrimary = Color(0xFFE8D9B0),
    surfaceTint = BunnyAccent
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFB8902F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5E6C2),
    onPrimaryContainer = Color(0xFF2A1F08),
    secondary = Color(0xFF5F5F64),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E9EC),
    onSecondaryContainer = Color(0xFF1C1C1F),
    tertiary = Color(0xFF8C6F1F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F6F4),
    onBackground = Color(0xFF1B1B1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1D),
    surfaceVariant = Color(0xFFECEAE7),
    onSurfaceVariant = Color(0xFF55555A),
    error = Color(0xFFC0392B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADA),
    onErrorContainer = Color(0xFF3A0000),
    outline = Color(0xFFC9C7C3),
    outlineVariant = Color(0xFFDCDAD6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF1B1B1D),
    inverseOnSurface = Color(0xFFF2F2F4),
    inversePrimary = Color(0xFFD4A847),
    surfaceTint = Color(0xFFB8902F)
)

private val AmberColorScheme = darkColorScheme(
    primary = Color(0xFFC9A84E),
    onPrimary = Color(0xFF1A1408),
    primaryContainer = Color(0xFF3A2E10),
    onPrimaryContainer = Color(0xFFE8D9B0),
    secondary = Color(0xFFD4A847),
    onSecondary = Color(0xFF1A1408),
    secondaryContainer = Color(0xFF3A2E10),
    onSecondaryContainer = Color(0xFFE8D9B0),
    tertiary = Color(0xFFA8A8AD),
    onTertiary = Color(0xFF1C1C1F),
    background = Color(0xFF0F0D0A),
    onBackground = Color(0xFFF2EFE8),
    surface = Color(0xFF171512),
    onSurface = Color(0xFFF2EFE8),
    surfaceVariant = Color(0xFF201D18),
    onSurfaceVariant = Color(0xFFC0B9AC),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF3A1A1A),
    onErrorContainer = Color(0xFFFFDADA),
    outline = Color(0xFF38332A),
    outlineVariant = Color(0xFF29251E),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF2EFE8),
    inverseOnSurface = Color(0xFF171512),
    inversePrimary = Color(0xFFE8D9B0),
    surfaceTint = Color(0xFFC9A84E)
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
