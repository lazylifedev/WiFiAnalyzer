package com.lazyapps.wifianalyzer.ui.screens.channel

import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import kotlin.math.hypot

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

    fun mountain(ap: WifiAccessPoint, range: GraphRange, left: Float, right: Float, top: Float, bottom: Float): GraphMountain {
        val width = WifiAnalysis.safeChannelWidthMhz(ap.channelWidthMhz)
        return GraphMountain(
            ap,
            frequencyToX(ap.frequencyMhz - width / 2f, range, left, right),
            GraphPoint(frequencyToX(ap.frequencyMhz.toFloat(), range, left, right), rssiToY(ap.rssi.toFloat(), top, bottom)),
            frequencyToX(ap.frequencyMhz + width / 2f, range, left, right),
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
            val clamped = GraphLabelRect(
                rect.left.coerceIn(bounds.left, bounds.right - (rect.right - rect.left)),
                rect.top.coerceIn(bounds.top, bounds.bottom - (rect.bottom - rect.top)),
                rect.right.coerceIn(bounds.left + (rect.right - rect.left), bounds.right),
                rect.bottom.coerceIn(bounds.top + (rect.bottom - rect.top), bounds.bottom),
            )
            if (accepted.none { it.second.overlaps(clamped) }) accepted += id to clamped
        }
        return accepted
    }
}
