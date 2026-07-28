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
    val distanceUnit: DistanceUnitPreference = DistanceUnitPreference.METERS,
    val visibleBands: Set<WifiBand> = WifiBand.entries.toSet(),
)

enum class DistanceUnitPreference { METERS, FEET }

class WifiUiPreferencesRepository(private val context: Context) {
    val preferences: Flow<WifiUiPreferences> = context.wifiUiDataStore.data.map { values ->
        val visibleBands = values[VISIBLE_BANDS]
            ?.split(',')
            ?.mapNotNull { it.toWifiBand() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: WifiBand.entries.toSet()
        val firstVisible = WifiBand.entries.first { it in visibleBands }
        WifiUiPreferences(
            homeBand = values[HOME_BAND]?.toWifiBand()?.takeIf { it in visibleBands } ?: firstVisible,
            channelBand = values[CHANNEL_BAND]?.toWifiBand()?.takeIf { it in visibleBands } ?: firstVisible,
            refreshIntervalMillis = decodeRefreshIntervalMillis(values[REFRESH_SECONDS]),
            distanceUnit = values[DISTANCE_UNIT]
                ?.let { runCatching { DistanceUnitPreference.valueOf(it) }.getOrNull() }
                ?: DistanceUnitPreference.METERS,
            visibleBands = visibleBands,
        )
    }

    suspend fun setHomeBand(band: WifiBand) = context.wifiUiDataStore.edit { it[HOME_BAND] = band.name }
    suspend fun setChannelBand(band: WifiBand) = context.wifiUiDataStore.edit { it[CHANNEL_BAND] = band.name }
    suspend fun setRefreshInterval(milliseconds: Long) {
        context.wifiUiDataStore.edit { it[REFRESH_SECONDS] = encodeRefreshIntervalSeconds(milliseconds) }
    }
    suspend fun setDistanceUnit(unit: DistanceUnitPreference) = context.wifiUiDataStore.edit { it[DISTANCE_UNIT] = unit.name }
    suspend fun setVisibleBands(bands: Set<WifiBand>) {
        require(bands.isNotEmpty())
        context.wifiUiDataStore.edit { values ->
            values[VISIBLE_BANDS] = WifiBand.entries.filter { it in bands }.joinToString(",") { it.name }
            val first = WifiBand.entries.first { it in bands }
            if (values[HOME_BAND]?.toWifiBand() !in bands) values[HOME_BAND] = first.name
            if (values[CHANNEL_BAND]?.toWifiBand() !in bands) values[CHANNEL_BAND] = first.name
        }
    }

    private fun String.toWifiBand(): WifiBand? = WifiBand.entries.firstOrNull { it.name == this }

    companion object {
        val REFRESH_INTERVAL_SECONDS = listOf(3, 5, 10, 15, 20, 30, 60, 120, 300)
        const val DEFAULT_REFRESH_INTERVAL_MILLIS = 20_000L
        fun normalizeRefreshSeconds(seconds: Int): Int = if (seconds == 18) 20 else seconds
        fun decodeRefreshIntervalMillis(storedSeconds: Int?): Long =
            storedSeconds
                ?.let(::normalizeRefreshSeconds)
                ?.takeIf { it in REFRESH_INTERVAL_SECONDS }
                ?.times(1_000L)
                ?: DEFAULT_REFRESH_INTERVAL_MILLIS

        fun encodeRefreshIntervalSeconds(milliseconds: Long): Int {
            require(milliseconds % 1_000L == 0L)
            return (milliseconds / 1_000L).toInt().also {
                require(it in REFRESH_INTERVAL_SECONDS)
            }
        }
        private val HOME_BAND = stringPreferencesKey("home_band")
        private val CHANNEL_BAND = stringPreferencesKey("channel_band")
        private val REFRESH_SECONDS = intPreferencesKey("refresh_interval_seconds")
        private val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        private val VISIBLE_BANDS = stringPreferencesKey("visible_bands")
    }
}
