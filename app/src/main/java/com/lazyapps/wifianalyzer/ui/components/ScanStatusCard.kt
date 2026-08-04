package com.lazyapps.wifianalyzer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

@Composable
fun ScanStatusCard(
    state: ScanState,
    hasResults: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: (ScanState) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    showScanningStatus: Boolean = true,
) {
    val content = when (state) {
        ScanState.PERMISSION_REQUIRED -> StatusContent(R.string.scan_permission_required, R.string.grant_permission, onRequestPermission)
        ScanState.PERMISSION_DENIED -> StatusContent(R.string.scan_permission_denied, R.string.grant_permission, onRequestPermission)
        ScanState.PERMISSION_PERMANENTLY_DENIED -> StatusContent(R.string.scan_permission_permanently_denied, R.string.open_app_settings) { onOpenSettings(state) }
        ScanState.LOCATION_DISABLED -> StatusContent(R.string.location_disabled, R.string.open_location_settings) { onOpenSettings(state) }
        ScanState.WIFI_DISABLED -> StatusContent(R.string.wifi_disabled, R.string.open_wifi_settings) { onOpenSettings(state) }
        ScanState.THROTTLED -> StatusContent(R.string.scan_throttled, null, null)
        ScanState.SCANNING -> if (showScanningStatus) StatusContent(R.string.scan_in_progress, null, null) else null
        ScanState.EMPTY -> StatusContent(R.string.scan_empty, R.string.refresh_scan, onRefresh)
        ScanState.ERROR -> StatusContent(R.string.scan_error, R.string.refresh_scan, onRefresh)
        ScanState.READY -> null
    } ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state in setOf(ScanState.PERMISSION_DENIED, ScanState.PERMISSION_PERMANENTLY_DENIED, ScanState.ERROR)) {
                MaterialTheme.colorScheme.errorContainer
            } else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Icon(Icons.Rounded.Info, contentDescription = null)
                Text(stringResource(content.messageRes), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            content.actionRes?.let { label ->
                if (hasResults && state == ScanState.THROTTLED) {
                    TextButton(onClick = content.action ?: {}) { Text(stringResource(label)) }
                } else {
                    Button(onClick = content.action ?: {}) { Text(stringResource(label)) }
                }
            }
        }
    }
}

private data class StatusContent(
    val messageRes: Int,
    val actionRes: Int?,
    val action: (() -> Unit)?,
)
