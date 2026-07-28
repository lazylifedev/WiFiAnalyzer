package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.ui.scan.MonotonicClock
import com.lazyapps.wifianalyzer.ui.scan.ScanRequestSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanRequestScheduleTest {
    private val clock = FakeClock()
    private val schedule = ScanRequestSchedule(clock)

    @Test
    fun fiveSecondScheduleUsesFixedRateWithoutProcessingDrift() {
        val first = schedule.restart(5_000)
        assertEquals(5_000L, first.scheduledAtMillis)

        clock.now = 5_400
        val second = schedule.advance(first.generation)
        assertEquals(10_000L, second?.scheduledAtMillis)
        assertEquals(4_600L, second?.delayMillis)
    }

    @Test
    fun changingFiveToTwentyInvalidatesOldLoopAndUsesTwentySecondsNext() {
        val old = schedule.restart(5_000)
        clock.now = 2_000
        val replacement = schedule.restart(20_000)

        assertNull(schedule.current(old.generation))
        assertEquals(22_000L, replacement.scheduledAtMillis)
    }

    @Test
    fun changingTwentyToFiveDoesNotWaitForOldTwentySecondDeadline() {
        val old = schedule.restart(20_000)
        clock.now = 2_000
        val replacement = schedule.restart(5_000)

        assertNull(schedule.current(old.generation))
        assertEquals(7_000L, replacement.scheduledAtMillis)
    }

    @Test
    fun backgroundStopsRequestsAndForegroundStartsOnlyOneGeneration() {
        val foreground = schedule.restart(5_000)
        schedule.stop()
        clock.now = 10_000
        assertNull(schedule.current(foreground.generation))

        val resumed = schedule.restart(5_000)
        assertEquals(resumed, schedule.current(resumed.generation))
    }

    @Test
    fun missedDeadlineSkipsPastTicksInsteadOfBurstingRequests() {
        val first = schedule.restart(3_000)
        clock.now = 10_000
        val next = schedule.advance(first.generation)

        assertEquals(12_000L, next?.scheduledAtMillis)
        assertEquals(2_000L, next?.delayMillis)
    }

    @Test
    fun rejectedScanDoesNotStopTheNextScheduledRequest() {
        val first = schedule.restart(5_000)
        clock.now = first.scheduledAtMillis

        // startScan() returning false does not mutate the schedule; completing the attempt advances it.
        val afterRejectedRequest = schedule.advance(first.generation)

        assertEquals(10_000L, afterRejectedRequest?.scheduledAtMillis)
    }

    private class FakeClock(var now: Long = 0L) : MonotonicClock {
        override fun nowMillis(): Long = now
    }
}
