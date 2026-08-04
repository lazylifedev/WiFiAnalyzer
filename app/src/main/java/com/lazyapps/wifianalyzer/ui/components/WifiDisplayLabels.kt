package com.lazyapps.wifianalyzer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Wifi1Bar
import androidx.compose.material.icons.rounded.Wifi2Bar
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.model.WifiStandard
import com.lazyapps.wifianalyzer.domain.WifiAnalysis

internal enum class WifiIconLevel { ZERO, ONE, TWO, THREE }

internal fun SignalQuality.wifiIconLevel(): WifiIconLevel = when (this) {
    SignalQuality.EXCELLENT -> WifiIconLevel.THREE
    SignalQuality.GOOD -> WifiIconLevel.TWO
    SignalQuality.FAIR -> WifiIconLevel.ONE
    SignalQuality.WEAK -> WifiIconLevel.ZERO
}

private val Wifi0Bar: ImageVector = ImageVector.Builder(
    name = "Wifi0Bar",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(fill = SolidColor(Color.Black)) {
    moveTo(8f, 17f)
    curveTo(8f, 15.9f, 8.9f, 15f, 10f, 15f)
    horizontalLineTo(14f)
    curveTo(15.1f, 15f, 16f, 15.9f, 16f, 17f)
    curveTo(16f, 18.1f, 15.1f, 19f, 14f, 19f)
    horizontalLineTo(10f)
    curveTo(8.9f, 19f, 8f, 18.1f, 8f, 17f)
    close()
}.build()

@Composable
fun SignalQualityWifiIcon(quality: SignalQuality, tint: Color, modifier: Modifier = Modifier) {
    val image = when (quality.wifiIconLevel()) {
        WifiIconLevel.THREE -> Icons.Rounded.Wifi
        WifiIconLevel.TWO -> Icons.Rounded.Wifi2Bar
        WifiIconLevel.ONE -> Icons.Rounded.Wifi1Bar
        WifiIconLevel.ZERO -> Wifi0Bar
    }
    Icon(image, contentDescription = null, tint = tint, modifier = modifier.size(18.dp))
}

@Composable
fun SignalQuality.localizedLabel(): String = stringResource(when (this) {
    SignalQuality.EXCELLENT -> R.string.signal_excellent
    SignalQuality.GOOD -> R.string.signal_good
    SignalQuality.FAIR -> R.string.signal_fair
    SignalQuality.WEAK -> R.string.signal_weak
})

@Composable
fun SecurityType.localizedLabel(): String = stringResource(when (this) {
    SecurityType.OPEN -> R.string.security_open
    SecurityType.WEP -> R.string.security_wep
    SecurityType.WPA -> R.string.security_wpa
    SecurityType.WPA2 -> R.string.security_wpa2
    SecurityType.WPA3 -> R.string.security_wpa3
    SecurityType.OWE -> R.string.security_owe
    SecurityType.ENTERPRISE -> R.string.security_enterprise
    SecurityType.UNKNOWN -> R.string.unknown
})

@Composable
fun DistanceRange.localizedLabel(feet: Boolean): String = stringResource(when (this) {
    DistanceRange.ONE_TO_THREE -> if (feet) R.string.distance_3_10_ft else R.string.distance_1_3_m
    DistanceRange.THREE_TO_EIGHT -> if (feet) R.string.distance_10_26_ft else R.string.distance_3_8_m
    DistanceRange.EIGHT_TO_TWENTY -> if (feet) R.string.distance_26_66_ft else R.string.distance_8_20_m
    DistanceRange.TWENTY_PLUS -> if (feet) R.string.distance_66_plus_ft else R.string.distance_20_plus_m
})

@Composable fun String.localizedSsid(): String = if (this == WifiAnalysis.HIDDEN_SSID) stringResource(R.string.hidden_network) else this

@Composable fun WifiStandard.localizedLabel(): String = if (this == WifiStandard.UNKNOWN) stringResource(R.string.unknown) else label
