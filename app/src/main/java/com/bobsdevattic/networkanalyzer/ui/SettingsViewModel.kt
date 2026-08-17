package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.bobsdevattic.networkanalyzer.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App settings, persisted in SharedPreferences. Currently just the theme mode. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private fun readThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    private companion object {
        const val PREFS = "settings"
        const val KEY_THEME = "theme_mode"
    }
}
