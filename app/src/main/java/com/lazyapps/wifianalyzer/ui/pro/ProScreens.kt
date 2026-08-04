package com.lazyapps.wifianalyzer.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.billing.BillingUiState
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.billing.ProEntitlementState
import com.lazyapps.wifianalyzer.billing.AccessRestriction
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.kintone.KintoneUiState
import com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceOption
import com.lazyapps.wifianalyzer.kintone.KintoneErrorMessages
import com.lazyapps.wifianalyzer.ui.operation.OperationState
import java.text.DateFormat
import java.util.Date

@Composable
fun ProScreen(state: BillingUiState, onBack: () -> Unit, onPurchase: () -> Unit, onRestore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
    ) {
        Header(stringResource(R.string.pro_title), onBack)
        when (state.entitlement) {
            ProEntitlementState.Pro -> {
                Text(stringResource(R.string.pro_active), style = MaterialTheme.typography.headlineSmall)
                BenefitList(listOf(stringResource(R.string.pro_purchased), stringResource(R.string.pro_no_ads), stringResource(R.string.pro_all_features)))
            }
            ProEntitlementState.Pending -> {
                Text(stringResource(R.string.pro_purchase_pending), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.pro_purchase_pending_detail))
                RestoreButton(state, onRestore)
            }
            else -> {
                Text(stringResource(R.string.pro_unlock), style = MaterialTheme.typography.headlineSmall)
                BenefitList(listOf(stringResource(R.string.pro_no_ads), stringResource(R.string.pro_unlock_features), stringResource(R.string.kintone_integration), stringResource(R.string.pro_future_features), stringResource(R.string.pro_one_time_purchase), stringResource(R.string.pro_no_recurring_fees)))
                state.product?.let { Text(stringResource(R.string.pro_price, it.formattedPrice), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("pro_price")) }
                if (state.connection == com.lazyapps.wifianalyzer.billing.BillingConnectionState.CONNECTING) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        CircularProgressIndicator(); Text(stringResource(R.string.pro_loading_product))
                    }
                } else if (state.product == null) {
                    Text(stringResource(R.string.pro_product_unavailable), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onPurchase,
                    enabled = state.canPurchase,
                    modifier = Modifier.fillMaxWidth().testTag("purchase_pro"),
                ) { if (state.purchasing) { CircularProgressIndicator(); Text(stringResource(R.string.pro_purchasing)) } else Text(stringResource(R.string.pro_purchase_button)) }
                RestoreButton(state, onRestore)
            }
        }
    }
}

