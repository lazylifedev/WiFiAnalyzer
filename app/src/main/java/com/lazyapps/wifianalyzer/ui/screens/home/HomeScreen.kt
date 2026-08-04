package com.lazyapps.wifianalyzer.ui.screens.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ads.AdConfiguration
import com.lazyapps.wifianalyzer.ads.InlineNativeAdContent
import com.lazyapps.wifianalyzer.ads.InlineNativeAdPolicy
import com.lazyapps.wifianalyzer.ads.rememberInlineNativeAd
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
    inlineAdContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val band = selectedBand
    val accessPoints = state.accessPointsFor(band)
    val nativeAdIndex = InlineNativeAdPolicy.insertionIndex(accessPoints.size, InlineNativeAdPolicy.HOME_AFTER_COUNT)
        ?.takeIf { showInlineNativeAd }
    val screenNativeAd = if (inlineAdContent == null) rememberInlineNativeAd(
        unitId = AdConfiguration.homeNativeUnitId,
        enabled = showInlineNativeAd,
        requestEligible = nativeAdIndex != null,
        debugPlacement = "home",
    ) else null
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
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
            indicator = {},
        ) {
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
                showScanningStatus = false,
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
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large, vertical = AppSpacing.small).testTag("home_access_point_section")) {
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
        val renderedItemCount = accessPoints.size + if (nativeAdIndex != null) 1 else 0
        items(
            count = renderedItemCount,
            key = { listIndex ->
                if (listIndex == nativeAdIndex) "home_inline_native_ad_item"
                else "access_point_${accessPoints[if (nativeAdIndex != null && listIndex > nativeAdIndex) listIndex - 1 else listIndex].bssid}"
            },
        ) { listIndex ->
            if (listIndex == nativeAdIndex) {
                val adModifier = Modifier.padding(horizontal = AppSpacing.large, vertical = AppSpacing.small)
                if (inlineAdContent != null) inlineAdContent(adModifier)
                else InlineNativeAdContent(screenNativeAd, "home_inline_native_ad", adModifier)
            } else {
                val accessPointIndex = if (nativeAdIndex != null && listIndex > nativeAdIndex) listIndex - 1 else listIndex
                DenseAccessPointRow(accessPoints[accessPointIndex], onSelectAccessPoint, onRegisterAccessPoint, state.distanceUnit == DistanceUnitPreference.FEET, Modifier.padding(horizontal = AppSpacing.large))
            }
        }
        }
        }
    }
}

