package com.lazyapps.wifianalyzer.ui.scan

internal fun interface MonotonicClock {
    fun nowMillis(): Long
}

internal data class ScheduledScanRequest(
    val generation: Long,
    val scheduledAtMillis: Long,
    val delayMillis: Long,
)

/**
 * Fixed-rate scan request schedule. A generation token makes cancelled or replaced loops inert
 * even if cancellation races with a suspended delay.
 */
internal class ScanRequestSchedule(
    private val clock: MonotonicClock,
) {
    private var generation = 0L
    private var active = false
    private var intervalMillis = 0L
    private var nextScheduledAtMillis = 0L

    @Synchronized
    fun restart(intervalMillis: Long): ScheduledScanRequest {
        require(intervalMillis > 0L)
        generation++
        active = true
        this.intervalMillis = intervalMillis
        nextScheduledAtMillis = clock.nowMillis() + intervalMillis
        return current(generation)!!
    }

    @Synchronized
    fun stop() {
        active = false
        generation++
    }

    @Synchronized
    fun current(generation: Long): ScheduledScanRequest? {
        if (!active || generation != this.generation) return null
        return ScheduledScanRequest(
            generation = generation,
            scheduledAtMillis = nextScheduledAtMillis,
            delayMillis = (nextScheduledAtMillis - clock.nowMillis()).coerceAtLeast(0L),
        )
    }

    @Synchronized
    fun advance(generation: Long, actualAtMillis: Long = clock.nowMillis()): ScheduledScanRequest? {
        if (!active || generation != this.generation) return null
        do {
            nextScheduledAtMillis += intervalMillis
        } while (nextScheduledAtMillis <= actualAtMillis)
        return current(generation)
    }
}
