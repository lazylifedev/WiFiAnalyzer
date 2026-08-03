package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAnalysisTest {
    @Test
    fun frequencyConvertsToChannelAcrossSupportedBands() {
        assertEquals(1, WifiAnalysis.channelFromFrequency(2412))
        assertEquals(14, WifiAnalysis.channelFromFrequency(2484))
        assertEquals(36, WifiAnalysis.channelFromFrequency(5180))
        assertEquals(2, WifiAnalysis.channelFromFrequency(5935))
        assertEquals(1, WifiAnalysis.channelFromFrequency(5955))
        assertEquals(0, WifiAnalysis.channelFromFrequency(8000))
    }

    @Test
    fun frequencyIsClassifiedInto24_5And6GhzBands() {
        assertEquals(WifiBand.BAND_24, WifiAnalysis.bandFromFrequency(2437))
        assertEquals(WifiBand.BAND_5, WifiAnalysis.bandFromFrequency(5500))
        assertEquals(WifiBand.BAND_6, WifiAnalysis.bandFromFrequency(6115))
        assertEquals(null, WifiAnalysis.bandFromFrequency(900))
    }

    @Test
    fun signalQualityUsesDefinedDbmBoundaries() {
        assertEquals(SignalQuality.EXCELLENT, WifiAnalysis.signalQuality(-55))
        assertEquals(SignalQuality.GOOD, WifiAnalysis.signalQuality(-56))
        assertEquals(SignalQuality.FAIR, WifiAnalysis.signalQuality(-68))
        assertEquals(SignalQuality.WEAK, WifiAnalysis.signalQuality(-80))
    }

    @Test
    fun distanceUsesMedianToReduceOutlierImpactAndBandCorrection() {
        assertEquals(DistanceRange.THREE_TO_EIGHT, WifiAnalysis.distanceRange(listOf(-64, -65, -63, -20, -64), WifiBand.BAND_24))
        assertEquals(DistanceRange.ONE_TO_THREE, WifiAnalysis.distanceRange(listOf(-58, -58, -59), WifiBand.BAND_6))
        assertEquals(DistanceRange.TWENTY_PLUS, WifiAnalysis.distanceRange(listOf(-90, -88, -92), WifiBand.BAND_24))
        assertEquals(DistanceRange.EIGHT_TO_TWENTY, WifiAnalysis.distanceRange(listOf(-82, -72, -62, -52, -42), WifiBand.BAND_24))
    }

    @Test
    fun duplicateBssidKeepsNewestScanResultAndSortsBySignal() {
        val old = accessPoint("AA:BB:CC:DD:EE:FF", -50, 10)
        val newest = accessPoint("aa:bb:cc:dd:ee:ff", -70, 20)
        val other = accessPoint("11:22:33:44:55:66", -60, 15)
        val result = WifiAnalysis.deduplicateByBssid(listOf(old, newest, other))

        assertEquals(2, result.size)
        assertEquals(other.bssid, result.first().bssid)
        assertEquals(20, result.last().timestampMicros)
    }

    @Test
    fun channelCongestionAccountsForCountSignalWidthAndAdjacentOverlap() {
        val weakSingle = accessPoint("00:00:00:00:00:01", -88, 1, channel = 1, frequency = 2412)
        val strongWide = accessPoint("00:00:00:00:00:02", -48, 2, channel = 6, frequency = 2437, width = 40)
        val sameChannel = accessPoint("00:00:00:00:00:03", -58, 3, channel = 6, frequency = 2437)
        val adjacentOverlap = accessPoint("00:00:00:00:00:04", -55, 4, channel = 7, frequency = 2442)
        val result = WifiAnalysis.channelOccupancy(listOf(weakSingle, strongWide, sameChannel, adjacentOverlap), WifiBand.BAND_24)

        val channel1 = result.first { it.channel == 1 }
        val channel6 = result.first { it.channel == 6 }
        assertTrue(channel6.estimatedCongestion > channel1.estimatedCongestion)
        assertEquals(2, channel6.accessPoints.size)
        assertTrue(channel6.estimatedCongestion in 0f..1f)
    }

    @Test
    fun blankSsidHasNaturalDisplayName() {
        assertEquals(WifiAnalysis.HIDDEN_SSID, WifiAnalysis.displaySsid(""))
        assertEquals(WifiAnalysis.HIDDEN_SSID, WifiAnalysis.displaySsid("   "))
        assertEquals("Office", WifiAnalysis.displaySsid(" Office "))
    }

    private fun accessPoint(
        bssid: String,
        rssi: Int,
        timestamp: Long,
        channel: Int = 1,
        frequency: Int = 2412,
        width: Int = 20,
    ) = WifiAccessPoint(
        ssid = "Test", bssid = bssid.uppercase(), rssi = rssi, frequencyMhz = frequency,
        channel = channel, channelWidthMhz = width, capabilities = "[WPA2]", timestampMicros = timestamp,
        band = WifiAnalysis.bandFromFrequency(frequency)!!, signalQuality = WifiAnalysis.signalQuality(rssi),
        securityType = SecurityType.WPA2, wifiStandard = WifiStandard.WIFI_4,
        distanceRange = DistanceRange.THREE_TO_EIGHT, observedAtMillis = 1,
    )
}
