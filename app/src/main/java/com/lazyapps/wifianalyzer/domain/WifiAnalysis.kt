package com.lazyapps.wifianalyzer.domain

import com.lazyapps.wifianalyzer.model.ChannelOccupancy
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import kotlin.math.exp

object WifiAnalysis {
    const val HIDDEN_SSID = "非公開ネットワーク"

    fun displaySsid(ssid: String?): String = ssid?.trim().takeUnless { it.isNullOrEmpty() } ?: HIDDEN_SSID

    fun channelFromFrequency(frequencyMhz: Int): Int = when {
        frequencyMhz == 2484 -> 14
        frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
        frequencyMhz in 4900..4995 -> (frequencyMhz - 4000) / 5
        frequencyMhz in 5000..5895 -> (frequencyMhz - 5000) / 5
        frequencyMhz == 5935 -> 2
        frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> 0
    }

    fun bandFromFrequency(frequencyMhz: Int): WifiBand? = when (frequencyMhz) {
        in 2400..2500 -> WifiBand.BAND_24
        in 4900..5899 -> WifiBand.BAND_5
        in 5925..7125 -> WifiBand.BAND_6
        else -> null
    }

    fun signalQuality(rssi: Int): SignalQuality = when {
        rssi >= -55 -> SignalQuality.EXCELLENT
        rssi >= -67 -> SignalQuality.GOOD
        rssi >= -79 -> SignalQuality.FAIR
        else -> SignalQuality.WEAK
    }

    fun distanceRange(rssiSamples: List<Int>, band: WifiBand): DistanceRange {
        if (rssiSamples.isEmpty()) return DistanceRange.TWENTY_PLUS
        val sorted = rssiSamples.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        val bandCorrection = when (band) {
            WifiBand.BAND_24 -> 0
            WifiBand.BAND_5 -> 3
            WifiBand.BAND_6 -> 5
        }
        val adjusted = median + bandCorrection
        val base = when {
            adjusted >= -55 -> DistanceRange.ONE_TO_THREE
            adjusted >= -67 -> DistanceRange.THREE_TO_EIGHT
            adjusted >= -79 -> DistanceRange.EIGHT_TO_TWENTY
            else -> DistanceRange.TWENTY_PLUS
        }
        val spread = (sorted[sorted.size * 3 / 4] - sorted[sorted.size / 4]).coerceAtLeast(0)
        return if (spread >= 16) base.nextFarther() else base
    }

    fun securityType(capabilities: String): SecurityType {
        val value = capabilities.uppercase()
        return when {
            "EAP" in value || "IEEE8021X" in value -> SecurityType.ENTERPRISE
            "SAE" in value || "WPA3" in value -> SecurityType.WPA3
            "OWE" in value -> SecurityType.OWE
            "WPA2" in value || "RSN" in value -> SecurityType.WPA2
            "WPA" in value -> SecurityType.WPA
            "WEP" in value -> SecurityType.WEP
            value.isBlank() || value == "[ESS]" -> SecurityType.OPEN
            else -> SecurityType.UNKNOWN
        }
    }

    fun deduplicateByBssid(accessPoints: List<WifiAccessPoint>): List<WifiAccessPoint> =
        accessPoints
            .filter { it.bssid.isNotBlank() }
            .groupBy { it.bssid.uppercase() }
            .mapNotNull { (_, matches) -> matches.maxByOrNull { it.timestampMicros } }
            .sortedByDescending { it.rssi }

    fun channelOccupancy(accessPoints: List<WifiAccessPoint>, band: WifiBand): List<ChannelOccupancy> {
        val inBand = accessPoints.filter { it.band == band && it.channel > 0 }
        return inBand.groupBy { it.channel }.toSortedMap().map { (channel, direct) ->
            val frequency = direct.maxByOrNull { it.rssi }?.frequencyMhz ?: 0
            val pressure = inBand.sumOf { ap ->
                signalWeight(ap.rssi) * widthWeight(ap.channelWidthMhz) * overlapWeight(
                    targetFrequency = frequency,
                    targetBand = band,
                    interferer = ap,
                )
            }
            ChannelOccupancy(
                channel = channel,
                frequencyMhz = frequency,
                estimatedCongestion = (1.0 - exp(-pressure / 2.2)).toFloat().coerceIn(0f, 1f),
                accessPoints = direct.sortedByDescending { it.rssi },
            )
        }
    }

    private fun signalWeight(rssi: Int): Double = ((rssi + 100).coerceIn(5, 55) / 55.0)

    private fun widthWeight(widthMhz: Int): Double = when {
        widthMhz >= 160 -> 1.8
        widthMhz >= 80 -> 1.5
        widthMhz >= 40 -> 1.25
        else -> 1.0
    }

    private fun overlapWeight(targetFrequency: Int, targetBand: WifiBand, interferer: WifiAccessPoint): Double {
        val distance = kotlin.math.abs(targetFrequency - interferer.frequencyMhz).toDouble()
        return if (targetBand == WifiBand.BAND_24) {
            (1.0 - distance / 25.0).coerceIn(0.0, 1.0)
        } else {
            val halfSpan = (20 + interferer.channelWidthMhz.coerceAtLeast(20)) / 2.0
            (1.0 - distance / halfSpan).coerceIn(0.0, 1.0)
        }
    }

    private fun DistanceRange.nextFarther(): DistanceRange = when (this) {
        DistanceRange.ONE_TO_THREE -> DistanceRange.THREE_TO_EIGHT
        DistanceRange.THREE_TO_EIGHT -> DistanceRange.EIGHT_TO_TWENTY
        DistanceRange.EIGHT_TO_TWENTY, DistanceRange.TWENTY_PLUS -> DistanceRange.TWENTY_PLUS
    }
}
