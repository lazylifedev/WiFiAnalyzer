package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import com.lazyapps.wifianalyzer.model.*
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.home.HomeScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

class HomeRegisteredBadgeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun registeredBadgeIsBelowSingleLineSsidAndIncludedInCardSemantics() {
        val bssid = "12:34:56:78:9A:BC"
        val accessPoint = WifiAccessPoint(
            ssid = "DIRECT-E3-EPSON-VERY-LONG-SSID-THAT-MUST-ELLIPSIZE", bssid = bssid, rssi = -39,
            frequencyMhz = 2437, channel = 6, channelWidthMhz = 20, capabilities = "[WPA2]",
            timestampMicros = 1, band = WifiBand.BAND_24, signalQuality = SignalQuality.EXCELLENT,
            securityType = SecurityType.WPA2, wifiStandard = WifiStandard.WIFI_4,
            distanceRange = DistanceRange.ONE_TO_THREE, observedAtMillis = 1,
            isRegistered = true, registeredDeviceName = "DIRECT-E3-EPSON", registeredGroupName = "東大阪本社",
        )
        rule.setContent {
            WifiAnalyzerTheme {
                HomeScreen(ScanUiState(scanState = ScanState.READY, accessPoints = listOf(accessPoint)), {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithTag("home_access_point_list").performScrollToNode(hasTestTag("home_access_point_$bssid"))
        val ssid = rule.onNodeWithTag("home_ssid_$bssid", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val badge = rule.onNodeWithTag("home_registered_$bssid", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assert(badge.top >= ssid.bottom)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithTag("home_access_point_$bssid").assertContentDescriptionEquals(
            context.getString(R.string.registered_description_prefix) + context.getString(R.string.access_point_description, "", accessPoint.ssid, -39),
        )
    }
}
