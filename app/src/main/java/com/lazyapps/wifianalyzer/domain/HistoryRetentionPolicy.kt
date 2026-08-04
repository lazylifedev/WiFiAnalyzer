package com.lazyapps.wifianalyzer.domain

import com.lazyapps.wifianalyzer.model.SignalSample

fun interface TimeProvider { fun nowMillis(): Long }

object HistoryRetentionPolicy {
    const val FREE_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
    fun retain(samples: List<SignalSample>, nowMillis: Long, isPro: Boolean): List<SignalSample> =
        if (isPro) samples.takeLast(900) else samples.filter { it.timestampMillis >= nowMillis - FREE_RETENTION_MILLIS }.takeLast(900)
}