@Composable
fun ProRestrictionDialog(reason: AccessRestriction, onOpenPro: () -> Unit, onDismiss: () -> Unit) {
    val message = when (reason) {
        AccessRestriction.SavedDeviceLimitReached -> R.string.pro_device_limit_message
        AccessRestriction.WorkspaceLimitReached -> R.string.pro_workspace_limit_message
        AccessRestriction.DevicePhotoLimitReached -> R.string.pro_photo_limit_message
        else -> R.string.pro_restriction_message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pro_limit_title)) },
        text = { Text(stringResource(R.string.pro_restriction_message) + "\n" + stringResource(message)) },
        confirmButton = { TextButton(onClick = { onDismiss(); onOpenPro() }, modifier = Modifier.testTag("pro_view")) { Text(stringResource(R.string.view_pro)) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("pro_close")) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun KintoneScreen(
    access: FeatureAccessPolicy,
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    onPluginInfo: () -> Unit,
    onOpenBooth: () -> Unit = {},
    state: KintoneUiState = KintoneUiState(),
    onScanQr: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancelPending: () -> Unit = {},
    onVerify: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onSync: () -> Unit = {},
    onCancelSync: () -> Unit = {},
    onAutoSyncChange: (Boolean) -> Unit = {},
    onPhotoAutoSyncChange: (Boolean) -> Unit = {},
    onWorkspaceSelected: (KintoneWorkspaceOption) -> Unit = {},
) {
    val confirmDisconnect = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val confirmAutoSync = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val showWorkspaceSelector = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Header(stringResource(R.string.kintone_integration), onBack)
        if (!access.canUseKintone) {
            Text(stringResource(R.string.kintone_pro_required), style = MaterialTheme.typography.titleLarge)
            Button(onClick = onOpenPro, modifier = Modifier.fillMaxWidth().testTag("kintone_open_pro")) { Text(stringResource(R.string.learn_about_pro)) }
        } else {
            Text(stringResource(R.string.kintone_sync_workspace), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showWorkspaceSelector.value = true }, modifier = Modifier.fillMaxWidth().testTag("kintone_workspace_selector")) {
                Text(state.workspaceName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.kintone_workspace_dropdown))
            }
            if (state.appWorkspaceId > 0 && state.appWorkspaceId != state.workspaceId) Text(stringResource(R.string.kintone_different_workspace), color = MaterialTheme.colorScheme.tertiary)
            ConnectionStatusCard(state)
            state.errorCode?.let {
                val text = when (state.failureContext) {
                    com.lazyapps.wifianalyzer.ui.kintone.KintoneFailureContext.QR -> stringResource(R.string.kintone_qr_error)
                    com.lazyapps.wifianalyzer.ui.kintone.KintoneFailureContext.SYNC -> stringResource(R.string.kintone_sync_error)
                    else -> stringResource(R.string.kintone_connection_failed)
                }
                Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("kintone_error_message"))
            }
            state.message?.let { Text(it, modifier = Modifier.testTag("kintone_message")) }
            state.connection?.let {
                KintoneAutoSyncSection(state, onSync, onVerify, onAutoSyncChange, onPhotoAutoSyncChange) { confirmAutoSync.value = true }
                OutlinedButton(onClick = onVerify, modifier = Modifier.fillMaxWidth().testTag("kintone_verify")) { Text(stringResource(R.string.kintone_check_connection)) }
                OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth().testTag("kintone_rescan_qr")) { Icon(Icons.Rounded.QrCodeScanner, null); Text(stringResource(R.string.kintone_reconnect), Modifier.padding(start = AppSpacing.small)) }
                TextButton(onClick = { confirmDisconnect.value = true }, modifier = Modifier.fillMaxWidth().testTag("kintone_disconnect")) { Text(stringResource(R.string.kintone_disconnect)) }
            } ?: Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                Button(onClick = onScanQr, modifier = Modifier.fillMaxWidth().testTag("kintone_scan_qr")) { Icon(Icons.Rounded.QrCodeScanner, null); Text(stringResource(R.string.kintone_scan_qr), Modifier.padding(start = AppSpacing.small)) }
                Text(stringResource(R.string.kintone_no_plugin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BoothButton(onOpenBooth)
            }
            if (state.operation is OperationState.Running) {
                val running = state.operation as OperationState.Running
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text(stringResource(R.string.kintone_processing), Modifier.padding(start = AppSpacing.small)) }
                when (val progress = running.progress) {
                    is com.lazyapps.wifianalyzer.ui.operation.OperationProgress.Count -> {
                        LinearProgressIndicator(progress = { progress.fraction }, Modifier.fillMaxWidth())
                        Text(stringResource(R.string.kintone_sync_progress, progress.current, progress.total))
                    }
                    else -> Unit
                }
                if (running.cancellable) TextButton(onClick = onCancelSync, modifier = Modifier.testTag("kintone_cancel_sync")) { Text(stringResource(R.string.kintone_cancel)) }
            }
            state.syncResult?.let { result ->
                Card(Modifier.fillMaxWidth().testTag("kintone_sync_result")) { Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(stringResource(R.string.kintone_sync_result), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.kintone_sync_result_summary, result.total, result.succeeded, result.failed, result.skipped))
                    if (result.failed > 0) Text(stringResource(R.string.kintone_retry_note), color = MaterialTheme.colorScheme.error)
                    result.batches.firstOrNull { it.validationErrors.isNotEmpty() }?.let { failure ->
                        val detail = failure.validationErrors.first()
                        Text(stringResource(R.string.kintone_error_field_inline, detail.path), modifier = Modifier.testTag("kintone_sync_error_field"))
                        Text(stringResource(R.string.kintone_error_detail_inline, stringResource(R.string.kintone_validation_detail)), modifier = Modifier.testTag("kintone_sync_error_detail"))
                        failure.recordIndex?.let { Text(stringResource(R.string.kintone_record_index, it + 1)) }
                    }
                } }
            }
            OutlinedButton(onClick = onPluginInfo, modifier = Modifier.fillMaxWidth().testTag("kintone_plugin_info")) { Icon(Icons.Rounded.Info, null); Text(stringResource(R.string.kintone_plugin_info), Modifier.padding(start = AppSpacing.small)) }
            if (state.connection != null) BoothButton(onOpenBooth)
        }
    }
    if (showWorkspaceSelector.value) AlertDialog(
        onDismissRequest = { showWorkspaceSelector.value = false },
        title = { Text(stringResource(R.string.kintone_sync_workspace)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            state.workspaces.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable { onWorkspaceSelected(option); showWorkspaceSelector.value = false }.testTag("kintone_workspace_${option.id}"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = option.id == state.workspaceId, onClick = { onWorkspaceSelected(option); showWorkspaceSelector.value = false })
                    Column(Modifier.weight(1f)) {
                        Text(option.name)
                        Text(if (option.autoSyncEnabled) stringResource(R.string.kintone_workspace_summary_auto, option.deviceCount, if (option.connected) stringResource(R.string.kintone_status_connected) else stringResource(R.string.kintone_not_connected)) else stringResource(R.string.kintone_workspace_summary, option.deviceCount, if (option.connected) stringResource(R.string.kintone_status_connected) else stringResource(R.string.kintone_not_connected)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { showWorkspaceSelector.value = false }, modifier = Modifier.testTag("kintone_workspace_close")) { Text(stringResource(R.string.kintone_close)) } },
    )
    state.pending?.let { pending -> AlertDialog(
        onDismissRequest = onCancelPending,
        title = { Text(stringResource(R.string.kintone_confirm_connection)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(stringResource(R.string.kintone_domain, maskDomain(pending.payload.domain)))
            Text(stringResource(R.string.kintone_app_id, pending.payload.appId))
            Text(stringResource(R.string.kintone_plugin_version, pending.payload.pluginVersion))
            Text(stringResource(R.string.kintone_template_version, pending.payload.templateVersion))
            Text(stringResource(R.string.kintone_field_schema_version, pending.payload.fieldSchemaVersion))
            Text(stringResource(R.string.kintone_destination_workspace, pending.workspaceName))
            Text(stringResource(R.string.kintone_api_token_configured))
            if (pending.verification.warnings.isEmpty()) Text(stringResource(R.string.kintone_fields_verified)) else pending.verification.warnings.forEach { Text(stringResource(R.string.kintone_warning, kintoneVerificationMessage(it)), color = MaterialTheme.colorScheme.error) }
            pending.verification.information.forEach { Text(kintoneVerificationMessage(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (pending.duplicateTarget) Text(stringResource(R.string.kintone_duplicate_target), color = MaterialTheme.colorScheme.error)
            if (pending.replacing) Text(stringResource(R.string.kintone_replacing_connection))
        } },
        confirmButton = { Button(onClick = onConfirm, modifier = Modifier.testTag("kintone_confirm")) { Text(stringResource(R.string.kintone_connect)) } },
        dismissButton = { TextButton(onClick = onCancelPending, modifier = Modifier.testTag("kintone_confirm_cancel")) { Text(stringResource(R.string.kintone_cancel)) } },
    ) }
    if (confirmDisconnect.value) AlertDialog(
        onDismissRequest = { confirmDisconnect.value = false },
        title = { Text(stringResource(R.string.kintone_disconnect_title)) },
        text = { Text(stringResource(R.string.kintone_disconnect_message)) },
        confirmButton = { Button(onClick = { confirmDisconnect.value = false; onDisconnect() }, modifier = Modifier.testTag("kintone_disconnect_confirm")) { Text(stringResource(R.string.kintone_disconnect)) } },
        dismissButton = { TextButton(onClick = { confirmDisconnect.value = false }) { Text(stringResource(R.string.kintone_cancel)) } },
    )
    if (confirmAutoSync.value) AlertDialog(
        onDismissRequest = { confirmAutoSync.value = false },
        title = { Text(stringResource(R.string.kintone_enable_auto_sync_title)) },
        text = { Text(stringResource(R.string.kintone_enable_auto_sync_message)) },
        confirmButton = { Button(onClick = { confirmAutoSync.value = false; onAutoSyncChange(true) }, modifier = Modifier.testTag("kintone_auto_sync_confirm")) { Text(stringResource(R.string.kintone_enable)) } },
        dismissButton = { TextButton(onClick = { confirmAutoSync.value = false }) { Text(stringResource(R.string.kintone_cancel)) } },
    )
}

@Composable
private fun ConnectionStatusCard(state: KintoneUiState) {
    val error = state.errorCode != null
    val connected = state.connection != null
    val icon = when { error -> Icons.Rounded.Error; connected -> Icons.Rounded.CheckCircle; else -> Icons.Rounded.CloudOff }
    val color = when { error -> MaterialTheme.colorScheme.error; connected -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Card(Modifier.fillMaxWidth().testTag("kintone_connection_status")) {
        Row(Modifier.padding(AppSpacing.medium), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(when { error -> R.string.kintone_connection_error; connected -> R.string.kintone_status_connected; else -> R.string.kintone_not_connected }), style = MaterialTheme.typography.titleLarge, color = color)
                if (connected) {
                    Text(stringResource(R.string.kintone_last_sync, if (state.autoSync.lastFinishedAt > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.autoSync.lastFinishedAt)) else stringResource(R.string.kintone_never)), style = MaterialTheme.typography.bodySmall)
                    Text(syncStatusLabel(state.autoSync.status), style = MaterialTheme.typography.bodySmall, color = if (state.autoSync.status in setOf(com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.FAILED, com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.PARTIAL)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BoothButton(onOpenBooth: () -> Unit) {
    OutlinedButton(onClick = onOpenBooth, modifier = Modifier.fillMaxWidth().testTag("kintone_open_booth")) {
        Text(stringResource(R.string.kintone_get_plugin_booth))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.kintone_open_external), Modifier.padding(start = AppSpacing.small).size(18.dp))
    }
}

@Composable
private fun KintoneAutoSyncSection(state: KintoneUiState, onSync: () -> Unit, onVerify: () -> Unit, onAutoSyncChange: (Boolean) -> Unit, onPhotoAutoSyncChange: (Boolean) -> Unit, onConfirmEnable: () -> Unit) {
    val showDetails = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val showSyncNotes = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small), modifier = Modifier.fillMaxWidth().testTag("kintone_sync_section")) {
        Text(stringResource(R.string.kintone_sync_settings), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().testTag("kintone_auto_sync"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Rounded.Devices, null, Modifier.padding(end = AppSpacing.medium))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.kintone_automatic_sync), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.kintone_auto_sync_detail), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.autoSync.enabled, enabled = state.canUseKintone, onCheckedChange = { if (it) onConfirmEnable() else onAutoSyncChange(false) }, modifier = Modifier.testTag("kintone_auto_sync_switch"))
        }
        Row(Modifier.fillMaxWidth().testTag("kintone_photo_auto_sync"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Rounded.Photo, null, Modifier.padding(end = AppSpacing.medium))
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.kintone_sync_photos), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.kintone_photo_data_note), style = MaterialTheme.typography.bodySmall) }
            Switch(checked = state.autoSync.photoEnabled, enabled = state.canUseKintone && state.autoSync.enabled, onCheckedChange = onPhotoAutoSyncChange, modifier = Modifier.testTag("kintone_photo_auto_sync_switch"))
        }
        if (state.autoSync.status in setOf(com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.FAILED, com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.PARTIAL)) {
            Text(pluralStringResource(R.plurals.kintone_failed_count, state.autoSync.failureCount, state.autoSync.failureCount), color = MaterialTheme.colorScheme.error)
            if (showDetails.value) {
                state.autoSync.lastUserMessage?.let { Text(stringResource(R.string.kintone_error_reason, kintoneUserMessage(it)), modifier = Modifier.testTag("kintone_safe_error_message")) }
                state.autoSync.lastKintoneErrorCode?.let { Text(stringResource(R.string.kintone_error_code, it), modifier = Modifier.testTag("kintone_error_code")) }
                state.autoSync.lastHttpStatus?.let { Text(stringResource(R.string.kintone_http_status, it)) }
                state.autoSync.lastErrorPath?.let { Text(stringResource(R.string.kintone_error_field, it), modifier = Modifier.testTag("kintone_error_field")) }
                state.autoSync.lastErrorDetail?.let { Text(stringResource(R.string.kintone_error_detail, stringResource(R.string.kintone_validation_detail)), modifier = Modifier.testTag("kintone_error_detail")) }
                state.autoSync.lastFailedRecordIndex?.let { Text(stringResource(R.string.kintone_record_index, it + 1)) }
            }
            if (state.autoSync.requiresAttention) Text(stringResource(R.string.kintone_attention_required), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                TextButton(onClick = { showDetails.value = !showDetails.value }, modifier = Modifier.testTag("kintone_error_details")) { Text(stringResource(R.string.kintone_view_details)) }
                TextButton(onClick = onSync, modifier = Modifier.testTag("kintone_retry_failed")) { Text(stringResource(R.string.kintone_retry_failed)) }
                TextButton(onClick = onVerify, modifier = Modifier.testTag("kintone_check_connection")) { Text(stringResource(R.string.kintone_check_connection)) }
            }
        }
        TextButton(onClick = { showSyncNotes.value = !showSyncNotes.value }, modifier = Modifier.testTag("kintone_sync_notes")) {
            Icon(if (showSyncNotes.value) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            Text(stringResource(R.string.kintone_sync_details))
        }
        if (showSyncNotes.value) Card(Modifier.fillMaxWidth().testTag("kintone_sync_notes_content")) {
            Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(stringResource(R.string.kintone_sync_note_order))
                Text(stringResource(R.string.kintone_sync_note_replace))
                Text(stringResource(R.string.kintone_sync_note_scope))
                Text(stringResource(R.string.kintone_sync_note_deletion))
            }
        }
        state.syncPreview?.let { preview ->
            Text(stringResource(R.string.kintone_preview), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.kintone_preview_summary, preview.total, preview.valid))
            Text(stringResource(R.string.kintone_photo_preview, preview.photoDeviceCount, preview.photoCount))
            if (preview.errors.isNotEmpty()) Text(pluralStringResource(R.plurals.kintone_error_count, preview.errors.size, preview.errors.size), color = MaterialTheme.colorScheme.error)
            if (preview.warnings.isNotEmpty()) Text(pluralStringResource(R.plurals.kintone_warning_count, preview.warnings.size, preview.warnings.size), color = MaterialTheme.colorScheme.tertiary)
        }
        val hasSendableTargets = state.syncPreview?.valid?.let { it > 0 } ?: true
        Button(
            onClick = onSync,
            enabled = state.connection != null && state.canUseKintone && hasSendableTargets && state.operation !is OperationState.Running,
            modifier = Modifier.fillMaxWidth().testTag("kintone_sync"),
        ) { Icon(Icons.Rounded.Sync, null); Text(stringResource(R.string.kintone_sync_now), Modifier.padding(start = AppSpacing.small)) }
    }
}

