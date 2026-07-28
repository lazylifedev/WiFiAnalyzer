package com.lazyapps.wifianalyzer.ui.screens.channel

import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

data class GraphRange(val minFrequencyMhz: Float, val maxFrequencyMhz: Float)
data class GraphPoint(val x: Float, val y: Float)
data class GraphMountain(
    val accessPoint: WifiAccessPoint,
    val leftX: Float,
    val peak: GraphPoint,
    val rightX: Float,
)
data class GraphLabelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: GraphLabelRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

object ChannelGraphGeometry {
    const val MIN_RSSI = -100
    const val MAX_RSSI = -30

    fun rangeFor(band: WifiBand, accessPoints: List<WifiAccessPoint>): GraphRange = when (band) {
        WifiBand.BAND_24 -> GraphRange(2400f, 2496f)
        WifiBand.BAND_5 -> dynamicRange(accessPoints, 5170f, 5835f)
        WifiBand.BAND_6 -> dynamicRange(accessPoints, 5945f, 7125f)
    }

    private fun dynamicRange(accessPoints: List<WifiAccessPoint>, fallbackMin: Float, fallbackMax: Float): GraphRange {
        val valid = accessPoints.map { it.frequencyMhz }.filter { it > 0 }
        if (valid.isEmpty()) return GraphRange(fallbackMin, fallbackMax)
        val min = (valid.min() - 90).coerceAtLeast(fallbackMin.toInt()).toFloat()
        val max = (valid.max() + 90).coerceAtMost(fallbackMax.toInt()).toFloat()
        return if (max - min < 180f) GraphRange(min, (min + 180f).coerceAtMost(fallbackMax)) else GraphRange(min, max)
    }

    fun frequencyToX(frequencyMhz: Float, range: GraphRange, left: Float, right: Float): Float {
        val fraction = ((frequencyMhz - range.minFrequencyMhz) / (range.maxFrequencyMhz - range.minFrequencyMhz))
            .coerceIn(0f, 1f)
        return left + fraction * (right - left)
    }

    fun rssiToY(rssi: Float, top: Float, bottom: Float): Float {
        val fraction = ((rssi.coerceIn(MIN_RSSI.toFloat(), MAX_RSSI.toFloat()) - MAX_RSSI) /
            (MIN_RSSI - MAX_RSSI))
        return top + fraction * (bottom - top)
    }

    fun mountain(
        ap: WifiAccessPoint,
        range: GraphRange,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        minimumDisplayWidth: Float = 0f,
    ): GraphMountain {
        val width = WifiAnalysis.safeChannelWidthMhz(ap.channelWidthMhz)
        val peakX = frequencyToX(ap.frequencyMhz.toFloat(), range, left, right)
        val actualLeft = frequencyToX(ap.frequencyMhz - width / 2f, range, left, right)
        val actualRight = frequencyToX(ap.frequencyMhz + width / 2f, range, left, right)
        val minimumForBandwidth = minimumDisplayWidth * sqrt(width / 20f)
        val displayWidth = max(actualRight - actualLeft, minimumForBandwidth).coerceAtMost(right - left)
        val displayLeft = (peakX - displayWidth / 2f).coerceIn(left, right - displayWidth)
        return GraphMountain(
            ap,
            displayLeft,
            GraphPoint(peakX, rssiToY(ap.rssi.toFloat(), top, bottom)),
            displayLeft + displayWidth,
        )
    }

    fun hitTest(mountains: List<GraphMountain>, point: GraphPoint, maxDistance: Float = 64f): WifiAccessPoint? =
        mountains.asSequence()
            .map { it to hypot((point.x - it.peak.x).toDouble(), (point.y - it.peak.y).toDouble()).toFloat() }
            .filter { (mountain, distance) ->
                point.x in (mountain.leftX - 12f)..(mountain.rightX + 12f) &&
                    (distance <= maxDistance || point.y >= mountain.peak.y)
            }
            .minWithOrNull(compareBy<Pair<GraphMountain, Float>> { it.second }.thenByDescending { it.first.accessPoint.rssi })
            ?.first?.accessPoint

    fun placeLabels(candidates: List<Pair<String, GraphLabelRect>>, bounds: GraphLabelRect): List<Pair<String, GraphLabelRect>> {
        val accepted = mutableListOf<Pair<String, GraphLabelRect>>()
        candidates.forEach { (id, rect) ->
            val width = (rect.right - rect.left).coerceAtMost(bounds.right - bounds.left)
            val height = (rect.bottom - rect.top).coerceAtMost(bounds.bottom - bounds.top)
            val clampedLeft = rect.left.coerceIn(bounds.left, bounds.right - width)
            val clampedTop = rect.top.coerceIn(bounds.top, bounds.bottom - height)
            val clamped = GraphLabelRect(
                clampedLeft,
                clampedTop,
                clampedLeft + width,
                clampedTop + height,
            )
            if (accepted.none { it.second.overlaps(clamped) }) accepted += id to clamped
        }
        return accepted
    }

    fun labelCandidates(accessPoints: List<WifiAccessPoint>, selectedBssid: String?): List<WifiAccessPoint> {
        val selected = accessPoints.firstOrNull { it.bssid == selectedBssid }
        val registered = accessPoints.asSequence()
            .filter { it.isRegistered && it.bssid != selectedBssid }
            .sortedByDescending { it.rssi }
            .take(2)
            .toList()
        val excluded = buildSet {
            selected?.let { add(it.bssid) }
            registered.forEach { add(it.bssid) }
        }
        val strongestLimit = if (selected == null) 2 else 1
        val strongest = accessPoints.asSequence()
            .filter { it.bssid !in excluded }
            .sortedByDescending { it.rssi }
            .take(strongestLimit)
            .toList()
        return listOfNotNull(selected) + registered + strongest
    }

    fun stablePaletteIndex(bssid: String, paletteSize: Int): Int {
        require(paletteSize > 0)
        return (bssid.hashCode() and Int.MAX_VALUE) % paletteSize
    }

    fun labelText(accessPoint: WifiAccessPoint, selected: Boolean): String {
        val name = (accessPoint.registeredDeviceName ?: accessPoint.ssid).take(18)
        return if (selected) "$name  ${accessPoint.rssi} dBm" else name
    }
}
