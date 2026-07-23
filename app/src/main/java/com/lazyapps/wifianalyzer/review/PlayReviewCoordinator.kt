package com.lazyapps.wifianalyzer.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PlayReviewCoordinator : ReviewCoordinator {
    override suspend fun request(activity: Activity): Boolean = suspendCancellableCoroutine { continuation ->
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                if (continuation.isActive) continuation.resume(false)
                return@addOnCompleteListener
            }
            manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                if (continuation.isActive) continuation.resume(true)
            }
        }
    }
}

class ReviewPromptController(
    private val historyRepository: ReviewHistoryRepository,
    private val coordinator: ReviewCoordinator,
) {
    private var requestedThisSession = false

    suspend fun requestIfEligible(activity: Activity, context: ReviewContext, now: Long = System.currentTimeMillis()): Boolean {
        if (requestedThisSession) return false
        val history = historyRepository.current()
        if (!ReviewEligibilityPolicy.isEligible(history, context, now)) return false
        requestedThisSession = true
        historyRepository.recordRequest(now)
        coordinator.request(activity)
        return true
    }
}
