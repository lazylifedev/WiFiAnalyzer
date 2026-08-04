package com.lazyapps.wifianalyzer.ads

import com.lazyapps.wifianalyzer.BuildConfig

object AdConfiguration {
    const val debugNativeUnitId = "ca-app-pub-3940256099942544/2247696110"

    val homeNativeUnitId: String
        get() = if (BuildConfig.DEBUG) debugNativeUnitId else ""

    val devicesNativeUnitId: String
        get() = if (BuildConfig.DEBUG) debugNativeUnitId else ""

    val interstitialUnitId: String
        get() = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/1033173712" else "ca-app-pub-2834345829449590/4274627782"

    const val interstitialMinimumIntervalMillis = 5 * 60 * 1000L
    const val interstitialSessionLimit = 3
    const val initialCandidateSkipCount = 1
    const val transitionMinimum = 10
    const val transitionMaximum = 20
}
