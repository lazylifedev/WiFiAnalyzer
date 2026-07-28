package com.lazyapps.wifianalyzer.domain

import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import kotlin.math.abs
import kotlin.math.exp

data class ChannelCandidate(
    val channel: Int,
    val frequencyMhz: Int,
    val congestion: Float,
)

object ChannelRecommendation {
    fun bestCandidate(accessPoints: List<WifiAccessPoint>, band: WifiBand): ChannelCandidate? =
        candidates(accessPoints, band).minWithOrNull(compareBy(ChannelCandidate::congestion, ChannelCandidate::channel))

    fun candidates(accessPoints: List<WifiAccessPoint>, band: WifiBand): List<ChannelCandidate> {
        val inBand = accessPoints.filter { it.band == band && it.frequencyMhz > 0 }
        val frequencies = when (band) {
            WifiBand.BAND_24 -> listOf(2412, 2437, 2462)
            WifiBand.BAND_5 -> detected20MhzCenters(inBand, listOf(5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805))
            WifiBand.BAND_6 -> detected20MhzCenters(inBand, emptyList())
        }
        return frequencies.distinct().sorted().mapNotNull { frequency ->
            val channel = WifiAnalysis.channelFromFrequency(frequency)
            if (channel <= 0) return@mapNotNull null
            val pressure = inBand.sumOf { ap ->
                val width = WifiAnalysis.safeChannelWidthMhz(ap.channelWidthMhz)
                val targetHalf = 10.0
                val apHalf = width / 2.0
                val overlap = ((targetHalf + apHalf - abs(frequency - ap.frequencyMhz)) / (targetHalf + apHalf))
                    .coerceIn(0.0, 1.0)
                val signal = ((ap.rssi.coerceIn(-100, -30) + 100) / 70.0).let { it * it }
                overlap * signal * (width / 20.0).coerceAtMost(4.0)
            }
            ChannelCandidate(channel, frequency, (1.0 - exp(-pressure / 2.2)).toFloat().coerceIn(0f, 1f))
        }
    }

    private fun detected20MhzCenters(
        accessPoints: List<WifiAccessPoint>,
        fallback: List<Int>,
    ): List<Int> {
        if (accessPoints.isEmpty()) return fallback
        return accessPoints.flatMap { ap ->
            val width = WifiAnalysis.safeChannelWidthMhz(ap.channelWidthMhz)
            val count = (width / 20).coerceAtLeast(1)
            val first = ap.frequencyMhz - (count - 1) * 10
            List(count) { first + it * 20 }
        }
    }
}
