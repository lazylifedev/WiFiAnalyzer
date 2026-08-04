package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.lazyapps.wifianalyzer.domain.RegisteredBssid
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.model.*
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.devices.DevicesScreen
import com.lazyapps.wifianalyzer.ui.screens.home.HomeScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompactListCardTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeRegisterAndDetailsActionsRemainIndependentFromCardTap() {
        var selected = 0
        var registered = 0
        val ap = accessPoint()
        rule.setContent {
            WifiAnalyzerTheme {
                HomeScreen(ScanUiState(ScanState.READY, listOf(ap)), {}, {}, {},
                    { selected++ }, { registered++ })
            }
        }
        val id = "12_34_56_78_9A_BC"
        rule.onNodeWithTag("home_access_point_details_$id", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.register_as_device),
        ).assertExists()
        rule.onNodeWithTag("home_register_device_$id", useUnmergedTree = true).performClick()
        assertEquals(1, registered)
        assertEquals(0, selected)
        rule.onNodeWithTag("home_access_point_expand_$id", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_access_point_details_$id", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("home_access_point_expand_$id", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("home_access_point_details_$id", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("home_access_point_${ap.bssid}").performClick()
        assertEquals(1, selected)
    }

    @Test fun savedDeviceDetailsStartCollapsedAndToggle() {
        val device = device()
        rule.setContent {
            WifiAnalyzerTheme {
                DevicesScreen(listOf(device), emptyList(), null, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> })
            }
        }
        rule.onNodeWithTag("saved_device_details_7").assertDoesNotExist()
        rule.onNodeWithTag("saved_device_expand_7", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("saved_device_details_7").assertExists()
        rule.onNodeWithTag("saved_device_expand_7", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("saved_device_details_7").assertDoesNotExist()
    }

    private fun accessPoint() = WifiAccessPoint(
        ssid = "A very long access point name that must ellipsize safely", bssid = "12:34:56:78:9A:BC", rssi = -52,
        frequencyMhz = 2437, channel = 6, channelWidthMhz = 20, capabilities = "[WPA2]",
        timestampMicros = 1, band = WifiBand.BAND_24, signalQuality = SignalQuality.GOOD,
        securityType = SecurityType.WPA2, wifiStandard = WifiStandard.WIFI_4,
        distanceRange = DistanceRange.THREE_TO_EIGHT, observedAtMillis = 1,
    )

    private fun device() = RegisteredDevice(
        id = 7, displayName = "A very long registered device name that must ellipsize safely",
        manufacturer = "Maker", model = "Model", serialNumber = "", ssid = "Long SSID that must ellipsize safely",
        groupId = null, groupName = null, location = "Office", notes = "", createdAt = 1, updatedAt = 1,
        lastSeenAt = null, lastSeenRssi = -48, isEnabled = true,
        bssids = listOf(RegisteredBssid(1, "12:34:56:78:9A:BC", "2.4 GHz", "")),
    )
}
