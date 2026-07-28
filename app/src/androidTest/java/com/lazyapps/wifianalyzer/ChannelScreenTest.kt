package com.lazyapps.wifianalyzer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lazyapps.wifianalyzer.data.ChannelDisplayMode
import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiStandard
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.channel.ChannelScreen
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.AccentColor
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Rule
import org.junit.Test

class ChannelScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun graphAndOccupancySwitchAndSelectedDetailsAreVisible() {
        val accessPoint = accessPoint()
        var mode by mutableStateOf(ChannelDisplayMode.GRAPH)
        composeRule.setContent {
            WifiAnalyzerTheme(mode = ThemeMode.DARK) {
                ChannelScreen(
                    state = ScanUiState(
                        scanState = ScanState.READY,
                        accessPoints = listOf(accessPoint),
                        selectedBssid = accessPoint.bssid,
                        channelDisplayMode = mode,
                    ),
                    onRefresh = {},
                    onRequestPermission = {},
                    onOpenSettings = {},
                    onSelectAccessPoint = {},
                    onClearAccessPointSelection = {},
                    onOpenAccessPoint = {},
                    onRegisterAccessPoint = {},
                    onDisplayModeChange = { mode = it },
                )
            }
        }

        composeRule.onNodeWithTag("channel_graph").assertIsDisplayed()
        composeRule.onNodeWithTag("channel_selected_ap").assertIsDisplayed()
        composeRule.onNodeWithText("空いている候補").assertIsDisplayed()
        composeRule.onNodeWithTag("channel_mode_occupancy").performClick()
        composeRule.onNodeWithText("1ネットワーク").assertIsDisplayed()
    }

    @Test
    fun graphRendersWithFiftyNetworksAcrossLightDarkAndSevenAccents() {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var accent by mutableStateOf(AccentColor.BLUE)
        val accessPoints = List(50) { index ->
            accessPoint().copy(
                bssid = "02:00:00:00:${(index / 100).toString().padStart(2, '0')}:${(index % 100).toString().padStart(2, '0')}",
                ssid = "Load AP $index",
                rssi = -35 - index.coerceAtMost(60),
                frequencyMhz = 2412 + (index % 11) * 5,
                channel = 1 + index % 11,
                channelWidthMhz = listOf(20, 40, 80, 160)[index % 4],
                isRegistered = index % 9 == 0,
                registeredDeviceName = if (index % 9 == 0) "登録AP $index" else null,
            )
        }
        composeRule.setContent {
            WifiAnalyzerTheme(mode = theme, accent = accent) {
                ChannelScreen(
                    state = ScanUiState(scanState = ScanState.READY, accessPoints = accessPoints),
                    onRefresh = {},
                    onRequestPermission = {},
                    onOpenSettings = {},
                    onSelectAccessPoint = {},
                    onClearAccessPointSelection = {},
                    onOpenAccessPoint = {},
                    onRegisterAccessPoint = {},
                    onDisplayModeChange = {},
                )
            }
        }
        ThemeMode.entries.filter { it != ThemeMode.SYSTEM }.forEach { targetTheme ->
            AccentColor.entries.forEach { targetAccent ->
                composeRule.runOnIdle {
                    theme = targetTheme
                    accent = targetAccent
                }
                composeRule.onNodeWithTag("channel_graph").assertIsDisplayed()
            }
        }
    }

    private fun accessPoint() = WifiAccessPoint(
        ssid = "Test AP",
        bssid = "00:11:22:33:44:55",
        rssi = -52,
        frequencyMhz = 2437,
        channel = 6,
        channelWidthMhz = 40,
        capabilities = "[WPA2]",
        timestampMicros = 1,
        band = WifiBand.BAND_24,
        signalQuality = WifiAnalysis.signalQuality(-52),
        securityType = SecurityType.WPA2,
        wifiStandard = WifiStandard.WIFI_6,
        distanceRange = DistanceRange.ONE_TO_THREE,
        observedAtMillis = System.currentTimeMillis(),
        isRegistered = true,
        registeredDeviceName = "会議室AP",
        registeredGroupName = "オフィス",
    )
}
