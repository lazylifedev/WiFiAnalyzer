package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.ads.InlineNativeAdPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineNativeAdPolicyTest {
    @Test fun homeUsesFourthPositionOrEnd() {
        assertNull(InlineNativeAdPolicy.insertionIndex(0, InlineNativeAdPolicy.HOME_AFTER_COUNT))
        assertEquals(2, InlineNativeAdPolicy.insertionIndex(2, InlineNativeAdPolicy.HOME_AFTER_COUNT))
        assertEquals(4, InlineNativeAdPolicy.insertionIndex(4, InlineNativeAdPolicy.HOME_AFTER_COUNT))
        assertEquals(4, InlineNativeAdPolicy.insertionIndex(8, InlineNativeAdPolicy.HOME_AFTER_COUNT))
    }

    @Test fun devicesUsesThirdPositionOrEnd() {
        assertNull(InlineNativeAdPolicy.insertionIndex(0, InlineNativeAdPolicy.DEVICES_AFTER_COUNT))
        assertEquals(2, InlineNativeAdPolicy.insertionIndex(2, InlineNativeAdPolicy.DEVICES_AFTER_COUNT))
        assertEquals(3, InlineNativeAdPolicy.insertionIndex(3, InlineNativeAdPolicy.DEVICES_AFTER_COUNT))
        assertEquals(3, InlineNativeAdPolicy.insertionIndex(8, InlineNativeAdPolicy.DEVICES_AFTER_COUNT))
    }

    @Test fun requestRequiresEveryGate() {
        assertTrue(InlineNativeAdPolicy.canRequest(1, adsAllowed = true, mobileAdsInitialized = true, adUnitId = "test"))
        assertFalse(InlineNativeAdPolicy.canRequest(0, adsAllowed = true, mobileAdsInitialized = true, adUnitId = "test"))
        assertFalse(InlineNativeAdPolicy.canRequest(1, adsAllowed = false, mobileAdsInitialized = true, adUnitId = "test"))
        assertFalse(InlineNativeAdPolicy.canRequest(1, adsAllowed = true, mobileAdsInitialized = false, adUnitId = "test"))
        assertFalse(InlineNativeAdPolicy.canRequest(1, adsAllowed = true, mobileAdsInitialized = true, adUnitId = ""))
    }

    @Test fun insertingOncePreservesNormalOrder() {
        val source = listOf("a", "b", "c", "d", "e")
        val index = InlineNativeAdPolicy.insertionIndex(source.size, 3)!!
        val rendered = source.take(index) + "inline_native_ad" + source.drop(index)
        assertEquals(listOf("a", "b", "c", "inline_native_ad", "d", "e"), rendered)
        assertEquals(source, rendered.filterNot { it == "inline_native_ad" })
        assertEquals(1, rendered.count { it == "inline_native_ad" })
    }
}
