package com.lazyapps.wifianalyzer.ui.theme

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.themeDataStore by preferencesDataStore("appearance")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AccentColor { BLUE, INDIGO, PURPLE, CYAN, GREEN, ORANGE, PINK }

data class ThemeUiState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.BLUE,
    val animationsEnabled: Boolean = true,
)

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.themeDataStore
    private val modeFlow = store.data.map { preferences ->
        preferences[MODE_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    private val accentFlow = store.data.map { preferences ->
        preferences[ACCENT_KEY]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: AccentColor.BLUE
    }
    private val animationFlow = store.data.map { it[ANIMATION_KEY] ?: true }

    val uiState = combine(modeFlow, accentFlow, animationFlow, ::ThemeUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeUiState())

    fun setMode(mode: ThemeMode) = viewModelScope.launch { store.edit { it[MODE_KEY] = mode.name } }
    fun setAccent(accent: AccentColor) = viewModelScope.launch { store.edit { it[ACCENT_KEY] = accent.name } }
    fun setAnimationsEnabled(enabled: Boolean) = viewModelScope.launch { store.edit { it[ANIMATION_KEY] = enabled } }

    private companion object {
        val MODE_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_KEY = stringPreferencesKey("accent_color")
        val ANIMATION_KEY = booleanPreferencesKey("animations_enabled")
    }
}
