package com.bunny.util

object ThemeUtils {
    fun getThemeFromString(theme: String?): com.bunny.ui.theme.AppTheme {
        return when (theme) {
            "light" -> com.bunny.ui.theme.AppTheme.LIGHT
            "yellow" -> com.bunny.ui.theme.AppTheme.YELLOW
            else -> com.bunny.ui.theme.AppTheme.DARK
        }
    }
}
