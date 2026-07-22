package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.sampledata.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataTest {
    @Test
    fun channelsReferenceOnlyTheirAssignedFrequency() {
        SampleData.channelUsage.forEach { usage ->
            assertTrue(usage.networks.all { it.channel == usage.channel })
            assertTrue(usage.occupancy in 0f..1f)
        }
    }

    @Test
    fun signalHistoryContainsThirtySeconds() {
        assertEquals(30, SampleData.signalHistory.size)
    }
}