@Composable
private fun DenseAccessPointRow(accessPoint: WifiAccessPoint, onClick: (String) -> Unit, onRegister: (WifiAccessPoint) -> Unit, feet: Boolean, modifier: Modifier = Modifier) {
    val signalColor = when (accessPoint.signalQuality) { SignalQuality.EXCELLENT, SignalQuality.GOOD -> MaterialTheme.colorScheme.primary; SignalQuality.FAIR -> MaterialTheme.colorScheme.tertiary; SignalQuality.WEAK -> MaterialTheme.colorScheme.error }
    val stableId = accessPoint.bssid.replace(Regex("[^A-Za-z0-9]"), "_")
    val displaySsid = accessPoint.ssid.localizedSsid()
    val registeredDescription = if (accessPoint.isRegistered) stringResource(R.string.registered_description_prefix) else ""
    val rowDescription = stringResource(R.string.access_point_description, registeredDescription, displaySsid, accessPoint.rssi)
    var expanded by rememberSaveable(accessPoint.bssid) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth().testTag("home_access_point_card_$stableId").semantics { contentDescription = rowDescription }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.medium, vertical = 7.dp)) {
            Column(Modifier.fillMaxWidth().padding(end = 48.dp).testTag("home_access_point_${accessPoint.bssid}"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth().clickable { onClick(accessPoint.bssid) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(displaySsid, Modifier.weight(1f).testTag("home_ssid_${accessPoint.bssid}"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp))
                }
                Row(Modifier.fillMaxWidth().clickable { onClick(accessPoint.bssid) }, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Wifi, null, tint = signalColor, modifier = Modifier.size(18.dp))
                        Text(accessPoint.bssid, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("home_bssid_$stableId"))
                    }
                    Column(Modifier.widthIn(min = 72.dp), horizontalAlignment = Alignment.End) {
                        Text("${accessPoint.rssi} dBm", maxLines = 1, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = signalColor, modifier = Modifier.testTag("home_rssi_$stableId"))
                        Text(accessPoint.signalQuality.localizedLabel(), maxLines = 1, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp), color = signalColor, modifier = Modifier.testTag("home_signal_quality_$stableId"))
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { onClick(accessPoint.bssid) }, verticalAlignment = Alignment.CenterVertically) { Text("${accessPoint.band.label} · CH ${accessPoint.channel} · ${stringResource(R.string.estimated_prefix, accessPoint.distanceRange.localizedLabel(feet))}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (expanded) Column(Modifier.testTag("home_access_point_details_$stableId").padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text("${accessPoint.securityType.localizedLabel()} · ${accessPoint.frequencyMhz} MHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${accessPoint.channelWidthMhz} MHz · ${accessPoint.wifiStandard.localizedLabel()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); accessPoint.registeredDeviceName?.let { Text(stringResource(R.string.saved_name_format, it), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }; accessPoint.registeredGroupName?.let { Text(stringResource(R.string.group_format, it), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) } }
            }
            Box(Modifier.align(Alignment.TopEnd).size(48.dp).testTag("home_registration_slot_$stableId"), contentAlignment = Alignment.Center) { if (accessPoint.isRegistered) Icon(Icons.Rounded.CheckCircle, stringResource(R.string.registered), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp).testTag("home_registered_${accessPoint.bssid}")) else IconButton(onClick = { onRegister(accessPoint) }, modifier = Modifier.size(48.dp).testTag("home_register_device_$stableId")) { Icon(Icons.Rounded.AddCircleOutline, stringResource(R.string.register_as_device), modifier = Modifier.size(24.dp).testTag("home_unregistered_icon_$stableId")) } }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).testTag("home_access_point_expand_$stableId")) { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, stringResource(if (expanded) R.string.hide_details else R.string.show_details)) }
        }
    }
}

@Composable
private fun AccessPointRow(accessPoint: WifiAccessPoint, onClick: (String) -> Unit, onRegister: (WifiAccessPoint) -> Unit, feet: Boolean, modifier: Modifier = Modifier) {
    val signalColor = when (accessPoint.signalQuality) { SignalQuality.EXCELLENT, SignalQuality.GOOD -> MaterialTheme.colorScheme.primary; SignalQuality.FAIR -> MaterialTheme.colorScheme.tertiary; SignalQuality.WEAK -> MaterialTheme.colorScheme.error }
    val stableId = accessPoint.bssid.replace(Regex("[^A-Za-z0-9]"), "_")
    val displaySsid = accessPoint.ssid.localizedSsid()
    val registeredDescription = if (accessPoint.isRegistered) stringResource(R.string.registered_description_prefix) else ""
    val rowDescription = stringResource(R.string.access_point_description, registeredDescription, displaySsid, accessPoint.rssi)
    var expanded by rememberSaveable(accessPoint.bssid) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth().testTag("home_access_point_card_$stableId").semantics { contentDescription = rowDescription }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.medium, vertical = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(end = 92.dp, bottom = 48.dp).testTag("home_access_point_${accessPoint.bssid}").clickable { onClick(accessPoint.bssid) }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displaySsid, Modifier.testTag("home_ssid_${accessPoint.bssid}"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Rounded.Wifi, null, tint = signalColor, modifier = Modifier.size(18.dp)); Text(accessPoint.bssid, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${accessPoint.band.label} ﾂｷ CH ${accessPoint.channel} ﾂｷ ${stringResource(R.string.estimated_prefix, accessPoint.distanceRange.localizedLabel(feet))}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expanded) Column(Modifier.testTag("home_access_point_details_$stableId").padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${accessPoint.securityType.localizedLabel()} ﾂｷ ${accessPoint.frequencyMhz} MHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${accessPoint.channelWidthMhz} MHz ﾂｷ ${accessPoint.wifiStandard.localizedLabel()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    accessPoint.registeredDeviceName?.let { Text(stringResource(R.string.saved_name_format, it), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                    accessPoint.registeredGroupName?.let { Text(stringResource(R.string.group_format, it), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                }
            }
            Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${accessPoint.rssi} dBm", maxLines = 1, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = signalColor, modifier = Modifier.testTag("home_rssi_$stableId"))
                    Text(accessPoint.signalQuality.localizedLabel(), maxLines = 1, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp), color = signalColor)
                }
                Box(Modifier.size(48.dp).testTag("home_registration_slot_$stableId"), contentAlignment = Alignment.Center) { if (accessPoint.isRegistered) Icon(Icons.Rounded.CheckCircle, stringResource(R.string.registered), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp).testTag("home_registered_${accessPoint.bssid}")) else IconButton(onClick = { onRegister(accessPoint) }, modifier = Modifier.size(48.dp).testTag("home_register_device_$stableId")) { Icon(Icons.Rounded.AddCircleOutline, stringResource(R.string.register_as_device), modifier = Modifier.size(24.dp).testTag("home_unregistered_icon_$stableId")) } }
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).testTag("home_access_point_expand_$stableId")) { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, stringResource(if (expanded) R.string.hide_details else R.string.show_details)) }
        }
    }
}

