package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.ui.components.WifiIconLevel
import com.lazyapps.wifianalyzer.ui.components.wifiIconLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiSignalIconMappingTest {
    @Test
    fun signalQualityMapsToFourSharedIconLevels() {
        assertEquals(WifiIconLevel.THREE, SignalQuality.EXCELLENT.wifiIconLevel())
        assertEquals(WifiIconLevel.TWO, SignalQuality.GOOD.wifiIconLevel())
        assertEquals(WifiIconLevel.ONE, SignalQuality.FAIR.wifiIconLevel())
        assertEquals(WifiIconLevel.ZERO, SignalQuality.WEAK.wifiIconLevel())
    }
}
