package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.data.ChannelDisplayMode
import com.lazyapps.wifianalyzer.domain.ChannelRecommendation
import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiStandard
import com.lazyapps.wifianalyzer.ui.screens.channel.ChannelGraphGeometry
import com.lazyapps.wifianalyzer.ui.screens.channel.GraphLabelRect
import com.lazyapps.wifianalyzer.ui.screens.channel.GraphPoint
import com.lazyapps.wifianalyzer.ui.screens.channel.GraphRange
import com.lazyapps.wifianalyzer.ui.screens.channel.graphHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelGraphAnalysisTest {
    @Test
    fun empty24GhzStillReturnsStableCandidateFrom1_6_11() {
        val result = ChannelRecommendation.bestCandidate(emptyList(), WifiBand.BAND_24)
        assertEquals(1, result?.channel)
        assertEquals(0f, result?.congestion)
    }

    @Test
    fun strongAndWideNetworksCreateMorePressureThanWeakNarrowNetwork() {
        val weak = ap("01", -90, 2412, 1, 20)
        val strongWide = ap("02", -42, 2437, 6, 40)
        val candidates = ChannelRecommendation.candidates(listOf(weak, strongWide), WifiBand.BAND_24)
        assertTrue(candidates.first { it.channel == 6 }.congestion > candidates.first { it.channel == 1 }.congestion)
        assertEquals(11, ChannelRecommendation.bestCandidate(listOf(weak, strongWide), WifiBand.BAND_24)?.channel)
    }

    @Test
    fun candidateIsIndependentOfInputOrderAndOtherBandsAreSafe() {
        val values = listOf(ap("01", -55, 5180, 36, 80), ap("02", -70, 5220, 44, 20))
        assertEquals(
            ChannelRecommendation.bestCandidate(values, WifiBand.BAND_5),
            ChannelRecommendation.bestCandidate(values.reversed(), WifiBand.BAND_5),
        )
        assertNotNull(ChannelRecommendation.bestCandidate(values, WifiBand.BAND_5))
        assertNull(ChannelRecommendation.bestCandidate(emptyList(), WifiBand.BAND_6))
    }

    @Test
    fun invalidWidthFallsBackAndScanConstantsMapInOnePlace() {
        assertEquals(20, WifiAnalysis.safeChannelWidthMhz(-1))
        assertEquals(20, WifiAnalysis.safeChannelWidthMhz(17))
        assertEquals(160, WifiAnalysis.channelWidthMhzFromScanResult(4))
        assertEquals(320, WifiAnalysis.channelWidthMhzFromScanResult(5))
    }

    @Test
    fun graphCoordinatesClampAndReflectFrequencyAndWidth() {
        val range = ChannelGraphGeometry.rangeFor(WifiBand.BAND_24, emptyList())
        assertEquals(10f, ChannelGraphGeometry.rssiToY(-30f, 10f, 110f), .01f)
        assertEquals(110f, ChannelGraphGeometry.rssiToY(-120f, 10f, 110f), .01f)
        assertEquals(10f, ChannelGraphGeometry.frequencyToX(2300f, range, 10f, 210f), .01f)
        val narrow = ChannelGraphGeometry.mountain(ap("01", -50, 2437, 6, 20), range, 10f, 210f, 10f, 110f)
        val wide = ChannelGraphGeometry.mountain(ap("02", -50, 2437, 6, 80), range, 10f, 210f, 10f, 110f)
        assertTrue(wide.rightX - wide.leftX > narrow.rightX - narrow.leftX)
        assertTrue(narrow.leftX >= 10f && narrow.rightX <= 210f)
    }

    @Test
    fun minimumMountainWidthPreservesBandwidthOrderAndBounds() {
        val range = GraphRange(5000f, 6000f)
        val widths = listOf(20, 40, 80, 160).mapIndexed { index, width ->
            ChannelGraphGeometry.mountain(
                ap("0$index", -50, 5500, 100 + index, width),
                range,
                10f,
                410f,
                0f,
                300f,
                minimumDisplayWidth = 60f,
            )
        }.map { it.rightX - it.leftX }
        assertTrue(widths.zipWithNext().all { (first, second) -> second > first })
        assertTrue(widths.first() >= 60f)
        assertTrue(widths.all { it <= 400f })

        val edge = ChannelGraphGeometry.mountain(
            ap("09", -50, 5000, 1, 20),
            range,
            10f,
            410f,
            0f,
            300f,
            minimumDisplayWidth = 60f,
        )
        assertTrue(edge.leftX >= 10f && edge.rightX <= 410f)
        assertEquals(edge.accessPoint.bssid, ChannelGraphGeometry.hitTest(listOf(edge), GraphPoint(35f, 200f))?.bssid)
    }

    @Test
    fun hitTestingSelectsPeakAndBlankClears() {
        val range = ChannelGraphGeometry.rangeFor(WifiBand.BAND_24, emptyList())
        val first = ChannelGraphGeometry.mountain(ap("01", -45, 2412, 1, 20), range, 0f, 500f, 0f, 300f)
        val second = ChannelGraphGeometry.mountain(ap("02", -70, 2462, 11, 20), range, 0f, 500f, 0f, 300f)
        assertEquals(first.accessPoint.bssid, ChannelGraphGeometry.hitTest(listOf(first, second), first.peak)?.bssid)
        assertNull(ChannelGraphGeometry.hitTest(listOf(first, second), GraphPoint(250f, 5f), 20f))
    }

    @Test
    fun labelsKeepPriorityOrderAvoidOverlapAndStayInside() {
        val bounds = GraphLabelRect(0f, 0f, 100f, 100f)
        val placed = ChannelGraphGeometry.placeLabels(
            listOf(
                "selected" to GraphLabelRect(-20f, -20f, 50f, 10f),
                "normal" to GraphLabelRect(0f, 0f, 60f, 30f),
                "other" to GraphLabelRect(60f, 60f, 120f, 90f),
            ),
            bounds,
        )
        assertEquals(listOf("selected", "other"), placed.map { it.first })
        assertTrue(placed.all { it.second.left >= 0 && it.second.right <= 100 })
    }

    @Test
    fun labelCandidatesPrioritizeSelectionAndCapRegisteredAndStrongest() {
        val selected = ap("01", -80, 2412, 1, 20)
        val registered = listOf(
            ap("02", -60, 2417, 2, 20).copy(isRegistered = true),
            ap("03", -65, 2422, 3, 20).copy(isRegistered = true),
            ap("04", -70, 2427, 4, 20).copy(isRegistered = true),
        )
        val ordinary = listOf(
            ap("05", -40, 2432, 5, 20),
            ap("06", -45, 2437, 6, 20),
            ap("07", -50, 2442, 7, 20),
        )
        val labels = ChannelGraphGeometry.labelCandidates(listOf(selected) + registered + ordinary, selected.bssid)
        assertEquals(selected.bssid, labels.first().bssid)
        assertEquals(2, labels.count { it.isRegistered })
        assertEquals(4, labels.size)
        assertEquals(ordinary.first().bssid, labels.last().bssid)
        assertTrue(ChannelGraphGeometry.labelText(selected, true).endsWith("-80 dBm"))
        assertTrue(!ChannelGraphGeometry.labelText(ordinary.first(), false).contains("dBm"))
    }

    @Test
    fun paletteAssignmentIsStableAndResponsiveHeightUsesExpectedClasses() {
        val bssids = (0 until 30).map { "02:00:00:00:00:${it.toString().padStart(2, '0')}" }
        val first = bssids.map { ChannelGraphGeometry.stablePaletteIndex(it, 6) }
        val second = bssids.map { ChannelGraphGeometry.stablePaletteIndex(it, 6) }
        assertEquals(first, second)
        assertTrue(first.toSet().size >= 4)
        assertEquals(340, graphHeightDp(720, 400))
        assertEquals(380, graphHeightDp(390, 800))
        assertEquals(440, graphHeightDp(700, 900))
        assertEquals(480, graphHeightDp(900, 1200))
    }

    @Test
    fun displayModeUsesStableNamesAndFallsBackToGraph() {
        assertEquals(ChannelDisplayMode.OCCUPANCY, ChannelDisplayMode.fromStored("OCCUPANCY"))
        assertEquals(ChannelDisplayMode.GRAPH, ChannelDisplayMode.fromStored("bad"))
        assertEquals(ChannelDisplayMode.GRAPH, ChannelDisplayMode.fromStored(null))
    }

    private fun ap(bssid: String, rssi: Int, frequency: Int, channel: Int, width: Int) = WifiAccessPoint(
        ssid = "AP-$bssid",
        bssid = "00:00:00:00:00:$bssid",
        rssi = rssi,
        frequencyMhz = frequency,
        channel = channel,
        channelWidthMhz = width,
        capabilities = "[WPA2]",
        timestampMicros = 1,
        band = WifiAnalysis.bandFromFrequency(frequency)!!,
        signalQuality = WifiAnalysis.signalQuality(rssi),
        securityType = SecurityType.WPA2,
        wifiStandard = WifiStandard.WIFI_6,
        distanceRange = DistanceRange.THREE_TO_EIGHT,
        observedAtMillis = 1,
    )
}
