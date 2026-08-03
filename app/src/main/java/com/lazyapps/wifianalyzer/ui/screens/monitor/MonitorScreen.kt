package com.lazyapps.wifianalyzer.ui.screens.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.domain.SignalHistoryPolicy
import com.lazyapps.wifianalyzer.model.SignalSample
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.ui.components.ScanStatusCard
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.components.RefreshProgress
import com.lazyapps.wifianalyzer.ui.components.localizedLabel
import com.lazyapps.wifianalyzer.ui.components.localizedSsid
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import kotlin.math.abs

@Composable
fun MonitorScreen(
    state: ScanUiState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: (ScanState) -> Unit,
    onHistoryRangeChange: (Long) -> Unit = {},
) {
    val accessPoint = state.selectedAccessPoint
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("monitor_screen"),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.screen_monitor),
                accessPoint?.let { "${it.ssid} · ${it.bssid}" } ?: state.selectedBssid ?: stringResource(R.string.monitor_target),
            ) {
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_scan)) }
            }
        }
        item { RefreshProgress(state) }
        item {
            ScanStatusCard(
                state.scanState, state.accessPoints.isNotEmpty(), onRequestPermission, onOpenSettings, onRefresh,
                Modifier.padding(horizontal = AppSpacing.large),
            )
        }
        if (accessPoint == null && state.selectedBssid == null) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large)) {
                    Text(stringResource(R.string.monitor_no_target), Modifier.padding(AppSpacing.large))
                }
            }
        } else if (accessPoint == null) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(AppSpacing.medium)) {
                        Text(stringResource(R.string.monitor_not_detected), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.monitor_not_detected_detail), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            if (!state.selectedDetected) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(AppSpacing.medium)) {
                            Text(stringResource(R.string.monitor_not_detected), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.monitor_not_detected_detail), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { SignalSummary(accessPoint, state.selectedDetected, state.signalHistory, state.distanceUnit == DistanceUnitPreference.FEET, Modifier.padding(horizontal = AppSpacing.large)) }
            item { SignalChart(state.signalHistory, state.signalHistoryRangeMillis, onHistoryRangeChange, Modifier.padding(horizontal = AppSpacing.large)) }
        }
    }
}

