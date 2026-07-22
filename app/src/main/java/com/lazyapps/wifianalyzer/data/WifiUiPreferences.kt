package com.lazyapps.wifianalyzer.data

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.lazyapps.wifianalyzer.model.WifiBand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wifiUiDataStore by preferencesDataStore("wifi_ui_preferences")

data class WifiUiPreferences(
    val homeBand: WifiBand = WifiBand.BAND_24,
    val channelBand: WifiBand = WifiBand.BAND_24,
    val refreshIntervalMillis: Long = WifiUiPreferencesRepository.DEFAULT_REFRESH_INTERVAL_MILLIS,
)

class WifiUiPreferencesRepository(private val context: Context) {
    val preferences: Flow<WifiUiPreferences> = context.wifiUiDataStore.data.map { values ->
        WifiUiPreferences(
            homeBand = values[HOME_BAND]?.toWifiBand() ?: WifiBand.BAND_24,
            channelBand = values[CHANNEL_BAND]?.toWifiBand() ?: WifiBand.BAND_24,
            refreshIntervalMillis = values[REFRESH_SECONDS]
                ?.takeIf { it in REFRESH_INTERVAL_SECONDS }
                ?.times(1_000L)
                ?: DEFAULT_REFRESH_INTERVAL_MILLIS,
        )
    }

    suspend fun setHomeBand(band: WifiBand) = context.wifiUiDataStore.edit { it[HOME_BAND] = band.name }
    suspend fun setChannelBand(band: WifiBand) = context.wifiUiDataStore.edit { it[CHANNEL_BAND] = band.name }
    suspend fun setRefreshInterval(milliseconds: Long) {
        val seconds = (milliseconds / 1_000L).toInt()
        require(seconds in REFRESH_INTERVAL_SECONDS)
        context.wifiUiDataStore.edit { it[REFRESH_SECONDS] = seconds }
    }

    private fun String.toWifiBand(): WifiBand? = WifiBand.entries.firstOrNull { it.name == this }

    companion object {
        val REFRESH_INTERVAL_SECONDS = listOf(18, 30, 60, 120, 300)
        const val DEFAULT_REFRESH_INTERVAL_MILLIS = 18_000L
        private val HOME_BAND = stringPreferencesKey("home_band")
        private val CHANNEL_BAND = stringPreferencesKey("channel_band")
        private val REFRESH_SECONDS = intPreferencesKey("refresh_interval_seconds")
    }
}
