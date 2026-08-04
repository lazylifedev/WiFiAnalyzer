package com.lazyapps.wifianalyzer.ads

object InlineNativeAdPolicy {
    const val HOME_AFTER_COUNT = 4
    val DEVICES_AFTER_COUNTS = listOf(5, 10)

    fun insertionIndex(itemCount: Int, afterCount: Int): Int? =
        if (itemCount <= 0) null else minOf(itemCount, afterCount)

    fun deviceInsertionIndices(itemCount: Int): List<Int> =
        DEVICES_AFTER_COUNTS.filter { itemCount >= it }

    fun canRequest(
        itemCount: Int,
        adsAllowed: Boolean,
        mobileAdsInitialized: Boolean,
        adUnitId: String,
    ): Boolean = itemCount > 0 && adsAllowed && mobileAdsInitialized && adUnitId.isNotBlank()
}
