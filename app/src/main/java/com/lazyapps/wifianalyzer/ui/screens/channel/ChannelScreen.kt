package com.lazyapps.wifianalyzer.ui.screens.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.ChannelOccupancy
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.ui.components.RegisteredBadge
import com.lazyapps.wifianalyzer.ui.components.BandSelector
import com.lazyapps.wifianalyzer.ui.components.ScanStatusCard
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.components.RefreshProgress
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme

@Composable
fun ChannelScreen(
    state: ScanUiState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: (ScanState) -> Unit,
    onSelectAccessPoint: (String) -> Unit,
    onRegisterAccessPoint: (WifiAccessPoint) -> Unit,
    workspaceName: String? = null,
) {
    var band by remember { mutableStateOf(WifiBand.BAND_24) }
    val occupancy = state.occupancyFor(band)
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item {
            ScreenHeader(stringResource(R.string.screen_channel), listOfNotNull(workspaceName, stringResource(R.string.estimated_congestion)).joinToString(" ・ ")) {
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_scan)) }
            }
        }
        item { RefreshProgress(state) }
        item { BandSelector(band, { band = it }, Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            ScanStatusCard(
                state.scanState, state.accessPoints.isNotEmpty(), onRequestPermission, onOpenSettings, onRefresh,
                Modifier.padding(horizontal = AppSpacing.large),
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${stringResource(R.string.estimated_congestion)}: ${stringResource(R.string.occupancy_legend_free)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.occupancy_legend_busy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (occupancy.isEmpty() && state.scanState in setOf(ScanState.READY, ScanState.THROTTLED, ScanState.SCANNING)) {
            item { Text(stringResource(R.string.band_empty, band.label), Modifier.padding(horizontal = AppSpacing.large), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(occupancy, key = { it.channel }) { usage ->
            ChannelCard(usage, onSelectAccessPoint, onRegisterAccessPoint, Modifier.padding(horizontal = AppSpacing.large))
        }
    }
    }
}

@Composable
private fun ChannelCard(usage: ChannelOccupancy, onSelect: (String) -> Unit, onRegister: (WifiAccessPoint) -> Unit, modifier: Modifier = Modifier) {
    val barColor = when {
        usage.estimatedCongestion >= .8f -> MaterialTheme.colorScheme.error
        usage.estimatedCongestion >= .55f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.channel_format, usage.channel), style = MaterialTheme.typography.titleLarge)
                Text("  ${usage.frequencyMhz} MHz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${stringResource(R.string.estimated_congestion)} ${(usage.estimatedCongestion * 100).toInt()}%", color = barColor, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { usage.estimatedCongestion },
                modifier = Modifier.fillMaxWidth(),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(stringResource(R.string.network_count, usage.accessPoints.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            usage.accessPoints.forEach { accessPoint ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(accessPoint.bssid) }.padding(vertical = AppSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            Text(accessPoint.ssid, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            if (accessPoint.isRegistered) RegisteredBadge()
                        }
                        accessPoint.registeredDeviceName?.let { Text("$it · ${accessPoint.registeredGroupName ?: "未分類"}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        Text(accessPoint.bssid, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${accessPoint.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                        Text("推定 ${accessPoint.distanceRange.label}", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!accessPoint.isRegistered) TextButton(onClick = { onRegister(accessPoint) }) { Text("登録") }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07111F)
@Composable
private fun ChannelPreview() = WifiAnalyzerTheme(mode = ThemeMode.DARK) {
    ChannelScreen(ScanUiState(scanState = ScanState.EMPTY), {}, {}, {}, {}, {})
}
