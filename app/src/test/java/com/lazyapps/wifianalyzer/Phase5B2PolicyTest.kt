package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.data.WifiUiPreferencesRepository
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.displayLabel
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.settings.refreshIntervalLabel
import com.lazyapps.wifianalyzer.ui.screens.settings.visibleBandLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5B2PolicyTest {
    @Test fun refreshIntervalsIncludeNewChoicesAndDefaultToTwentySeconds() {
        assertEquals(listOf(10, 15, 20, 30, 60, 120, 300), WifiUiPreferencesRepository.REFRESH_INTERVAL_SECONDS)
        assertEquals(20_000L, WifiUiPreferencesRepository.DEFAULT_REFRESH_INTERVAL_MILLIS)
        assertEquals("10秒", refreshIntervalLabel(10_000L))
        assertEquals("15秒", refreshIntervalLabel(15_000L))
        assertEquals("20秒", refreshIntervalLabel(20_000L))
        assertEquals("1分", refreshIntervalLabel(60_000L))
        assertEquals("5分", refreshIntervalLabel(300_000L))
    }

    @Test fun legacyEighteenSecondsMigratesWithoutChangingOtherValues() {
        assertEquals(20, WifiUiPreferencesRepository.normalizeRefreshSeconds(18))
        assertEquals(30, WifiUiPreferencesRepository.normalizeRefreshSeconds(30))
    }

    @Test fun distanceRangesUseOneSharedFeetConversion() {
        assertEquals("約3～8m", DistanceRange.THREE_TO_EIGHT.displayLabel(false))
        assertEquals("約10～26ft", DistanceRange.THREE_TO_EIGHT.displayLabel(true))
        assertEquals("約66ft以上", DistanceRange.TWENTY_PLUS.displayLabel(true))
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
