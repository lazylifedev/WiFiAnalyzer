package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.debug.DebugLogCategory
import com.lazyapps.wifianalyzer.debug.DebugLogStore
import com.lazyapps.wifianalyzer.debug.DebugUpdateSource
import com.lazyapps.wifianalyzer.debug.debugUpdateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogStoreTest {
    @Test
    fun capsEntriesAndDropsOldest() {
        var time = 0L
        val store = DebugLogStore({ true }, maxEntries = 3, { ++time }, { time })
        repeat(5) { store.add(DebugLogCategory.STATE, "state-$it") }

        assertEquals(listOf("state-2", "state-3", "state-4"), store.entries.value.map { it.message })
    }

    @Test
    fun clearRemovesEntries() {
        val store = DebugLogStore({ true }, wallClock = { 1L }, elapsedRealtime = { 1L })
        store.add(DebugLogCategory.STATE, "READY")
        store.clear()

        assertTrue(store.entries.value.isEmpty())
    }

    @Test
    fun disabledStoreIsNoOpIncludingClear() {
        var enabled = true
        val store = DebugLogStore({ enabled }, wallClock = { 1L }, elapsedRealtime = { 1L })
        store.add(DebugLogCategory.STATE, "kept")
        enabled = false
        store.add(DebugLogCategory.ERROR, "ignored")
        store.clear()

        assertEquals(listOf("kept"), store.entries.value.map { it.message })
    }

    @Test
    fun identicalCacheEventsAreAggregated() {
        val store = DebugLogStore({ true }, wallClock = { 1L }, elapsedRealtime = { 1L })
        repeat(3) {
            store.add(
                DebugLogCategory.CACHE_NO_CHANGE,
                "sameTimestamp=4 uiUpdated=false",
                DebugUpdateSource.OS_CACHE_NO_CHANGE,
                aggregate = true,
            )
        }

        assertEquals(1, store.entries.value.size)
        assertEquals(3, store.entries.value.single().repeated)
    }

    @Test
    fun updateSourcePrioritizesMeasurementEvidence() {
        assertEquals(DebugUpdateSource.NEW_SCAN_RESULT, debugUpdateSource(true, false, false))
        assertEquals(DebugUpdateSource.NEW_SCAN_RESULT, debugUpdateSource(false, true, false))
        assertEquals(DebugUpdateSource.OS_CACHE_UI_UPDATED, debugUpdateSource(false, false, true))
        assertEquals(DebugUpdateSource.OS_CACHE_NO_CHANGE, debugUpdateSource(false, false, false))
    }
}
