package com.lazyapps.wifianalyzer.ui.screens.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ads.AdConfiguration
import com.lazyapps.wifianalyzer.ads.InlineNativeAd
import com.lazyapps.wifianalyzer.ads.InlineNativeAdPolicy
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiStandard
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.ui.components.BandSelector
import com.lazyapps.wifianalyzer.ui.components.ScanStatusCard
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.components.RegisteredBadge
import com.lazyapps.wifianalyzer.ui.components.RefreshProgress
import com.lazyapps.wifianalyzer.ui.components.localizedLabel
import com.lazyapps.wifianalyzer.ui.components.localizedSsid
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import java.util.Date

@Composable
fun HomeScreen(
    state: ScanUiState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: (ScanState) -> Unit,
    onSelectAccessPoint: (String) -> Unit,
    onRegisterAccessPoint: (WifiAccessPoint) -> Unit,
    workspaceName: String? = null,
    selectedBand: WifiBand = state.homeBand,
    onBandSelected: (WifiBand) -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onOpenOcr: () -> Unit = {},
    showInlineNativeAd: Boolean = false,
    inlineAdContent: @Composable (Modifier) -> Unit = { modifier ->
        InlineNativeAd(AdConfiguration.homeNativeUnitId, "home_inline_native_ad", modifier)
    },
) {
    val band = selectedBand
    val accessPoints = state.accessPointsFor(band)
    val context = LocalContext.current
    val updated = state.lastUpdatedMillis?.let {
        stringResource(R.string.last_updated_time, DateFormat.getTimeFormat(context).format(Date(it)))
    } ?: stringResource(R.string.last_updated_time, stringResource(R.string.not_available))

    Column(Modifier.fillMaxSize().testTag("home_screen")) {
        ScreenHeader(stringResource(R.string.screen_home), listOfNotNull(workspaceName, updated).joinToString(" ・ ")) {
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_scan)) }
        }
        BandSelector(band, onBandSelected, Modifier.padding(horizontal = AppSpacing.large), state.visibleBands)
        RefreshProgress(state)
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
        LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_access_point_list"),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        item {
            ScanStatusCard(
                state = state.scanState,
                hasResults = state.accessPoints.isNotEmpty(),
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                onRefresh = onRefresh,
                modifier = Modifier.padding(horizontal = AppSpacing.large),
            )
        }
        if (state.accessPoints.isEmpty() && state.lastUpdatedMillis == null) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
                ) {
                    Text(stringResource(R.string.home_get_started_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_get_started_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_wifi)) }
                    OutlinedButton(onClick = onOpenDevices, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_device)) }
                    TextButton(onClick = onOpenOcr, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_with_ocr)) }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large, vertical = AppSpacing.small)) {
                Text(stringResource(R.string.nearby_access_points), style = MaterialTheme.typography.titleMedium)
                Text(pluralStringResource(R.plurals.access_point_count, accessPoints.size, accessPoints.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (accessPoints.isEmpty() && state.scanState in setOf(ScanState.READY, ScanState.THROTTLED, ScanState.SCANNING)) {
            item {
                Text(
                    stringResource(R.string.band_empty, band.label),
                    modifier = Modifier.padding(horizontal = AppSpacing.large),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val nativeAdIndex = InlineNativeAdPolicy.insertionIndex(accessPoints.size, InlineNativeAdPolicy.HOME_AFTER_COUNT)
            ?.takeIf { showInlineNativeAd }
        itemsIndexed(accessPoints, key = { _, item -> "access_point_${item.bssid}" }) { index, accessPoint ->
            Column {
                AccessPointRow(accessPoint, onSelectAccessPoint, onRegisterAccessPoint, state.distanceUnit == DistanceUnitPreference.FEET, Modifier.padding(horizontal = AppSpacing.large))
                if (nativeAdIndex == index + 1) {
                    inlineAdContent(Modifier.padding(horizontal = AppSpacing.large, vertical = AppSpacing.small))
                }
            }
        }
        }
        }
    }
}

@Composable
private fun AccessPointRow(accessPoint: WifiAccessPoint, onClick: (String) -> Unit, onRegister: (WifiAccessPoint) -> Unit, feet: Boolean, modifier: Modifier = Modifier) {
    val signalColor = when (accessPoint.signalQuality) {
        SignalQuality.EXCELLENT, SignalQuality.GOOD -> MaterialTheme.colorScheme.primary
        SignalQuality.FAIR -> MaterialTheme.colorScheme.tertiary
        SignalQuality.WEAK -> MaterialTheme.colorScheme.error
    }
    val registeredDescription = if (accessPoint.isRegistered) stringResource(R.string.registered_description_prefix) else ""
    val displaySsid = accessPoint.ssid.localizedSsid()
    val rowDescription = stringResource(R.string.access_point_description, registeredDescription, displaySsid, accessPoint.rssi)
    Card(
        modifier = modifier.fillMaxWidth()
            .testTag("home_access_point_${accessPoint.bssid}")
            .semantics { contentDescription = rowDescription }
            .clickable { onClick(accessPoint.bssid) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        ) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = signalColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displaySsid, modifier = Modifier.testTag("home_ssid_${accessPoint.bssid}"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (accessPoint.isRegistered) RegisteredBadge(Modifier.testTag("home_registered_${accessPoint.bssid}").clearAndSetSemantics { })
                accessPoint.registeredDeviceName?.let { name ->
                    Text(stringResource(R.string.saved_name_format, name), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.group_format, accessPoint.registeredGroupName ?: stringResource(R.string.uncategorized)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(accessPoint.bssid, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${accessPoint.securityType.localizedLabel()} · ${accessPoint.band.label} · CH ${accessPoint.channel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${accessPoint.frequencyMhz} MHz · ${accessPoint.channelWidthMhz} MHz · ${accessPoint.wifiStandard.localizedLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (accessPoint.isRegistered) {
                    Text(stringResource(R.string.select_for_monitor), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = { onRegister(accessPoint) }) { Text(stringResource(R.string.save_as_device)) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${accessPoint.rssi} dBm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = signalColor)
                Text(accessPoint.signalQuality.localizedLabel(), style = MaterialTheme.typography.labelSmall, color = signalColor)
                Text(stringResource(R.string.estimated_prefix, accessPoint.distanceRange.localizedLabel(feet)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val previewAccessPoint = WifiAccessPoint(
    ssid = "Preview Wi-Fi", bssid = "12:34:56:78:9A:BC", rssi = -58,
    frequencyMhz = 2437, channel = 6, channelWidthMhz = 20, capabilities = "[WPA2-PSK]",
    timestampMicros = 1, band = WifiBand.BAND_24, signalQuality = SignalQuality.GOOD,
    securityType = SecurityType.WPA2, wifiStandard = WifiStandard.WIFI_4,
    distanceRange = DistanceRange.THREE_TO_EIGHT, observedAtMillis = 1,
)

@Preview(showBackground = true, backgroundColor = 0xFF07111F)
@Composable
private fun HomePreview() = WifiAnalyzerTheme(mode = ThemeMode.DARK) {
    HomeScreen(ScanUiState(ScanState.READY, listOf(previewAccessPoint), 1), {}, {}, {}, {}, {})
}
