package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.domain.SignalHistoryPolicy
import com.lazyapps.wifianalyzer.model.SignalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5B1PolicyTest {
    @Test fun adaptiveAxisPadsAndRoundsRealisticData() {
        val axis = SignalHistoryPolicy.adaptiveYAxis(listOf(SignalSample(1, -59), SignalSample(2, -36)))
        assertEquals(-65, axis.lower)
        assertEquals(-30, axis.upper)
    }

    @Test fun adaptiveAxisKeepsMinimumTwentyDbmSpan() {
        val axis = SignalHistoryPolicy.adaptiveYAxis(listOf(SignalSample(1, -53), SignalSample(2, -48)))
        assertTrue(axis.span >= 20)
        assertTrue(axis.lower >= -100)
        assertTrue(axis.upper <= -20)
        assertEquals(0, axis.lower % 5)
        assertEquals(0, axis.upper % 5)
    }

    @Test fun adaptiveAxisFallsBackWithoutSamples() {
        val axis = SignalHistoryPolicy.adaptiveYAxis(emptyList())
        assertEquals(-100, axis.lower)
        assertEquals(-30, axis.upper)
    }
}
