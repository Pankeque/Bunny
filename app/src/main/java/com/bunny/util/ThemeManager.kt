package com.bunny.util

import android.content.SharedPreferences
import com.bunny.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(
    private val prefs: SharedPreferences
) {
    private val _theme = MutableStateFlow(
        ThemeUtils.getThemeFromString(prefs.getString(Constants.KEY_THEME, "dark"))
    )
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(Constants.KEY_THEME, theme.id).apply()
        _theme.value = theme
    }

    fun currentTheme(): AppTheme = _theme.value
}
