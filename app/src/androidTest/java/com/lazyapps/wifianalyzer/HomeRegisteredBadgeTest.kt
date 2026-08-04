package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.lazyapps.wifianalyzer.model.*
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.screens.home.HomeScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.test.platform.app.InstrumentationRegistry
import android.content.res.Configuration
import java.util.Locale

class HomeRegisteredBadgeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun registeredIconUsesHeaderSlotAndIsIncludedInCardSemantics() {
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
        assert(badge.top < ssid.bottom && badge.bottom > ssid.top)
        assert(ssid.right <= badge.left)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithTag("home_registered_$bssid", useUnmergedTree = true)
            .assertContentDescriptionEquals(context.getString(R.string.registered))
    }

    @Test fun registeredAndUnregisteredCardsShareHeightAndRegistrationSlotGeometry() {
        val unregistered = accessPoint("12:34:56:78:9A:01", false)
        val registered = accessPoint("12:34:56:78:9A:02", true)
        rule.setContent {
            WifiAnalyzerTheme {
                HomeScreen(ScanUiState(scanState = ScanState.READY, accessPoints = listOf(unregistered, registered)), {}, {}, {}, {}, {})
            }
        }
        val firstId = "12_34_56_78_9A_01"
        val secondId = "12_34_56_78_9A_02"
        val firstCard = rule.onNodeWithTag("home_access_point_card_$firstId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val secondCard = rule.onNodeWithTag("home_access_point_card_$secondId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val add = rule.onNodeWithTag("home_unregistered_icon_$firstId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val check = rule.onNodeWithTag("home_registered_${registered.bssid}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithTag("home_register_device_$firstId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertEquals(firstCard.height, secondCard.height, 0.5f)
        assertEquals(add.center.x, check.center.x, 0.5f)
        assertEquals(add.width, check.width, 0.5f)
        assert(button.width >= 48f * rule.density.density)
        assert(button.height >= 48f * rule.density.density)
    }

    @Test fun longSsidKeepsRegistrationAndRssiVisibleInEnglish() = assertLongSsidLayout(Locale.ENGLISH)

    @Test fun longSsidKeepsRegistrationAndRssiVisibleInJapanese() = assertLongSsidLayout(Locale.JAPANESE)

    private fun assertLongSsidLayout(locale: Locale) {
        val accessPoint = accessPoint("12:34:56:78:9A:03", false)
        val stableId = "12_34_56_78_9A_03"
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration).apply { setLocale(locale) }
        val localizedContext = targetContext.createConfigurationContext(configuration)
        rule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                WifiAnalyzerTheme {
                    HomeScreen(ScanUiState(scanState = ScanState.READY, accessPoints = listOf(accessPoint)), {}, {}, {}, {}, {})
                }
            }
        }
        val card = rule.onNodeWithTag("home_access_point_card_$stableId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val registration = rule.onNodeWithTag("home_register_device_$stableId", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val rssi = rule.onNodeWithTag("home_rssi_$stableId", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val ssid = rule.onNodeWithTag("home_ssid_${accessPoint.bssid}", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val bssid = rule.onNodeWithTag("home_bssid_$stableId", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val quality = rule.onNodeWithTag("home_signal_quality_$stableId", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assert(registration.left >= card.left && registration.right <= card.right)
        assert(rssi.left >= card.left && rssi.right <= card.right)
        assert(ssid.right <= registration.left)
        assert(rssi.top >= ssid.bottom)
        assert(rssi.top < bssid.bottom && rssi.bottom > bssid.top)
        assertEquals(rssi.right, quality.right, 0.5f)
    }

    private fun accessPoint(bssid: String, registered: Boolean) = WifiAccessPoint(
        ssid = "A very long SSID that must ellipsize without moving actions or RSSI off screen",
        bssid = bssid,
        rssi = -51,
        frequencyMhz = 2437,
        channel = 6,
        channelWidthMhz = 20,
        capabilities = "[WPA2]",
        timestampMicros = 1,
        band = WifiBand.BAND_24,
        signalQuality = SignalQuality.GOOD,
        securityType = SecurityType.WPA2,
        wifiStandard = WifiStandard.WIFI_4,
        distanceRange = DistanceRange.ONE_TO_THREE,
        observedAtMillis = 1,
        isRegistered = registered,
    )
}