@Composable
private fun syncStatusLabel(status: com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus) = stringResource(when (status) {
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.NEVER -> R.string.kintone_never
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.WAITING -> R.string.kintone_waiting
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.RUNNING -> R.string.kintone_syncing
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.SUCCESS -> R.string.kintone_sync_success
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.NO_TARGETS -> R.string.kintone_no_targets
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.PARTIAL -> R.string.kintone_partial_failure
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.FAILED -> R.string.kintone_sync_failed
})

@Composable
private fun KintoneSyncSection(state: KintoneUiState, onSync: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small), modifier = Modifier.testTag("kintone_sync_section")) {
        state.syncPreview?.let { preview ->
            Text(stringResource(R.string.kintone_preview), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.kintone_preview_summary, preview.total, preview.valid))
            if (preview.errors.isNotEmpty()) Text(pluralStringResource(R.plurals.kintone_error_count, preview.errors.size, preview.errors.size), color = MaterialTheme.colorScheme.error)
            if (preview.warnings.isNotEmpty()) Text(pluralStringResource(R.plurals.kintone_warning_count, preview.warnings.size, preview.warnings.size), color = MaterialTheme.colorScheme.tertiary)
        }
        val running = state.operation is OperationState.Running
        Button(onClick = onSync, enabled = !running, modifier = Modifier.fillMaxWidth().testTag("kintone_sync")) {
            Text(stringResource(if (state.syncPreview == null) R.string.kintone_show_preview else R.string.kintone_sync_to_kintone))
        }
        if (state.syncPreview?.errors?.isNotEmpty() == true) Text(stringResource(R.string.kintone_invalid_rows), color = MaterialTheme.colorScheme.error)
    }
}

