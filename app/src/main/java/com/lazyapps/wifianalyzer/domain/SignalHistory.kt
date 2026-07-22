package com.lazyapps.wifianalyzer.domain

import com.lazyapps.wifianalyzer.model.SignalSample
import kotlin.math.ceil
import kotlin.math.floor

data class SignalStatistics(val maximum: Int, val average: Double, val minimum: Int, val latest: Int)

object SignalHistoryPolicy {
    const val MAX_WINDOW_MS = 15 * 60_000L
    const val MAX_SAMPLES_PER_BSSID = 900
    const val GAP_THRESHOLD_MS = 35_000L

    fun trim(samples: List<SignalSample>, now: Long): List<SignalSample> = samples
        .asSequence()
        .filter { now - it.timestampMillis <= MAX_WINDOW_MS }
        .distinctBy { it.timestampMillis }
        .sortedBy { it.timestampMillis }
        .toList()
        .takeLast(MAX_SAMPLES_PER_BSSID)

    fun segments(samples: List<SignalSample>): List<List<SignalSample>> {
        if (samples.isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<SignalSample>>()
        samples.sortedBy { it.timestampMillis }.forEach { sample ->
            val current = result.lastOrNull()
            if (current == null || sample.timestampMillis - current.last().timestampMillis > GAP_THRESHOLD_MS) {
                result += mutableListOf(sample)
            } else current += sample
        }
        return result
    }

    fun statistics(samples: List<SignalSample>): SignalStatistics? = samples.takeIf { it.isNotEmpty() }?.let {
        SignalStatistics(it.maxOf(SignalSample::rssi), it.map(SignalSample::rssi).average(), it.minOf(SignalSample::rssi), it.maxBy(SignalSample::timestampMillis).rssi)
    }

    fun adaptiveYAxis(samples: List<SignalSample>): SignalAxisRange {
        if (samples.isEmpty()) return SignalAxisRange(-100, -30)
        val dataMin = samples.minOf { it.rssi }.coerceIn(-100, -20)
        val dataMax = samples.maxOf { it.rssi }.coerceIn(-100, -20)
        val padding = ceil((dataMax - dataMin).coerceAtLeast(1) * .15).toInt().coerceAtLeast(3)
        var lower = floor((dataMin - padding) / 5.0).toInt() * 5
        var upper = ceil((dataMax + padding) / 5.0).toInt() * 5
        if (upper - lower < 20) {
            val missing = 20 - (upper - lower)
            lower -= missing / 2
            upper += missing - missing / 2
            lower = floor(lower / 5.0).toInt() * 5
            upper = ceil(upper / 5.0).toInt() * 5
        }
        lower = lower.coerceAtLeast(-100)
        upper = upper.coerceAtMost(-20)
        if (upper - lower < 20) {
            if (lower == -100) upper = -80 else lower = (upper - 20).coerceAtLeast(-100)
        }
        return SignalAxisRange(lower, upper)
    }
}

data class SignalAxisRange(val lower: Int, val upper: Int) { val span: Int get() = upper - lower }
