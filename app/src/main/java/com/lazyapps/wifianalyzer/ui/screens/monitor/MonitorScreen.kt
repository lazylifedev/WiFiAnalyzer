package com.lazyapps.wifianalyzer.ui.screens.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.data.WifiScanRepository
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.SignalSample
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.ui.components.ScanStatusCard
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import kotlinx.coroutines.delay

@Composable
fun MonitorScreen(
    state: ScanUiState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: (ScanState) -> Unit,
) {
    LaunchedEffect(state.selectedBssid) {
        if (state.selectedBssid != null) {
            onRefresh()
            while (true) {
                delay(WifiScanRepository.MIN_SCAN_INTERVAL_MS)
                onRefresh()
            }
        }
    }

    val accessPoint = state.selectedAccessPoint
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
            item { SignalSummary(accessPoint, state.selectedDetected, state.signalHistory, Modifier.padding(horizontal = AppSpacing.large)) }
            item { SignalChart(state.signalHistory, Modifier.padding(horizontal = AppSpacing.large)) }
        }
    }
}

@Composable
private fun SignalSummary(accessPoint: WifiAccessPoint, detected: Boolean, history: List<SignalSample>, modifier: Modifier = Modifier) {
    val stability = stabilityLabel(history)
    val progress = ((accessPoint.rssi + 100) / 55f).coerceIn(0f, 1f)
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.large), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Text(accessPoint.ssid, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
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
                    Text(if (detected) accessPoint.signalQuality.label else stringResource(R.string.monitor_not_detected), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                MetricCard(Icons.AutoMirrored.Rounded.ShowChart, stringResource(R.string.stability), stability, Modifier.weight(1f))
                MetricCard(Icons.Rounded.LocationOn, stringResource(R.string.monitor_distance), if (detected) "推定 ${accessPoint.distanceRange.label}" else "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun stabilityLabel(history: List<SignalSample>): String {
    if (history.size < 2) return "測定中"
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

@Composable
private fun SignalChart(history: List<SignalSample>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.signal_history), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.dbm_axis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.9f)) {
                repeat(4) { index ->
                    val y = size.height * index / 3f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                repeat(6) { index ->
                    val x = size.width * index / 5f
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
                if (history.isNotEmpty()) {
                    val end = history.maxOf { it.timestampMillis }
                    val start = end - 30_000L
                    val path = Path()
                    history.forEachIndexed { index, sample ->
                        val x = size.width * ((sample.timestampMillis - start).coerceIn(0, 30_000) / 30_000f)
                        val y = size.height * (1f - ((sample.rssi.coerceIn(-90, -40) + 90) / 50f))
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        if (history.size == 1) drawCircle(lineColor, 4.dp.toPx(), Offset(x, y))
                    }
                    if (history.size > 1) drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.seconds_ago_30), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.seconds_ago_15), style = MaterialTheme.typography.labelSmall)
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
