package com.lazyapps.wifianalyzer.debug

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.lazyapps.wifianalyzer.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.debugDisplayDataStore by preferencesDataStore("debug_display_preferences")

class DebugDisplayPreferences(private val context: Context) {
    val enabled: Flow<Boolean> = if (BuildConfig.DEBUG) {
        context.debugDisplayDataStore.data.map { it[DEBUG_DISPLAY] ?: false }
    } else {
        flowOf(false)
    }

    suspend fun setEnabled(enabled: Boolean) {
        if (BuildConfig.DEBUG) context.debugDisplayDataStore.edit { it[DEBUG_DISPLAY] = enabled }
    }

    private companion object {
        val DEBUG_DISPLAY = booleanPreferencesKey("debugDisplay")
    }
}
