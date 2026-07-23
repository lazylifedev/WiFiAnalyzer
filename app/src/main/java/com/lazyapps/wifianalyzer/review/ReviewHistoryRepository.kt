package com.lazyapps.wifianalyzer.review

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reviewDataStore by preferencesDataStore("review_history")

class ReviewHistoryRepository(private val context: Context) {
    val history: Flow<ReviewHistory> = context.reviewDataStore.data.map { p ->
        ReviewHistory(
            firstLaunchAt = p[FIRST_LAUNCH_AT] ?: 0,
            launchSessionCount = p[LAUNCH_SESSION_COUNT] ?: 0,
            meaningfulSuccessCount = p[MEANINGFUL_SUCCESS_COUNT] ?: 0,
            lastMeaningfulSuccessAt = p[LAST_MEANINGFUL_SUCCESS_AT],
            lastReviewRequestAt = p[LAST_REVIEW_REQUEST_AT],
            reviewRequestCount = p[REVIEW_REQUEST_COUNT] ?: 0,
            reviewPromptSuppressedUntil = p[REVIEW_PROMPT_SUPPRESSED_UNTIL],
            lastMajorErrorAt = p[LAST_MAJOR_ERROR_AT],
        )
    }

    suspend fun recordLaunch(now: Long = System.currentTimeMillis()) = context.reviewDataStore.edit { p ->
        if ((p[FIRST_LAUNCH_AT] ?: 0) == 0L) p[FIRST_LAUNCH_AT] = now
        p[LAUNCH_SESSION_COUNT] = (p[LAUNCH_SESSION_COUNT] ?: 0) + 1
    }

    suspend fun recordMeaningfulSuccess(now: Long = System.currentTimeMillis()) = context.reviewDataStore.edit { p ->
        p[MEANINGFUL_SUCCESS_COUNT] = (p[MEANINGFUL_SUCCESS_COUNT] ?: 0) + 1
        p[LAST_MEANINGFUL_SUCCESS_AT] = now
    }

    suspend fun recordMajorError(now: Long = System.currentTimeMillis()) = context.reviewDataStore.edit { p -> p[LAST_MAJOR_ERROR_AT] = now }

    suspend fun recordRequest(now: Long = System.currentTimeMillis()) = context.reviewDataStore.edit { p ->
        p[LAST_REVIEW_REQUEST_AT] = now
        p[REVIEW_REQUEST_COUNT] = (p[REVIEW_REQUEST_COUNT] ?: 0) + 1
    }

    suspend fun current() = history.first()

    private companion object {
        val FIRST_LAUNCH_AT = longPreferencesKey("firstLaunchAt")
        val LAUNCH_SESSION_COUNT = intPreferencesKey("launchSessionCount")
        val MEANINGFUL_SUCCESS_COUNT = intPreferencesKey("meaningfulSuccessCount")
        val LAST_MEANINGFUL_SUCCESS_AT = longPreferencesKey("lastMeaningfulSuccessAt")
        val LAST_REVIEW_REQUEST_AT = longPreferencesKey("lastReviewRequestAt")
        val REVIEW_REQUEST_COUNT = intPreferencesKey("reviewRequestCount")
        val REVIEW_PROMPT_SUPPRESSED_UNTIL = longPreferencesKey("reviewPromptSuppressedUntil")
        val LAST_MAJOR_ERROR_AT = longPreferencesKey("lastMajorErrorAt")
    }
}
