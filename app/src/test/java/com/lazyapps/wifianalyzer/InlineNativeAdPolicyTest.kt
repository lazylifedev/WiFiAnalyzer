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

    @Test fun devicesUsesFifthAndTenthPositionsWithMaximumTwo() {
        val expected = mapOf(
            0 to emptyList(), 1 to emptyList(), 4 to emptyList(),
            5 to listOf(5), 9 to listOf(5),
            10 to listOf(5, 10), 11 to listOf(5, 10), 20 to listOf(5, 10),
        )
        expected.forEach { (count, indices) ->
            assertEquals(indices, InlineNativeAdPolicy.deviceInsertionIndices(count))
        }
        assertTrue(InlineNativeAdPolicy.deviceInsertionIndices(100).size <= 2)
    }

    @Test fun insertingDeviceAdsPreservesOrderAndStableKeys() {
        val source = (1..20).map { "device_$it" }
        val insertions = InlineNativeAdPolicy.deviceInsertionIndices(source.size)
        val rendered = buildList {
            source.forEachIndexed { index, item ->
                add(item)
                if (index + 1 in insertions) add("devices_inline_native_ad_${insertions.indexOf(index + 1) + 1}")
            }
        }
        assertEquals(source, rendered.filter { it.startsWith("device_") })
        assertEquals(rendered.size, rendered.distinct().size)
        assertEquals(2, rendered.count { it.startsWith("devices_inline_native_ad_") })
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
