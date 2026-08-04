package com.lazyapps.wifianalyzer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.channel.ChannelScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.DevicesScreen
import com.lazyapps.wifianalyzer.ui.screens.monitor.MonitorScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Rule
import org.junit.Test

class ScanProgressScreensTest {
    @get:Rule val rule = createComposeRule()

    @Test fun channelUsesFixedTaggedScanProgressWithoutScanningText() {
        val state = mutableStateOf(ScanUiState(scanState = ScanState.READY, isRefreshing = false))
        rule.setContent { WifiAnalyzerTheme { ChannelScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("channel_scan_progress").assertIsDisplayed()
        val waitingTop = rule.onNodeWithTag("channel_list").fetchSemanticsNode().boundsInRoot.top
        rule.runOnIdle { state.value = state.value.copy(scanState = ScanState.SCANNING, isRefreshing = true) }
        rule.onNodeWithTag("channel_scan_progress").assertContentDescriptionEquals(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.home_refresh_progress_refreshing),
        )
        val scanningTop = rule.onNodeWithTag("channel_list").fetchSemanticsNode().boundsInRoot.top
        assert(waitingTop == scanningTop)
    }

    @Test fun monitorUsesFixedTaggedScanProgress() {
        rule.setContent { WifiAnalyzerTheme { MonitorScreen(ScanUiState(scanState = ScanState.READY), {}, {}, {}) } }
        rule.onNodeWithTag("monitor_scan_progress").assertIsDisplayed()
    }

    @Test fun savedDevicesUsesSharedScanStateAndFixedTaggedProgress() {
        rule.setContent {
            WifiAnalyzerTheme {
                DevicesScreen(
                    devices = emptyList(), groups = emptyList(), errorMessage = null,
                    onAddDevice = {}, onScanLabel = {}, onOpenDevice = {}, onDeleteDevice = {},
                    onCreateGroup = {}, onRenameGroup = { _, _ -> }, onDeleteGroup = {}, onMoveGroup = { _, _ -> },
                    scanState = ScanUiState(scanState = ScanState.READY),
                )
            }
        }
        rule.onNodeWithTag("devices_scan_progress").assertIsDisplayed()
    }
}
