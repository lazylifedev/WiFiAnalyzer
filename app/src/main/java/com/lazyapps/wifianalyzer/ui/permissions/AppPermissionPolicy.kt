package com.lazyapps.wifianalyzer.ui.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import com.lazyapps.wifianalyzer.R

enum class PermissionStatus { GRANTED, NOT_GRANTED, PARTIALLY_GRANTED, SETTINGS_REQUIRED }

data class PermissionSummary(
    val wifiScan: PermissionStatus,
    val camera: PermissionStatus,
    val photoPicker: PermissionStatus = PermissionStatus.GRANTED,
)

object AppPermissionPolicy {
    fun wifiScanPermissions(apiLevel: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (apiLevel >= Build.VERSION_CODES.S) add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    @StringRes
    fun wifiExplanationRes(apiLevel: Int = Build.VERSION.SDK_INT): Int =
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
            R.string.wifi_scan_permission_body_android_13
        } else {
            R.string.wifi_scan_permission_body
        }
}