private fun maskDomain(domain: String): String {
    val parts = domain.split('.')
    return if (parts.size < 3) domain else parts.first().take(2) + "***." + parts.drop(1).joinToString(".")
}

@Composable
private fun kintoneVerificationMessage(key: String): String = stringResource(when (key) {
    "PLUGIN_VERSION_INVALID" -> R.string.kintone_warning_plugin_version
    "ISSUED_AT_INVALID" -> R.string.kintone_warning_issued_at
    "NONCE_INVALID" -> R.string.kintone_warning_nonce
    "UNIQUE_SETTING_UNVERIFIED" -> R.string.kintone_warning_unique
    "UPDATED_AT_DEFAULT_INVALID" -> R.string.kintone_warning_updated_at
    "PHOTO_SYNC_UNAVAILABLE" -> R.string.kintone_warning_photo_unavailable
    "EXTRA_FIELDS_IGNORED" -> R.string.kintone_info_extra_fields
    else -> R.string.kintone_warning_generic
})

@Composable
private fun kintoneUserMessage(key: String): String = stringResource(when (key) {
    KintoneErrorMessages.AUTH_INVALID -> R.string.kintone_error_auth_invalid
    KintoneErrorMessages.AUTH_PERMISSION -> R.string.kintone_error_auth_permission
    KintoneErrorMessages.APP_NOT_FOUND -> R.string.kintone_error_app_not_found
    KintoneErrorMessages.RATE_LIMITED -> R.string.kintone_error_rate_limited
    KintoneErrorMessages.FIELD_MISMATCH -> R.string.kintone_error_field_mismatch
    KintoneErrorMessages.UPSERT_MISMATCH -> R.string.kintone_error_upsert_mismatch
    KintoneErrorMessages.SCHEMA_CHANGED -> R.string.kintone_error_schema_changed
    KintoneErrorMessages.TIMEOUT -> R.string.kintone_error_timeout
    KintoneErrorMessages.PHOTO_UPLOAD_TIMEOUT -> R.string.kintone_error_photo_timeout
    KintoneErrorMessages.PHOTO_UNREADABLE -> R.string.kintone_error_photo_unreadable
    KintoneErrorMessages.PHOTO_SYNC_FAILED -> R.string.kintone_error_photo_sync
    KintoneErrorMessages.NETWORK_UNAVAILABLE -> R.string.kintone_error_network
    else -> R.string.kintone_error_generic
})

