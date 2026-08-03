package com.lazyapps.wifianalyzer.ads

import com.lazyapps.wifianalyzer.billing.AdPlacement
import kotlin.random.Random

/** Counts only completed changes between the five ad-eligible top-level screens. */
internal class InterstitialTransitionPolicy(
    private val random: Random = Random.Default,
    private val targetPicker: () -> Int = { random.nextInt(AdConfiguration.transitionMinimum, AdConfiguration.transitionMaximum + 1) },
) {
    private var target = targetPicker()
    private var count = 0
    private var reached = false
    private var previous: AdPlacement? = null

    fun record(route: AdPlacement): Boolean {
        val old = previous
        previous = route
        if (old == null || old == route) return false
        if (!reached) {
            count++
            if (count >= target) reached = true
        }
        return reached
    }

    fun isReached(): Boolean = reached

    fun onAdShown() {
        count = 0
        reached = false
        target = targetPicker()
    }

    fun reset() {
        count = 0
        reached = false
        previous = null
        target = targetPicker()
    }

    fun clearPrevious() { previous = null }

    fun nextTargetForTest(): Int = target
    fun countForTest(): Int = count
}
