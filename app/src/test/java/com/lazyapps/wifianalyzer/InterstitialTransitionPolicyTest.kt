package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.ads.InterstitialTransitionPolicy
import com.lazyapps.wifianalyzer.billing.AdPlacement
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class InterstitialTransitionPolicyTest {
    @Test fun targetIsWithinConfiguredRange() {
        repeat(100) { assertTrue(InterstitialTransitionPolicy(Random(it)).nextTargetForTest() in 10..20) }
    }

    @Test fun countsOnlyDistinctEligibleScreenTransitions() {
        val p = InterstitialTransitionPolicy(targetPicker = { 2 })
        assertFalse(p.record(AdPlacement.HOME))
        assertFalse(p.record(AdPlacement.HOME))
        assertFalse(p.record(AdPlacement.CHANNEL))
        assertTrue(p.record(AdPlacement.MONITOR))
    }

    @Test fun clearPreviousPreventsExcludedRouteFromCountingAsTransition() {
        val p = InterstitialTransitionPolicy(targetPicker = { 1 })
        p.record(AdPlacement.HOME)
        p.clearPrevious()
        assertFalse(p.record(AdPlacement.CHANNEL))
    }

    @Test fun reachedStatePersistsUntilSuccessfulAdAndThenRepicks() {
        val values = arrayOf(10, 20).iterator()
        val p = InterstitialTransitionPolicy(targetPicker = { values.next() })
        repeat(11) { p.record(if (it % 2 == 0) AdPlacement.HOME else AdPlacement.CHANNEL) }
        assertTrue(p.isReached())
        p.onAdShown()
        assertFalse(p.isReached())
        assertEquals(20, p.nextTargetForTest())
    }
}
