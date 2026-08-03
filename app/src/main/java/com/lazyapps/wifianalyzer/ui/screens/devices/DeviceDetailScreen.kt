package com.lazyapps.wifianalyzer.ui.screens.devices

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.domain.DetectionPolicy
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.ui.components.localizedLabel
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.photos.DevicePhotoGallery
import java.util.Date

@Composable
fun DeviceDetailScreen(
    device: RegisteredDevice?,
    detectedAccessPoints: List<WifiAccessPoint>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMonitor: (String) -> Unit,
    onOcrUpdate: () -> Unit,
    useFeet: Boolean = false,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var chooseMonitor by remember { mutableStateOf(false) }
    if (device == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.device_detail_title), action = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } })
            Text(stringResource(R.string.device_not_found), Modifier.padding(AppSpacing.large))
        }
        return
    }
    val detected = detectedAccessPoints.isNotEmpty() || DetectionPolicy.isDetected(device.lastSeenAt, System.currentTimeMillis())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item {
            ScreenHeader(device.displayName, stringResource(if (detected) R.string.detected else R.string.not_detected), action = {
                Row {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_device")) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_device")) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                }
            })
        }
        item { DevicePhotoGallery(device.id, device.workspaceId) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
            ) {
                if (detectedAccessPoints.isNotEmpty()) {
                    FilledTonalButton(onClick = {
                        if (detectedAccessPoints.size == 1) onMonitor(detectedAccessPoints.first().bssid) else chooseMonitor = true
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ShowChart, null)
                        Text(stringResource(R.string.nav_monitor), maxLines = 1)
                    }
                }
                OutlinedButton(onClick = onOcrUpdate, modifier = Modifier.weight(1f).testTag("ocr_update_device")) {
                    Icon(Icons.Rounded.CameraAlt, null)
                    Text(stringResource(R.string.scan_device_label), maxLines = 1)
                }
            }
        }
        item {
            DetailCard(stringResource(R.string.detection_information)) {
                DetailLine(stringResource(R.string.status), stringResource(if (detected) R.string.detected else R.string.not_detected))
                DetailLine(stringResource(R.string.latest_rssi), device.lastSeenRssi?.let { "$it dBm" } ?: "—")
                DetailLine(stringResource(R.string.last_seen), relativeDate(device.lastSeenAt))
                val distance = detectedAccessPoints.maxByOrNull { it.rssi }?.distanceRange?.localizedLabel(useFeet) ?: "—"
                DetailLine(stringResource(R.string.estimated_distance_label), distance)
            }
        }
        item {
            DetailCard(stringResource(R.string.device_information)) {
                DetailLine(stringResource(R.string.manufacturer), device.manufacturer.ifBlank { "—" })
                DetailLine(stringResource(R.string.model), device.model.ifBlank { "—" })
                DetailLine(stringResource(R.string.serial_number), device.serialNumber.ifBlank { "—" })
                DetailLine("SSID", device.ssid.ifBlank { "—" })
                DetailLine(stringResource(R.string.group), device.groupName ?: stringResource(R.string.uncategorized))
                DetailLine(stringResource(R.string.installation_location), device.location.ifBlank { "—" })
                DetailLine(stringResource(R.string.notes), device.notes.ifBlank { "—" })
            }
        }
        item {
            DetailCard(stringResource(R.string.bssid_list)) {
                device.bssids.forEachIndexed { index, bssid ->
                    Text("${if (index == 0) stringResource(R.string.primary_prefix) else ""}${bssid.bssid}", style = MaterialTheme.typography.bodyMedium)
                    Text("${bssid.band}${bssid.label.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            DetailCard(stringResource(R.string.record)) {
                DetailLine(stringResource(R.string.created_at), absoluteDate(device.createdAt))
                DetailLine(stringResource(R.string.updated_at), absoluteDate(device.updatedAt))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_device_title)) },
            text = { Text(pluralStringResource(R.plurals.device_delete_with_photos, device.photoCount, device.photoCount)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (chooseMonitor) {
        AlertDialog(
            onDismissRequest = { chooseMonitor = false },
            title = { Text(stringResource(R.string.select_monitor_bssid)) },
            text = {
                Column {
                    detectedAccessPoints.forEach { ap ->
                        TextButton(onClick = { chooseMonitor = false; onMonitor(ap.bssid) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${ap.bssid} · ${ap.rssi} dBm")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseMonitor = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text(label, Modifier.weight(.35f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(.65f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun absoluteDate(timestamp: Long): String = DateFormat.getMediumDateFormat(LocalContext.current).format(Date(timestamp)) + " " + DateFormat.getTimeFormat(LocalContext.current).format(Date(timestamp))

private fun relativeDate(timestamp: Long?): String = timestamp?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() } ?: "—"
