package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.review.*
import org.junit.Assert.*
import org.junit.Test

class ReviewEligibilityPolicyTest {
    private val now = 1_800_000_000_000L
    private fun eligibleHistory() = ReviewHistory(
        firstLaunchAt = now - ReviewEligibilityPolicy.MIN_INSTALL_AGE_MILLIS,
        launchSessionCount = ReviewEligibilityPolicy.MIN_SESSIONS,
        meaningfulSuccessCount = ReviewEligibilityPolicy.MIN_MEANINGFUL_SUCCESSES,
    )
    private val context = ReviewContext(onboardingCompleted = true)

    @Test fun firstLaunchIsNotEligible() = assertFalse(ReviewEligibilityPolicy.isEligible(ReviewHistory(firstLaunchAt = now), context, now))
    @Test fun lessThanSevenDaysIsNotEligible() = assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory().copy(firstLaunchAt = now - ReviewEligibilityPolicy.MIN_INSTALL_AGE_MILLIS + 1), context, now))
    @Test fun insufficientSessionsIsNotEligible() = assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory().copy(launchSessionCount = 4), context, now))
    @Test fun insufficientSuccessesIsNotEligible() = assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory().copy(meaningfulSuccessCount = 2), context, now))
    @Test fun allConditionsMetIsEligibleForFreeOrPro() {
        assertTrue(ReviewEligibilityPolicy.isEligible(eligibleHistory(), context, now))
        assertTrue(ReviewEligibilityPolicy.isEligible(eligibleHistory(), context, now))
    }
    @Test fun requestWithin120DaysIsNotEligible() = assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory().copy(lastReviewRequestAt = now - ReviewEligibilityPolicy.REQUEST_COOLDOWN_MILLIS + 1), context, now))
    @Test fun requestAt120DaysIsEligible() = assertTrue(ReviewEligibilityPolicy.isEligible(eligibleHistory().copy(lastReviewRequestAt = now - ReviewEligibilityPolicy.REQUEST_COOLDOWN_MILLIS), context, now))
    @Test fun recentErrorAndBusyUiAreNotEligible() {
        assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory().copy(lastMajorErrorAt = now), context, now))
        assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory(), context.copy(isBusy = true), now))
        assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory(), context.copy(hasModal = true), now))
        assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory(), context.copy(purchaseJustCompleted = true), now))
    }
    @Test fun onboardingIsRequired() = assertFalse(ReviewEligibilityPolicy.isEligible(eligibleHistory(), context.copy(onboardingCompleted = false), now))
}
