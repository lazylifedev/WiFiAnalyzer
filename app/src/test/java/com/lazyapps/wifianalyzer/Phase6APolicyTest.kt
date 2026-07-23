package com.lazyapps.wifianalyzer

import android.Manifest
import com.lazyapps.wifianalyzer.ui.onboarding.onboardingPages
import com.lazyapps.wifianalyzer.ui.permissions.AppPermissionPolicy
import com.lazyapps.wifianalyzer.ui.permissions.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6APolicyTest {
    @Test fun android11ScanUsesFineLocation() {
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), AppPermissionPolicy.wifiScanPermissions(30))
    }

    @Test fun android12ScanUsesCoarseAndFineLocation() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            AppPermissionPolicy.wifiScanPermissions(31),
        )
    }

    @Test fun android13ScanAlsoUsesNearbyWifiDevices() {
        val permissions = AppPermissionPolicy.wifiScanPermissions(33)
        assertTrue(Manifest.permission.NEARBY_WIFI_DEVICES in permissions)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
    }

    @Test fun onboardingIsShortAndEndsWithPrivacyExplanation() {
        assertEquals(4, onboardingPages.size)
        assertTrue(onboardingPages.all { it.title.isNotBlank() && it.body.length < 100 })
        assertTrue(onboardingPages.last().body.contains("端末内"))
    }

    @Test fun permissionStatusesCoverSettingsAndPartialStates() {
        assertTrue(PermissionStatus.entries.contains(PermissionStatus.SETTINGS_REQUIRED))
        assertTrue(PermissionStatus.entries.contains(PermissionStatus.PARTIALLY_GRANTED))
        assertFalse(PermissionStatus.entries.isEmpty())
    }
}
