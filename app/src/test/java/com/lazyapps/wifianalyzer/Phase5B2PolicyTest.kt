package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.data.WifiUiPreferencesRepository
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.settings.refreshIntervalLabel
import com.lazyapps.wifianalyzer.ui.screens.settings.visibleBandLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5B2PolicyTest {
    @Test fun refreshIntervalsIncludeNewChoicesAndDefaultToTwentySeconds() {
        assertEquals(listOf(3, 5, 10, 15, 20, 30, 60, 120, 300), WifiUiPreferencesRepository.REFRESH_INTERVAL_SECONDS)
        assertEquals(20_000L, WifiUiPreferencesRepository.DEFAULT_REFRESH_INTERVAL_MILLIS)
        assertEquals("3秒", refreshIntervalLabel(3_000L))
        assertEquals("5秒", refreshIntervalLabel(5_000L))
        assertEquals("10秒", refreshIntervalLabel(10_000L))
        assertEquals("15秒", refreshIntervalLabel(15_000L))
        assertEquals("20秒", refreshIntervalLabel(20_000L))
        assertEquals("1分", refreshIntervalLabel(60_000L))
        assertEquals("5分", refreshIntervalLabel(300_000L))
    }

    @Test fun shortIntervalsAndExistingTwentySecondsRoundTripStableValues() {
        listOf(3_000L, 5_000L, 20_000L).forEach { interval ->
            val stored = WifiUiPreferencesRepository.encodeRefreshIntervalSeconds(interval)
            assertEquals(interval, WifiUiPreferencesRepository.decodeRefreshIntervalMillis(stored))
        }
        assertEquals(20_000L, WifiUiPreferencesRepository.decodeRefreshIntervalMillis(null))
    }

    @Test fun legacyEighteenSecondsMigratesWithoutChangingOtherValues() {
        assertEquals(20, WifiUiPreferencesRepository.normalizeRefreshSeconds(18))
        assertEquals(30, WifiUiPreferencesRepository.normalizeRefreshSeconds(30))
    }

    @Test fun distanceRangesRemainMeaningBasedAndLocaleNeutral() {
        assertEquals("THREE_TO_EIGHT", DistanceRange.THREE_TO_EIGHT.name)
        assertEquals("TWENTY_PLUS", DistanceRange.TWENTY_PLUS.name)
    }

    @Test fun visibleBandSummaryPreservesBandOrder() {
        assertEquals("2.4 / 5 GHz", visibleBandLabel(setOf(WifiBand.BAND_5, WifiBand.BAND_24)))
        assertEquals("6 GHz", visibleBandLabel(setOf(WifiBand.BAND_6)))
    }

    @Test fun signalRangeSurvivesOrdinaryScanStateCopies() {
        val selected = ScanUiState(signalHistoryRangeMillis = 300_000L)
        assertEquals(300_000L, selected.copy(isRefreshing = true).signalHistoryRangeMillis)
        assertEquals(300_000L, selected.copy(lastUpdatedMillis = 42L).signalHistoryRangeMillis)
        assertTrue(selected.visibleBands.isNotEmpty())
    }
}
