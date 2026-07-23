package com.lazyapps.wifianalyzer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import com.lazyapps.wifianalyzer.R

sealed class AppDestination(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Home : AppDestination("home", R.string.nav_home, Icons.Rounded.Home)
    data object Channel : AppDestination("channel", R.string.nav_channel, Icons.AutoMirrored.Rounded.ShowChart)
    data object Monitor : AppDestination("monitor", R.string.nav_monitor, Icons.Rounded.Speed)
    data object Devices : AppDestination("devices", R.string.nav_devices, Icons.Rounded.Devices)
    data object Settings : AppDestination("settings", R.string.nav_settings, Icons.Rounded.Settings)

    companion object { val bottomItems = listOf(Home, Channel, Monitor, Devices, Settings) }
}

const val REGISTRATION_ROUTE = "device-registration"
const val OCR_REGISTRATION_ROUTE = "ocr-registration"
const val BACKUP_ROUTE = "backup-restore"
const val EXPORT_ROUTE = "data-export"
const val IMPORT_ROUTE = "csv-import"
const val DEVICE_DETAIL_ROUTE = "device-detail/{deviceId}"
fun deviceDetailRoute(deviceId: Long) = "device-detail/$deviceId"
