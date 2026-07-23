package com.lazyapps.wifianalyzer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore("onboarding_preferences")

class OnboardingPreferencesRepository(private val context: Context) {
    val completed: Flow<Boolean> = context.onboardingDataStore.data.map { it[COMPLETED] ?: false }

    suspend fun setCompleted(completed: Boolean) {
        context.onboardingDataStore.edit { it[COMPLETED] = completed }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("phase_6a_completed")
    }
}
