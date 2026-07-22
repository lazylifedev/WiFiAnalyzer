package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.domain.BssidFormat
import com.lazyapps.wifianalyzer.domain.DetectionPolicy
import com.lazyapps.wifianalyzer.domain.DeviceMatching
import com.lazyapps.wifianalyzer.domain.GroupNameFormat
import com.lazyapps.wifianalyzer.domain.RegisteredBssid
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRegistryTest {
    @Test fun bssidNormalizationAcceptsSupportedInputForms() {
        assertEquals("AA:BB:CC:DD:EE:FF", BssidFormat.normalize("aa-bb-cc-dd-ee-ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", BssidFormat.normalize("aabbccddeeff"))
        assertEquals("AA:BB:CC:DD:EE:FF", BssidFormat.normalize("AA:BB:CC:DD:EE:FF"))
    }

    @Test fun bssidValidationRejectsMalformedValues() {
        assertFalse(BssidFormat.isValid("AA:BB:CC"))
        assertFalse(BssidFormat.isValid("GG:BB:CC:DD:EE:FF"))
        assertTrue(BssidFormat.isValid("01-23-45-67-89-ab"))
    }

    @Test fun duplicateBssidComparisonUsesNormalizedValue() {
        assertTrue(BssidFormat.hasDuplicates(listOf("aa-bb-cc-dd-ee-ff", "AABBCCDDEEFF")))
        assertFalse(BssidFormat.hasDuplicates(listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02")))
    }

    @Test fun registeredMatchingIgnoresInputFormatting() {
        val index = DeviceMatching.index(listOf(device()))
        val match = DeviceMatching.match(accessPoint("aa-bb-cc-dd-ee-ff"), index)
        assertNotNull(match)
        assertEquals("テスト機器", match?.deviceName)
    }

    @Test fun groupNormalizationAndDuplicateComparisonHandleWidthCaseAndSpaces() {
        assertEquals("office ap", GroupNameFormat.normalize("  ＯＦＦＩＣＥ AP "))
        assertTrue(GroupNameFormat.isDuplicate(" office ap ", listOf("Ｏｆｆｉｃｅ ＡＰ")))
    }

    @Test fun detectionAndLastSeenThrottlingFollowPolicy() {
        val now = 1_000_000L
        assertTrue(DetectionPolicy.isDetected(now - 10_000, now))
        assertFalse(DetectionPolicy.isDetected(now - 46_000, now))
        assertFalse(DetectionPolicy.shouldUpdate(now - 10_000, -60, now, -63))
        assertTrue(DetectionPolicy.shouldUpdate(now - 46_000, -60, now, -61))
        assertTrue(DetectionPolicy.shouldUpdate(now - 10_000, -60, now, -66))
        assertTrue(DetectionPolicy.shouldUpdate(now - 61_000, -60, now, -61))
    }

    private fun device() = RegisteredDevice(
        id = 1, displayName = "テスト機器", manufacturer = "", model = "", serialNumber = "", ssid = "Test",
        groupId = null, groupName = null, location = "", notes = "", createdAt = 1, updatedAt = 1,
        lastSeenAt = null, lastSeenRssi = null, isEnabled = true,
        bssids = listOf(RegisteredBssid(1, "AA:BB:CC:DD:EE:FF", "5 GHz", "")),
    )

    private fun accessPoint(bssid: String) = WifiAccessPoint(
        ssid = "Test", bssid = bssid, rssi = -55, frequencyMhz = 5180, channel = 36, channelWidthMhz = 80,
        capabilities = "[WPA2]", timestampMicros = 1, band = WifiBand.BAND_5, signalQuality = SignalQuality.EXCELLENT,
        securityType = SecurityType.WPA2, wifiStandard = WifiStandard.WIFI_5, distanceRange = DistanceRange.ONE_TO_THREE,
        observedAtMillis = 1,
    )
}