@Composable
fun KintonePluginInfoScreen(onBack: () -> Unit, onOpenBooth: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Header(stringResource(R.string.kintone_plugin_title), onBack)
        BenefitList(listOf(
            stringResource(R.string.kintone_plugin_requires_pro),
            stringResource(R.string.kintone_plugin_separate_purchase),
            stringResource(R.string.kintone_plugin_not_standalone),
            stringResource(R.string.kintone_plugin_qr),
            stringResource(R.string.kintone_plugin_no_manual_credentials),
        ))
        BoothButton(onOpenBooth)
    }
}

@Composable private fun Header(title: String, onBack: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigation_back)) }
    Text(title, style = MaterialTheme.typography.headlineMedium)
}

@Composable private fun BenefitList(items: List<String>) = Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
    items.forEach { Text(stringResource(R.string.bullet_item, it)) }
}

@Composable private fun RestoreButton(state: BillingUiState, onRestore: () -> Unit) {
    TextButton(onClick = onRestore, enabled = !state.restoring && !state.purchasing, modifier = Modifier.fillMaxWidth().testTag("restore_purchase")) {
        if (state.restoring) { CircularProgressIndicator(); Text(stringResource(R.string.pro_restoring)) } else Text(stringResource(R.string.pro_restore_purchase))
    }
}
