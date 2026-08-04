package com.lazyapps.wifianalyzer.ads

object InlineNativeAdPolicy {
    const val HOME_AFTER_COUNT = 4
    const val DEVICES_AFTER_COUNT = 3

    fun insertionIndex(itemCount: Int, afterCount: Int): Int? =
        if (itemCount <= 0) null else minOf(itemCount, afterCount)

    fun canRequest(
        itemCount: Int,
        adsAllowed: Boolean,
        mobileAdsInitialized: Boolean,
        adUnitId: String,
    ): Boolean = itemCount > 0 && adsAllowed && mobileAdsInitialized && adUnitId.isNotBlank()
}
