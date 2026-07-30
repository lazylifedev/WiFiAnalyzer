package com.lazyapps.wifianalyzer.debug

import android.os.SystemClock
import com.lazyapps.wifianalyzer.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DebugLogStore(
    private val enabled: () -> Boolean,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val lock = Any()
    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()
    private var nextId = 1L

    fun add(
        category: DebugLogCategory,
        message: String,
        updateSource: DebugUpdateSource? = null,
        aggregate: Boolean = false,
    ) {
        if (!enabled()) return
        synchronized(lock) {
            val nowWall = wallClock()
            val nowElapsed = elapsedRealtime()
            val current = _entries.value
            val last = current.lastOrNull()
            val next = if (
                aggregate && last?.category == category && last.message == message &&
                last.updateSource == updateSource
            ) {
                current.dropLast(1) + last.copy(
                    wallClockMillis = nowWall,
                    elapsedRealtimeMillis = nowElapsed,
                    repeated = last.repeated + 1,
                )
            } else {
                current + DebugLogEntry(
                    id = nextId++,
                    wallClockMillis = nowWall,
                    elapsedRealtimeMillis = nowElapsed,
                    category = category,
                    message = message,
                    updateSource = updateSource,
                )
            }
            _entries.value = next.takeLast(maxEntries)
        }
    }

    fun clear() {
        if (!enabled()) return
        synchronized(lock) { _entries.value = emptyList() }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 100
    }
}

object DebugLogs {
    val store = DebugLogStore(enabled = { BuildConfig.DEBUG })
}
