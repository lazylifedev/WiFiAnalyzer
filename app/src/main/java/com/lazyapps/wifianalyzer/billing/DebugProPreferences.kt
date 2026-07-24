package com.lazyapps.wifianalyzer.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.lazyapps.wifianalyzer.BuildConfig

private val Context.debugProDataStore by preferencesDataStore("debug_pro_preferences")

class DebugProPreferences(private val context: Context) {
    val forcePro: Flow<Boolean> = context.debugProDataStore.data.map { BuildConfig.DEBUG && (it[FORCE_PRO] ?: false) }

    suspend fun setForcePro(enabled: Boolean) {
        context.debugProDataStore.edit { it[FORCE_PRO] = enabled }
    }

    private companion object { val FORCE_PRO = booleanPreferencesKey("debugForcePro") }
}
