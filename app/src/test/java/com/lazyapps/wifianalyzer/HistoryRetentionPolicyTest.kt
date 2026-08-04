package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.domain.HistoryRetentionPolicy
import com.lazyapps.wifianalyzer.model.SignalSample
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRetentionPolicyTest {
    @Test fun freeKeepsBoundaryAndRemovesOlder() {
        val now = 100L + HistoryRetentionPolicy.FREE_RETENTION_MILLIS
        val samples = listOf(SignalSample(100L, -1), SignalSample(98L, -2))
        assertEquals(listOf(samples[0]), HistoryRetentionPolicy.retain(samples, now, false))
    }
    @Test fun proRetainsLongerHistoryButStillPreservesExisting900Cap() {
        val samples = (0 until 901).map { SignalSample(it.toLong(), -it) }
        assertEquals(900, HistoryRetentionPolicy.retain(samples, 100_000L, true).size)
        assertEquals(900, HistoryRetentionPolicy.retain(samples, 100_000L, false).size)
    }
}