@Composable
private fun LegacyAccessPointRow(accessPoint: WifiAccessPoint, onClick: (String) -> Unit, onRegister: (WifiAccessPoint) -> Unit, feet: Boolean, modifier: Modifier = Modifier) {
    val signalColor = when (accessPoint.signalQuality) {
        SignalQuality.EXCELLENT, SignalQuality.GOOD -> MaterialTheme.colorScheme.primary
        SignalQuality.FAIR -> MaterialTheme.colorScheme.tertiary
        SignalQuality.WEAK -> MaterialTheme.colorScheme.error
    }
    val registeredDescription = if (accessPoint.isRegistered) stringResource(R.string.registered_description_prefix) else ""
    val displaySsid = accessPoint.ssid.localizedSsid()
    val rowDescription = stringResource(R.string.access_point_description, registeredDescription, displaySsid, accessPoint.rssi)
    val stableId = accessPoint.bssid.replace(Regex("[^A-Za-z0-9]"), "_")
    var expanded by rememberSaveable(accessPoint.bssid) { mutableStateOf(false) }
    val detailsDescription = stringResource(if (expanded) R.string.hide_details else R.string.show_details)
    Card(
        modifier = modifier.fillMaxWidth()
            .testTag("home_access_point_card_$stableId")
            .semantics { contentDescription = rowDescription },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.medium, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = signalColor)
            Column(
                Modifier.weight(1f).testTag("home_access_point_${accessPoint.bssid}").clickable { onClick(accessPoint.bssid) },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displaySsid, modifier = Modifier.weight(1f).testTag("home_ssid_${accessPoint.bssid}"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp))
                    Box(Modifier.size(48.dp).testTag("home_registration_slot_$stableId"), contentAlignment = Alignment.Center) {
                        if (accessPoint.isRegistered) {
                            Icon(Icons.Rounded.CheckCircle, stringResource(R.string.registered), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp).testTag("home_registered_${accessPoint.bssid}"))
                        } else {
                            IconButton(onClick = { onRegister(accessPoint) }, modifier = Modifier.size(48.dp).testTag("home_register_device_$stableId")) {
                                Icon(Icons.Rounded.AddCircleOutline, stringResource(R.string.register_as_device), modifier = Modifier.size(24.dp).testTag("home_unregistered_icon_$stableId"))
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${accessPoint.rssi} dBm", maxLines = 1, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp), fontWeight = FontWeight.Bold, color = signalColor, modifier = Modifier.testTag("home_rssi_$stableId"))
                        Text(accessPoint.signalQuality.localizedLabel(), maxLines = 1, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp), color = signalColor)
                    }
                }
                Text(accessPoint.bssid, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${accessPoint.band.label} · CH ${accessPoint.channel} · ${stringResource(R.string.estimated_prefix, accessPoint.distanceRange.localizedLabel(feet))}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp).testTag("home_access_point_expand_$stableId")) {
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, detailsDescription)
                    }
                }
                if (expanded) {
                    Column(Modifier.testTag("home_access_point_details_$stableId").padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("${accessPoint.securityType.localizedLabel()} · ${accessPoint.frequencyMhz} MHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${accessPoint.channelWidthMhz} MHz · ${accessPoint.wifiStandard.localizedLabel()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        accessPoint.registeredDeviceName?.let { Text(stringResource(R.string.saved_name_format, it), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                        accessPoint.registeredGroupName?.let { Text(stringResource(R.string.group_format, it), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                    }
                }
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
