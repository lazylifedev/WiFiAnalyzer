package com.lazyapps.wifianalyzer.debug

data class DebugLogEntry(
    val id: Long,
    val wallClockMillis: Long,
    val elapsedRealtimeMillis: Long,
    val category: DebugLogCategory,
    val message: String,
    val updateSource: DebugUpdateSource? = null,
    val repeated: Int = 1,
)

enum class DebugLogCategory {
    SCAN_REQUEST,
    BROADCAST,
    CACHE_POLL,
    UI_UPDATE_NEW_SCAN,
    UI_UPDATE_CACHE,
    CACHE_NO_CHANGE,
    STATE,
    LIFECYCLE,
    ERROR,
}

enum class DebugUpdateSource {
    NEW_SCAN_RESULT,
    OS_CACHE_UI_UPDATED,
    OS_CACHE_NO_CHANGE,
}

internal fun debugUpdateSource(
    hasNewMeasurements: Boolean,
    hasTimestampChanges: Boolean,
    uiNotified: Boolean,
): DebugUpdateSource = when {
    hasNewMeasurements || hasTimestampChanges -> DebugUpdateSource.NEW_SCAN_RESULT
    uiNotified -> DebugUpdateSource.OS_CACHE_UI_UPDATED
    else -> DebugUpdateSource.OS_CACHE_NO_CHANGE
}
