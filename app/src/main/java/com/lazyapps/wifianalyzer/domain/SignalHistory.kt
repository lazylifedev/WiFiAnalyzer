package com.lazyapps.wifianalyzer.domain

import com.lazyapps.wifianalyzer.model.SignalSample

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
}
