package com.lazyapps.wifianalyzer.ads

import com.lazyapps.wifianalyzer.BuildConfig

object AdConfiguration {
    val interstitialUnitId: String
        get() = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/1033173712" else "ca-app-pub-2834345829449590/4274627782"

    const val interstitialMinimumIntervalMillis = 5 * 60 * 1000L
    const val interstitialSessionLimit = 2
    const val initialCandidateSkipCount = 1
}
