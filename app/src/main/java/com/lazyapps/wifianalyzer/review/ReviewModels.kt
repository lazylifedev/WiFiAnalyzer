package com.lazyapps.wifianalyzer.review

data class ReviewHistory(
    val firstLaunchAt: Long = 0,
    val launchSessionCount: Int = 0,
    val meaningfulSuccessCount: Int = 0,
    val lastMeaningfulSuccessAt: Long? = null,
    val lastReviewRequestAt: Long? = null,
    val reviewRequestCount: Int = 0,
    val reviewPromptSuppressedUntil: Long? = null,
    val lastMajorErrorAt: Long? = null,
)

data class ReviewContext(
    val onboardingCompleted: Boolean,
    val isBusy: Boolean = false,
    val hasModal: Boolean = false,
    val purchaseJustCompleted: Boolean = false,
)

object ReviewEligibilityPolicy {
    const val MIN_INSTALL_AGE_MILLIS = 7L * 24 * 60 * 60 * 1_000
    const val MIN_SESSIONS = 5
    const val MIN_MEANINGFUL_SUCCESSES = 3
    const val REQUEST_COOLDOWN_MILLIS = 120L * 24 * 60 * 60 * 1_000
    const val ERROR_COOLDOWN_MILLIS = 24L * 60 * 60 * 1_000

    fun isEligible(history: ReviewHistory, context: ReviewContext, now: Long): Boolean {
        if (!context.onboardingCompleted || context.isBusy || context.hasModal || context.purchaseJustCompleted) return false
        if (history.firstLaunchAt <= 0 || now - history.firstLaunchAt < MIN_INSTALL_AGE_MILLIS) return false
        if (history.launchSessionCount < MIN_SESSIONS || history.meaningfulSuccessCount < MIN_MEANINGFUL_SUCCESSES) return false
        if (history.reviewPromptSuppressedUntil?.let { now < it } == true) return false
        if (history.lastMajorErrorAt?.let { now - it < ERROR_COOLDOWN_MILLIS } == true) return false
        if (history.lastReviewRequestAt?.let { now - it < REQUEST_COOLDOWN_MILLIS } == true) return false
        return true
    }
}

sealed interface ReviewPromptState {
    data object Idle : ReviewPromptState
    data object Requesting : ReviewPromptState
    data object Completed : ReviewPromptState
}

interface ReviewCoordinator {
    suspend fun request(activity: android.app.Activity): Boolean
}

class FakeReviewCoordinator(private val succeeds: Boolean = true) : ReviewCoordinator {
    var requestCount = 0
        private set
    override suspend fun request(activity: android.app.Activity): Boolean {
        requestCount++
        return succeeds
    }
}
