package com.lazyapps.wifianalyzer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.domain.RegisteredBssid
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiStandard
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.devices.DevicesScreen
import com.lazyapps.wifianalyzer.ui.screens.home.HomeScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Rule
import org.junit.Test

class InlineNativeAdScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeCanRenderLoadedAd() {
        rule.setContent {
            WifiAnalyzerTheme {
                HomeScreen(
                    state = ScanUiState(ScanState.READY, List(5, ::accessPoint)),
                    onRefresh = {}, onRequestPermission = {}, onOpenSettings = {},
                    onSelectAccessPoint = {}, onRegisterAccessPoint = {},
                    showInlineNativeAd = true,
                    inlineAdContent = { Box(it.fillMaxWidth().height(72.dp).testTag("home_inline_native_ad")) },
                )
            }
        }
        rule.onNodeWithTag("home_access_point_list").performScrollToNode(hasTestTag("home_inline_native_ad"))
        rule.onNodeWithTag("home_inline_native_ad").assertIsDisplayed()
    }

    @Test fun homeWithZeroItemsHasNoAd() {
        rule.setContent {
            WifiAnalyzerTheme {
                HomeScreen(ScanUiState(ScanState.READY, emptyList()), {}, {}, {}, {}, {}, showInlineNativeAd = true,
                    inlineAdContent = { Box(it.testTag("home_inline_native_ad")) })
            }
        }
        rule.onNodeWithTag("home_inline_native_ad").assertDoesNotExist()
    }

    @Test fun devicesCanRenderLoadedAd() {
        val devices = List(4, ::device)
        rule.setContent {
            WifiAnalyzerTheme {
                DevicesScreen(devices = devices, groups = emptyList(), errorMessage = null,
                    onAddDevice = {}, onScanLabel = {}, onOpenDevice = {}, onDeleteDevice = {},
                    onCreateGroup = {}, onRenameGroup = { _, _ -> }, onDeleteGroup = {}, onMoveGroup = { _, _ -> },
                    showInlineNativeAd = true,
                    inlineAdContent = { Box(it.fillMaxWidth().height(72.dp).testTag("devices_inline_native_ad")) })
            }
        }
        rule.onNodeWithTag("devices_screen").performScrollToNode(hasTestTag("devices_inline_native_ad"))
        rule.onNodeWithTag("devices_inline_native_ad").assertExists()
    }

    @Test fun devicesProGateOmitsSlot() {
        val devices = List(4, ::device)
        rule.setContent {
            WifiAnalyzerTheme {
                DevicesScreen(devices = devices, groups = emptyList(), errorMessage = null,
                    onAddDevice = {}, onScanLabel = {}, onOpenDevice = {}, onDeleteDevice = {},
                    onCreateGroup = {}, onRenameGroup = { _, _ -> }, onDeleteGroup = {}, onMoveGroup = { _, _ -> },
                    showInlineNativeAd = false,
                    inlineAdContent = { Box(it.testTag("devices_inline_native_ad")) })
            }
        }
        rule.onNodeWithTag("devices_inline_native_ad").assertDoesNotExist()
    }

    private fun accessPoint(index: Int) = WifiAccessPoint(
        ssid = "AP $index", bssid = "00:11:22:33:44:${index.toString().padStart(2, '0')}", rssi = -50,
        frequencyMhz = 2437, channel = 6, channelWidthMhz = 20, capabilities = "[WPA2]",
        timestampMicros = 1, band = WifiBand.BAND_24, signalQuality = SignalQuality.GOOD,
        securityType = SecurityType.WPA2, wifiStandard = WifiStandard.WIFI_4,
        distanceRange = DistanceRange.ONE_TO_THREE, observedAtMillis = 1,
    )

    private fun device(index: Int) = RegisteredDevice(
        id = index.toLong(), displayName = "Device $index", manufacturer = "", model = "", serialNumber = "", ssid = "",
        groupId = null, groupName = null, location = "", notes = "", createdAt = index.toLong(), updatedAt = 1,
        lastSeenAt = null, lastSeenRssi = null, isEnabled = true,
        bssids = listOf(RegisteredBssid(index.toLong(), "00:11:22:33:55:${index.toString().padStart(2, '0')}", "2.4 GHz", "")),
    )
}
