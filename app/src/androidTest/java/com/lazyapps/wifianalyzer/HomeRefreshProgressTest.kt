package com.lazyapps.wifianalyzer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.ui.components.HomeScanProgressFraction
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.home.HomeScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeRefreshProgressTest {
    @get:Rule val rule = createComposeRule()

    @Test fun scanStateDoesNotMoveAccessPointSectionAndUsesOnlyLinearProgress() {
        val state = mutableStateOf(waitingState())
        rule.setContent { WifiAnalyzerTheme { HomeScreen(state.value, {}, {}, {}, {}, {}) } }
        val waitingTop = rule.onNodeWithTag("home_access_point_section").fetchSemanticsNode().boundsInRoot.top

        rule.runOnIdle { state.value = state.value.copy(scanState = ScanState.SCANNING, isRefreshing = true) }
        val scanningTop = rule.onNodeWithTag("home_access_point_section").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("home_scan_progress").assertContentDescriptionEquals(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.home_refresh_progress_refreshing),
        )
        rule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.scan_in_progress),
        ).assertDoesNotExist()

        rule.runOnIdle { state.value = waitingState().copy(refreshCycleStartedElapsedMillis = 20_000L) }
        val completedTop = rule.onNodeWithTag("home_access_point_section").fetchSemanticsNode().boundsInRoot.top
        assertEquals(waitingTop, scanningTop, rule.density.density)
        assertEquals(waitingTop, completedTop, rule.density.density)
    }

    @Test fun determinateProgressAnimatesSmoothlyAndRecompositionDoesNotResetIt() {
        val state = mutableStateOf(waitingState().copy(isRefreshing = true))
        rule.setContent {
            WifiAnalyzerTheme {
                com.lazyapps.wifianalyzer.ui.components.RefreshProgress(state.value, elapsedRealtime = { 10_000L })
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { state.value = waitingState() }
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithTag("home_scan_progress").assert(progressNear(0.25f))
        rule.runOnIdle { state.value = state.value.copy(lastUpdatedMillis = 123L) }
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithTag("home_scan_progress").assert(progressNear(0.5f))
    }

    private fun waitingState() = ScanUiState(
        scanState = ScanState.READY,
        refreshCycleStartedElapsedMillis = 10_000L,
        refreshIntervalMillis = 20_000L,
    )

    private fun progressNear(expected: Float) = SemanticsMatcher("progress near $expected") { node ->
        val actual = if (node.config.contains(HomeScanProgressFraction)) node.config[HomeScanProgressFraction]
            else return@SemanticsMatcher false
        actual in (expected - 0.03f)..(expected + 0.03f)
    }
}
