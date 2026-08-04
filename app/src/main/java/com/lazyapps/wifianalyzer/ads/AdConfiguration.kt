package com.lazyapps.wifianalyzer.ads

import com.lazyapps.wifianalyzer.BuildConfig

object AdConfiguration {
    const val debugNativeUnitId = "ca-app-pub-3940256099942544/2247696110"

    val homeNativeUnitId: String
        get() = if (BuildConfig.DEBUG) debugNativeUnitId else ""

    val devicesNativeUnitId: String
        get() = if (BuildConfig.DEBUG) debugNativeUnitId else ""

}
