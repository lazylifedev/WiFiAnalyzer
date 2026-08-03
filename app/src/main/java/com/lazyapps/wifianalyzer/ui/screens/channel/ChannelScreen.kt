package com.lazyapps.wifianalyzer.ui.screens.channel

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.data.ChannelDisplayMode
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.domain.ChannelRecommendation
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.ui.components.BandSelector
import com.lazyapps.wifianalyzer.ui.components.RefreshProgress
import com.lazyapps.wifianalyzer.ui.components.ScanStatusCard
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
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
    onClearAccessPointSelection: () -> Unit,
    onOpenAccessPoint: (String) -> Unit,
    onRegisterAccessPoint: (WifiAccessPoint) -> Unit,
    onDisplayModeChange: (ChannelDisplayMode) -> Unit,
    workspaceName: String? = null,
    selectedBand: WifiBand = state.channelBand,
    onBandSelected: (WifiBand) -> Unit = {},
) {
    val accessPoints = remember(state.accessPoints, selectedBand) { state.accessPointsFor(selectedBand) }
    val occupancy = remember(state.accessPoints, selectedBand) { state.occupancyFor(selectedBand) }
    val candidate = remember(accessPoints, selectedBand) { ChannelRecommendation.bestCandidate(accessPoints, selectedBand) }
    val selected = accessPoints.firstOrNull { it.bssid == state.selectedBssid }
    val context = LocalContext.current
    val updated = state.lastUpdatedMillis?.let {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        stringResource(R.string.last_updated_time, DateFormat.format(pattern, it))
    } ?: stringResource(R.string.last_updated_time, stringResource(R.string.not_available))
    LaunchedEffect(selectedBand, state.selectedBssid, accessPoints) {
        if (state.selectedBssid != null && selected == null) onClearAccessPointSelection()
    }

    Column(Modifier.fillMaxSize().testTag("channel_screen")) {
        ScreenHeader(stringResource(R.string.screen_channel), listOfNotNull(workspaceName, updated).joinToString(" ・ ")) {
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_scan)) }
        }
        BandSelector(selectedBand, onBandSelected, Modifier.padding(horizontal = AppSpacing.large), state.visibleBands)
        RefreshProgress(state)
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("channel_list"),
                contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
            ) {
                item {
                    DisplayModeSelector(state.channelDisplayMode, onDisplayModeChange, Modifier.padding(horizontal = AppSpacing.large))
                }
                if (state.scanState !in setOf(ScanState.READY, ScanState.SCANNING, ScanState.THROTTLED)) {
                    item {
                        ScanStatusCard(
                            state.scanState, state.accessPoints.isNotEmpty(), onRequestPermission, onOpenSettings, onRefresh,
                            Modifier.padding(horizontal = AppSpacing.large),
                        )
                    }
                }
                if (state.channelDisplayMode == ChannelDisplayMode.GRAPH) {
                    item {
                        ChannelSummaryCard(accessPoints.size, candidate, Modifier.padding(horizontal = AppSpacing.large))
                    }
                    item {
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            ChannelGraph(
                                selectedBand,
                                accessPoints,
                                state.selectedBssid,
                                candidate,
                                onSelectAccessPoint,
                                onClearAccessPointSelection,
                                Modifier.padding(horizontal = AppSpacing.xSmall),
                            )
                        }
                    }
                    if (accessPoints.isEmpty() && state.scanState in setOf(ScanState.READY, ScanState.THROTTLED, ScanState.SCANNING, ScanState.EMPTY)) {
                        item {
                            Text(
                                stringResource(R.string.band_empty, selectedBand.label),
                                Modifier.padding(horizontal = AppSpacing.large),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    selected?.let { ap ->
                        item {
                            SelectedAccessPointCard(
                                ap,
                                state.distanceUnit == DistanceUnitPreference.FEET,
                                { onOpenAccessPoint(ap.bssid) },
                                onRegisterAccessPoint,
                                Modifier.padding(horizontal = AppSpacing.large).testTag("channel_selected_ap"),
                            )
                        }
                    }
                } else {
                    if (occupancy.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.band_empty, selectedBand.label),
                                Modifier.padding(horizontal = AppSpacing.large),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(occupancy, key = { "${selectedBand.name}_${it.channel}" }) { usage ->
                        ChannelOccupancyCard(selectedBand, usage, onOpenAccessPoint, Modifier.padding(horizontal = AppSpacing.large))
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayModeSelector(
    selected: ChannelDisplayMode,
    onSelected: (ChannelDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = ChannelDisplayMode.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                modifier = Modifier.testTag("channel_mode_${mode.name.lowercase()}"),
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(stringResource(if (mode == ChannelDisplayMode.GRAPH) R.string.channel_mode_graph else R.string.channel_mode_occupancy)) },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07111F)
@Composable
private fun ChannelPreview() = WifiAnalyzerTheme(mode = ThemeMode.DARK) {
    ChannelScreen(ScanUiState(scanState = ScanState.EMPTY), {}, {}, {}, {}, {}, {}, {}, {})
}
