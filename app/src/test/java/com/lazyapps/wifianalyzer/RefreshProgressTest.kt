package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.ui.components.refreshProgressAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshProgressTest {
    @Test fun progressUsesElapsedTimeAcrossConfiguredInterval() {
        val start = 10_000L
        val interval = 20_000L
        assertEquals(0f, refreshProgressAt(start, start, interval), 0.001f)
        assertEquals(0.25f, refreshProgressAt(start + 5_000, start, interval), 0.001f)
        assertEquals(0.5f, refreshProgressAt(start + 10_000, start, interval), 0.001f)
        assertTrue(refreshProgressAt(start + 19_999, start, interval) < 1f)
    }

    @Test fun progressFollowsChangedIntervalAndRestoredElapsedTime() {
        val start = 40_000L
        assertEquals(0.5f, refreshProgressAt(42_500, start, 5_000), 0.001f)
        assertEquals(0.125f, refreshProgressAt(42_500, start, 20_000), 0.001f)
        assertEquals(0f, refreshProgressAt(start - 1_000, start, 20_000), 0.001f)
    }
}
