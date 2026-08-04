package com.lazyapps.wifianalyzer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun SignalQualityWifiIcon(quality: SignalQuality, tint: Color, modifier: Modifier = Modifier) {
    val activeBars = when (quality.wifiIconLevel()) {
        WifiIconLevel.THREE -> 3
        WifiIconLevel.TWO -> 2
        WifiIconLevel.ONE -> 1
        WifiIconLevel.ZERO -> 0
    }
    val inactive = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        val centerX = size.width / 2f
        val centerY = size.height * 0.72f
        listOf(4.0f, 6.7f, 9.4f).forEachIndexed { index, radiusDp ->
            val radius = radiusDp.dp.toPx()
            drawArc(
                color = if (index < activeBars) tint else inactive,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = stroke,
            )
        }
        drawCircle(if (activeBars > 0) tint else inactive, radius = 1.2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(centerX, centerY))
    }
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