@Composable
private fun SignalSummary(accessPoint: WifiAccessPoint, detected: Boolean, history: List<SignalSample>, feet: Boolean, modifier: Modifier = Modifier) {
    val stability = stabilityLabel(history)
    val progress = ((accessPoint.rssi + 100) / 55f).coerceIn(0f, 1f)
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.large), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Text(accessPoint.ssid.localizedSsid(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text(accessPoint.bssid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (detected) stringResource(R.string.monitor_status) else stringResource(R.string.monitor_not_detected), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                CircularProgressIndicator(
                    progress = { if (detected) progress else 0f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 9.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (detected) accessPoint.rssi.toString() else "—", fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
                    Text("dBm", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (detected) accessPoint.signalQuality.localizedLabel() else stringResource(R.string.monitor_not_detected), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                MetricCard(Icons.AutoMirrored.Rounded.ShowChart, stringResource(R.string.stability), stability, Modifier.weight(1f))
                MetricCard(Icons.Rounded.LocationOn, stringResource(R.string.monitor_distance), if (detected) stringResource(R.string.estimated_prefix, accessPoint.distanceRange.localizedLabel(feet)) else stringResource(R.string.not_available), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun stabilityLabel(history: List<SignalSample>): String {
    if (history.size < 2) return stringResource(R.string.measuring)
    val spread = history.maxOf { it.rssi } - history.minOf { it.rssi }
    return when {
        spread <= 4 -> stringResource(R.string.stable)
        spread <= 8 -> stringResource(R.string.slightly_variable)
        else -> stringResource(R.string.unstable)
    }
}

@Composable
private fun MetricCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private enum class HistoryRange(val labelRes: Int, val millis: Long) {
    THIRTY_SECONDS(R.string.duration_30_seconds, 30_000L), ONE_MINUTE(R.string.duration_1_minute, 60_000L), FIVE_MINUTES(R.string.duration_5_minutes, 300_000L), FIFTEEN_MINUTES(R.string.duration_15_minutes, 900_000L)
}

@Composable
internal fun SignalChart(history: List<SignalSample>, selectedRangeMillis: Long = 30_000L, onRangeChange: (Long) -> Unit = {}, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val range = HistoryRange.entries.firstOrNull { it.millis == selectedRangeMillis } ?: HistoryRange.THIRTY_SECONDS
    var zoom by remember { mutableFloatStateOf(1f) }
    var panFraction by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<SignalSample?>(null) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 8f)
        panFraction = (panFraction + panChange.x / 1600f).coerceIn(0f, 1f - 1f / zoom)
    }
    val latest = history.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
    val rangeStart = latest - range.millis
    val rangeSamples = history.filter { it.timestampMillis in rangeStart..latest }
    val visibleDuration = (range.millis / zoom).toLong()
    val visibleEnd = latest - (range.millis * panFraction).toLong()
    val visibleStart = visibleEnd - visibleDuration
    val visible = rangeSamples.filter { it.timestampMillis in visibleStart..visibleEnd }
    val yAxis = SignalHistoryPolicy.adaptiveYAxis(visible)
    val max = rangeSamples.maxOfOrNull { it.rssi }
    val min = rangeSamples.minOfOrNull { it.rssi }
    val average = rangeSamples.takeIf { it.isNotEmpty() }?.map { it.rssi }?.average()
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.signal_history), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.dbm_axis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
            ) {
                HistoryRange.entries.forEach { item ->
                    val fullLabel = stringResource(item.labelRes)
                    FilterChip(
                        selected = range == item,
                        onClick = { onRangeChange(item.millis); zoom = 1f; panFraction = 0f; selected = null },
                        label = { Text(fullLabel, maxLines = 1) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = fullLabel },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.signal_maximum, max?.let { "$it dBm" } ?: stringResource(R.string.not_available)), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.signal_average, average?.let { "%.1f dBm".format(it) } ?: stringResource(R.string.not_available)), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.signal_minimum, min?.let { "$it dBm" } ?: stringResource(R.string.not_available)), style = MaterialTheme.typography.labelMedium)
            }
            selected?.let { Text("${android.text.format.DateFormat.format("HH:mm:ss", it.timestampMillis)}  ${it.rssi} dBm", color = MaterialTheme.colorScheme.primary) }
            val selectedDescription = selected?.let { stringResource(R.string.signal_chart_selected_description, it.rssi, android.text.format.DateFormat.format("HH:mm:ss", it.timestampMillis)) }
                ?: stringResource(R.string.signal_chart_description)
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.9f)
                    .semantics { contentDescription = selectedDescription }
                    .transformable(transformState)
                    .pointerInput(visible, visibleStart, visibleDuration) {
                        detectTapGestures(
                            onDoubleTap = { zoom = 1f; panFraction = 0f; selected = null },
                            onTap = { tap ->
                                val axisWidth = 42.dp.toPx()
                                val plotWidth = (size.width - axisWidth - 8.dp.toPx()).coerceAtLeast(1f)
                                val target = visibleStart + (((tap.x - axisWidth) / plotWidth).coerceIn(0f, 1f) * visibleDuration).toLong()
                                selected = visible.minByOrNull { abs(it.timestampMillis - target) }
                            },
                        )
                    }
            ) {
                val labelPaint = android.graphics.Paint().apply { color = labelColor.toArgb(); textSize = 11.sp.toPx() }
                val leftAxisWidth = labelPaint.measureText("-100") + 12.dp.toPx()
                val rightPadding = 8.dp.toPx()
                val plotLeft = leftAxisWidth
                val plotWidth = (size.width - plotLeft - rightPadding).coerceAtLeast(1f)
                val plotRight = plotLeft + plotWidth
                val tickStep = if (yAxis.span <= 30) 5 else 10
                (yAxis.lower..yAxis.upper step tickStep).forEach { dbm ->
                    val y = size.height * ((yAxis.upper - dbm) / yAxis.span.toFloat())
                    drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText("$dbm", 2.dp.toPx(), (y - 2.dp.toPx()).coerceAtLeast(12.dp.toPx()), labelPaint)
                }
                repeat(4) { index ->
                    val y = size.height * index / 3f
                    drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
                }
                repeat(6) { index ->
                    val x = plotLeft + plotWidth * index / 5f
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
                SignalHistoryPolicy.segments(visible).forEach { segment ->
                    val path = Path()
                    segment.forEachIndexed { index, sample ->
                        val x = plotLeft + plotWidth * ((sample.timestampMillis - visibleStart).coerceIn(0, visibleDuration) / visibleDuration.toFloat())
                        val y = size.height * ((yAxis.upper - sample.rssi.coerceIn(yAxis.lower, yAxis.upper)) / yAxis.span.toFloat())
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        if (segment.size == 1) drawCircle(lineColor, 4.dp.toPx(), Offset(x, y))
                    }
                    if (segment.size > 1) drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
                }
                selected?.takeIf { it in visible }?.let { sample ->
                    val x = plotLeft + plotWidth * ((sample.timestampMillis - visibleStart).coerceIn(0, visibleDuration) / visibleDuration.toFloat())
                    val y = size.height * ((yAxis.upper - sample.rssi.coerceIn(yAxis.lower, yAxis.upper)) / yAxis.span.toFloat())
                    drawLine(lineColor.copy(alpha = .5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
                    drawCircle(lineColor, 4.dp.toPx(), Offset(x, y))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.seconds_ago_format, visibleDuration / 1000), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.time_axis), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.now), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07111F)
@Composable
private fun MonitorPreview() = WifiAnalyzerTheme(mode = ThemeMode.DARK) {
    MonitorScreen(ScanUiState(scanState = ScanState.READY), {}, {}, {})
}
