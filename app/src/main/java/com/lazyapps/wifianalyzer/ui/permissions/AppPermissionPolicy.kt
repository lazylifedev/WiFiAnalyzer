package com.lazyapps.wifianalyzer.ui.permissions

import android.Manifest
import android.os.Build

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

    fun wifiExplanation(apiLevel: Int = Build.VERSION.SDK_INT): String =
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
            "周辺Wi-Fiを取得するため、付近のデバイスと位置情報の権限が必要です。AndroidのWi-Fiスキャン仕様で必要ですが、位置情報そのものは保存・送信しません。"
        } else {
            "周辺Wi-Fiを取得するため、Androidの仕様により位置情報の権限が必要です。位置情報そのものは保存・送信しません。"
        }
}
