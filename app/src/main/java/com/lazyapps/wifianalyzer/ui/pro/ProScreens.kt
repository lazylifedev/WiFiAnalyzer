package com.lazyapps.wifianalyzer.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.lazyapps.wifianalyzer.billing.BillingUiState
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.billing.ProEntitlementState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.kintone.KintoneUiState
import com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceOption
import com.lazyapps.wifianalyzer.ui.operation.OperationState
import java.text.DateFormat
import java.util.Date

@Composable
fun ProScreen(state: BillingUiState, onBack: () -> Unit, onPurchase: () -> Unit, onRestore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
    ) {
        Header("Pro版", onBack)
        when (state.entitlement) {
            ProEntitlementState.Pro -> {
                Text("Pro版をご利用中です", style = MaterialTheme.typography.headlineSmall)
                BenefitList(listOf("購入済み", "広告なし", "すべてのPro機能を利用可能"))
            }
            ProEntitlementState.Pending -> {
                Text("購入手続きが保留されています。", style = MaterialTheme.typography.headlineSmall)
                Text("Google Playで支払いが完了すると利用可能になります。")
                RestoreButton(state, onRestore)
            }
            else -> {
                Text("無料版の制限を解除", style = MaterialTheme.typography.headlineSmall)
                BenefitList(listOf("広告なし", "機能制限を解除", "kintone連携", "今後追加されるPro機能"))
                state.product?.let { Text("価格: ${it.formattedPrice}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("pro_price")) }
                if (state.connection == com.lazyapps.wifianalyzer.billing.BillingConnectionState.CONNECTING) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        CircularProgressIndicator(); Text("商品情報を取得中")
                    }
                } else if (state.product == null) {
                    Text("Playストアから商品情報を取得できません。", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onPurchase,
                    enabled = state.canPurchase,
                    modifier = Modifier.fillMaxWidth().testTag("purchase_pro"),
                ) { if (state.purchasing) CircularProgressIndicator() else Text("Pro版を購入") }
                RestoreButton(state, onRestore)
            }
        }
    }
}

@Composable
fun KintoneScreen(
    access: FeatureAccessPolicy,
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    onPluginInfo: () -> Unit,
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
    onManualWorkspacesSelected: (Set<Long>) -> Unit = {},
) {
    val confirmDisconnect = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val confirmAutoSync = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val showWorkspaceSelector = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val workspaceDraft = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Set<Long>>(emptySet()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Header("kintone連携", onBack)
        if (!access.canUseKintone) {
            Text("Pro版で利用できます。", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onOpenPro, modifier = Modifier.fillMaxWidth().testTag("kintone_open_pro")) { Text("Pro版について") }
        } else {
            KintoneDashboard(
                state = state,
                onChangeTargets = { workspaceDraft.value = state.selectedWorkspaceIds; showWorkspaceSelector.value = true },
                onSync = onSync,
                onCancelSync = onCancelSync,
                onWorkspaceSelected = onWorkspaceSelected,
                onScanQr = onScanQr,
                onVerify = onVerify,
                onDisconnect = { confirmDisconnect.value = true },
                onAutoSyncChange = onAutoSyncChange,
                onPhotoAutoSyncChange = onPhotoAutoSyncChange,
                onConfirmAutoSync = { confirmAutoSync.value = true },
            )
            return@Column
            Text("同期対象ワークスペース", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showWorkspaceSelector.value = true }, modifier = Modifier.fillMaxWidth().testTag("kintone_workspace_selector")) { Text("${state.workspaceName} ▼") }
            Text("この画面の同期・接続操作は、選択したワークスペースに対して行われます。", style = MaterialTheme.typography.bodySmall)
            if (state.appWorkspaceId > 0 && state.appWorkspaceId != state.workspaceId) Text("アプリで現在使用中のワークスペースとは異なる同期対象を選択しています。", color = MaterialTheme.colorScheme.tertiary)
            Text(if (state.connection == null) "状態：未接続" else "状態：接続済み", style = MaterialTheme.typography.titleLarge)
            state.errorCode?.let {
                val text = when (state.failureContext) {
                    com.lazyapps.wifianalyzer.ui.kintone.KintoneFailureContext.QR -> "QRコードの内容を確認できませんでした"
                    com.lazyapps.wifianalyzer.ui.kintone.KintoneFailureContext.SYNC -> "kintoneへ同期できませんでした"
                    else -> "接続できませんでした"
                }
                Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("kintone_error_message"))
            }
            state.message?.let { Text(it, modifier = Modifier.testTag("kintone_message")) }
            state.connection?.let { connection ->
                KintoneAutoSyncSection(state, onSync, onVerify, onAutoSyncChange, onPhotoAutoSyncChange) { confirmAutoSync.value = true }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text("ドメイン：${maskDomain(connection.domain)}")
                    Text("アプリID：${connection.appId}")
                    Text("接続先ワークスペース：${state.workspaceName}")
                    Text("最終接続確認：${DateFormat.getDateTimeInstance().format(Date(connection.lastVerifiedAt))}")
                } }
                Button(onClick = onVerify, modifier = Modifier.fillMaxWidth().testTag("kintone_verify")) { Text("接続を確認") }
                OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth().testTag("kintone_rescan_qr")) { Text("QRコードを再読取") }
                TextButton(onClick = { confirmDisconnect.value = true }, modifier = Modifier.fillMaxWidth().testTag("kintone_disconnect")) { Text("連携を解除") }
            } ?: Button(onClick = onScanQr, modifier = Modifier.fillMaxWidth().testTag("kintone_scan_qr")) { Text("QRコードを読み取る") }
            if (state.operation is OperationState.Running) {
                val running = state.operation as OperationState.Running
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text("処理中…", Modifier.padding(start = AppSpacing.small)) }
                when (val progress = running.progress) {
                    is com.lazyapps.wifianalyzer.ui.operation.OperationProgress.Count -> {
                        LinearProgressIndicator(progress = { progress.fraction }, Modifier.fillMaxWidth())
                        Text("同期中 ${progress.current}/${progress.total}")
                    }
                    else -> Unit
                }
                if (running.cancellable) TextButton(onClick = onCancelSync, modifier = Modifier.testTag("kintone_cancel_sync")) { Text("キャンセル") }
            }
            (state.operation as? OperationState.Success)?.let { Text("処理が完了しました", color = MaterialTheme.colorScheme.primary) }
            state.syncResult?.let { result ->
                Card(Modifier.fillMaxWidth().testTag("kintone_sync_result")) { Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text("同期結果", style = MaterialTheme.typography.titleLarge)
                    Text("対象 ${result.total}件 / 成功 ${result.succeeded}件 / 失敗 ${result.failed}件 / 未送信 ${result.skipped}件")
                    if (result.failed > 0) Text("失敗した機器だけ再送できます。自動再試行は行いません。", color = MaterialTheme.colorScheme.error)
                    result.batches.firstOrNull { it.validationErrors.isNotEmpty() }?.let { failure ->
                        val detail = failure.validationErrors.first()
                        Text("エラーフィールド：${detail.path}", modifier = Modifier.testTag("kintone_sync_error_field"))
                        Text("内容：${detail.messages.joinToString(" / ")}", modifier = Modifier.testTag("kintone_sync_error_detail"))
                        failure.recordIndex?.let { Text("対象：送信データの${it + 1}件目") }
                    }
                } }
            }
            OutlinedButton(onClick = onPluginInfo, modifier = Modifier.fillMaxWidth()) { Text("プラグインについて") }
        }
    }
    if (showWorkspaceSelector.value) AlertDialog(
        onDismissRequest = { showWorkspaceSelector.value = false },
        title = { Text("同期対象ワークスペース") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            TextButton(onClick = { workspaceDraft.value = state.workspaces.mapTo(linkedSetOf()) { it.id } }) { Text("すべて選択") }
            state.workspaces.forEach { option ->
                val checked = option.id in workspaceDraft.value
                Row(Modifier.fillMaxWidth().clickable {
                    workspaceDraft.value = if (checked) workspaceDraft.value - option.id else workspaceDraft.value + option.id
                }.testTag("kintone_workspace_${option.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { value -> workspaceDraft.value = if (value) workspaceDraft.value + option.id else workspaceDraft.value - option.id })
                    Column(Modifier.weight(1f)) {
                        Text(option.name)
                        Text("登録機器 ${option.deviceCount}台・${if (option.connected) "接続済み" else "未接続"}・自動同期${if (option.autoSyncEnabled) "ON" else "OFF"}・写真${if (option.photoAutoSyncEnabled) "ON" else "OFF"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } },
        confirmButton = { Button(onClick = { onManualWorkspacesSelected(workspaceDraft.value); showWorkspaceSelector.value = false }, enabled = workspaceDraft.value.isNotEmpty(), modifier = Modifier.testTag("kintone_workspace_confirm")) { Text("選択を確定") } },
        dismissButton = { TextButton(onClick = { showWorkspaceSelector.value = false }) { Text("キャンセル") } },
    )
    state.pending?.let { pending -> AlertDialog(
        onDismissRequest = onCancelPending,
        title = { Text("接続内容を確認") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("ドメイン：${maskDomain(pending.payload.domain)}")
            Text("アプリID：${pending.payload.appId}")
            Text("pluginVersion：${pending.payload.pluginVersion}")
            Text("templateVersion：${pending.payload.templateVersion}")
            Text("fieldSchemaVersion：${pending.payload.fieldSchemaVersion}")
            Text("接続先ワークスペース：${pending.workspaceName}")
            Text("APIトークン：設定済み")
            if (pending.verification.warnings.isEmpty()) Text("フィールド検査：確認済み") else pending.verification.warnings.forEach { Text("警告：$it", color = MaterialTheme.colorScheme.error) }
            pending.verification.information.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (pending.duplicateTarget) Text("警告：別のワークスペースも同じkintoneアプリに接続しています", color = MaterialTheme.colorScheme.error)
            if (pending.replacing) Text("保存後に既存の接続設定を置き換えます")
        } },
        confirmButton = { Button(onClick = onConfirm) { Text("この内容で接続") } },
        dismissButton = { TextButton(onClick = onCancelPending) { Text("キャンセル") } },
    ) }
    if (confirmDisconnect.value) AlertDialog(
        onDismissRequest = { confirmDisconnect.value = false },
        title = { Text("kintone連携を解除しますか") },
        text = { Text("Android内の登録機器とkintone側レコードは変更しません。端末内の接続情報とAPIトークンを削除します。kintone側のAPIトークンは無効化されません。") },
        confirmButton = { Button(onClick = { confirmDisconnect.value = false; onDisconnect() }) { Text("連携を解除") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect.value = false }) { Text("キャンセル") } },
    )
    if (confirmAutoSync.value) AlertDialog(
        onDismissRequest = { confirmAutoSync.value = false },
        title = { Text("自動同期を有効にしますか") },
        text = { Text("このワークスペースの登録機器をkintoneへ自動同期します。Android側の内容でkintoneのレコードが追加・更新されます。") },
        confirmButton = { Button(onClick = { confirmAutoSync.value = false; onAutoSyncChange(true) }) { Text("有効にする") } },
        dismissButton = { TextButton(onClick = { confirmAutoSync.value = false }) { Text("キャンセル") } },
    )
}

@Composable
private fun KintoneDashboard(
    state: KintoneUiState,
    onChangeTargets: () -> Unit,
    onSync: () -> Unit,
    onCancelSync: () -> Unit,
    onWorkspaceSelected: (KintoneWorkspaceOption) -> Unit,
    onScanQr: () -> Unit,
    onVerify: () -> Unit,
    onDisconnect: () -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onPhotoAutoSyncChange: (Boolean) -> Unit,
    onConfirmAutoSync: () -> Unit,
) {
    val autoExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val connectionsExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val infoDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val selected = state.workspaces.filter { it.id in state.selectedWorkspaceIds }
    val disconnected = selected.count { !it.connected }
    val connectedSelected = selected.any { it.connected }
    val totalDevices = selected.sumOf { it.deviceCount }

    Card(Modifier.fillMaxWidth().clickable(onClick = onChangeTargets).testTag("kintone_workspace_selector")) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("同期対象", style = MaterialTheme.typography.titleMedium)
                Text(if (selected.size == 1) selected.first().name else "${selected.size}ワークスペース", style = MaterialTheme.typography.titleLarge)
                Text("登録機器 ${totalDevices}台" + if (disconnected > 0) "・${disconnected}件未接続" else "", style = MaterialTheme.typography.bodySmall)
            }
            Text("変更", color = MaterialTheme.colorScheme.primary)
        }
    }

    Card(Modifier.fillMaxWidth().testTag("kintone_status_summary")) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("状態", style = MaterialTheme.typography.titleMedium)
            val multi = state.multiSyncResult
            if (multi != null) {
                Text(when (multi.status) {
                    com.lazyapps.wifianalyzer.kintone.KintoneMultiSyncStatus.SUCCESS -> "前回の同期は成功しました"
                    com.lazyapps.wifianalyzer.kintone.KintoneMultiSyncStatus.PARTIAL -> "前回の同期は一部失敗しました"
                    com.lazyapps.wifianalyzer.kintone.KintoneMultiSyncStatus.FAILED -> "前回の同期に失敗しました"
                    com.lazyapps.wifianalyzer.kintone.KintoneMultiSyncStatus.NO_TARGETS -> "前回の同期は対象がありませんでした"
                    com.lazyapps.wifianalyzer.kintone.KintoneMultiSyncStatus.CANCELLED -> "前回の同期をキャンセルしました"
                }, style = MaterialTheme.typography.titleMedium)
                Text("${multi.workspaces.size}ワークスペース・${multi.succeededDevices}台を同期", style = MaterialTheme.typography.bodySmall)
                multi.workspaces.forEach { item ->
                    Text("${workspaceResultMark(item.status)} ${item.workspaceName}  ${workspaceResultLabel(item)}", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(if (state.autoSync.lastFinishedAt > 0) "前回の同期：${syncStatusLabel(state.autoSync.status)}" else "まだ同期していません")
                if (state.autoSync.lastFinishedAt > 0) Text(DateFormat.getDateTimeInstance().format(Date(state.autoSync.lastFinishedAt)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (state.operation is OperationState.Running) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(state.message ?: "同期中 ${state.syncingWorkspaceIndex}/${selected.size}ワークスペース")
        TextButton(onClick = onCancelSync, modifier = Modifier.testTag("kintone_cancel_sync")) { Text("キャンセル") }
    } else {
        Button(onClick = onSync, enabled = connectedSelected && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("kintone_sync")) { Text("今すぐ同期") }
        Text(if (connectedSelected) "選択中の${selected.size}ワークスペースを同期します" else "接続済みのワークスペースがありません", style = MaterialTheme.typography.bodySmall)
    }

    SummaryExpandableCard("自動同期", "${state.workspaces.count { it.autoSyncEnabled }}ワークスペースでON", autoExpanded.value, { autoExpanded.value = !autoExpanded.value }, "kintone_auto_sync_summary") {
        state.workspaces.forEach { option ->
            WorkspaceSettingRow(option, onWorkspaceSelected) {
                if (option.id == state.workspaceId && option.connected) {
                    Row(Modifier.fillMaxWidth().testTag("kintone_auto_sync"), horizontalArrangement = Arrangement.SpaceBetween) { Text("機器の自動同期"); Switch(state.autoSync.enabled, onCheckedChange = { if (it) onConfirmAutoSync() else onAutoSyncChange(false) }, modifier = Modifier.testTag("kintone_auto_sync_switch")) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("写真の自動同期"); Switch(state.autoSync.photoEnabled, enabled = state.autoSync.enabled, onCheckedChange = onPhotoAutoSyncChange, modifier = Modifier.testTag("kintone_photo_auto_sync_switch")) }
                }
            }
        }
    }
    SummaryExpandableCard("接続設定", "${state.workspaces.count { it.connected }}/${state.workspaces.size}ワークスペース接続済み", connectionsExpanded.value, { connectionsExpanded.value = !connectionsExpanded.value }, "kintone_connection_summary") {
        state.workspaces.forEach { option ->
            WorkspaceSettingRow(option, onWorkspaceSelected) {
                if (option.id == state.workspaceId) Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    TextButton(onClick = onScanQr) { Text(if (option.connected) "再接続" else "接続") }
                    if (option.connected) { TextButton(onClick = onVerify) { Text("接続確認") }; TextButton(onClick = onDisconnect) { Text("接続解除") } }
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth().clickable { infoDialog.value = "写真同期" }.testTag("kintone_photo_info")) { Row(Modifier.fillMaxWidth().padding(AppSpacing.medium), horizontalArrangement = Arrangement.SpaceBetween) { Text("写真同期について"); Text("確認", color = MaterialTheme.colorScheme.primary) } }
    Card(Modifier.fillMaxWidth().clickable { infoDialog.value = "連携仕様" }.testTag("kintone_link_spec")) { Row(Modifier.fillMaxWidth().padding(AppSpacing.medium), horizontalArrangement = Arrangement.SpaceBetween) { Text("連携仕様"); Text("確認", color = MaterialTheme.colorScheme.primary) } }
    infoDialog.value?.let { title -> AlertDialog(onDismissRequest = { infoDialog.value = null }, title = { Text(title) }, text = { Text(if (title == "写真同期") "Androidの並び順で同期します。キャプションとメイン写真設定は保存しません。Android側で写真を変更するとkintoneの写真を置き換えます。写真の自動同期は通信量が増え、kintone側で手動追加した写真が消える可能性があります。" else "複数BSSIDは主BSSIDのみ同期します。Androidで削除した機器のkintone反映は現在未対応です。") }, confirmButton = { TextButton(onClick = { infoDialog.value = null }) { Text("閉じる") } }) }
}

@Composable
private fun SummaryExpandableCard(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag(tag)) { Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(summary, style = MaterialTheme.typography.bodySmall) }; Text(if (expanded) "閉じる" else "展開", color = MaterialTheme.colorScheme.primary) }
        if (expanded) { HorizontalDivider(); content() }
    } }
}

@Composable
private fun WorkspaceSettingRow(option: KintoneWorkspaceOption, onSelect: (KintoneWorkspaceOption) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = AppSpacing.small)) { Text(option.name, style = MaterialTheme.typography.titleSmall); Text(if (option.connected) "接続済み" else "未接続", style = MaterialTheme.typography.bodySmall); content() }
}

private fun workspaceResultMark(status: com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus) = when (status) {
    com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.SUCCESS -> "✓"
    com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.PARTIAL -> "△"
    com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.FAILED -> "×"
    else -> "－"
}

private fun workspaceResultLabel(item: com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncResult) = when (item.status) {
    com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.SUCCESS -> "${item.result?.succeeded ?: 0}台成功"
    com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.PARTIAL -> "${item.result?.succeeded ?: 0}台成功／${item.result?.failed ?: 0}台失敗"
    com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.NOT_CONNECTED -> "未接続のためスキップ"
    com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.NO_TARGETS -> "対象なし"
    com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.CANCELLED -> "キャンセル"
    else -> "失敗"
}

@Composable
private fun KintoneAutoSyncSection(state: KintoneUiState, onSync: () -> Unit, onVerify: () -> Unit, onAutoSyncChange: (Boolean) -> Unit, onPhotoAutoSyncChange: (Boolean) -> Unit, onConfirmEnable: () -> Unit) {
    val showDetails = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small), modifier = Modifier.fillMaxWidth().testTag("kintone_sync_section")) {
        Row(Modifier.fillMaxWidth().testTag("kintone_auto_sync"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("登録機器を自動同期", style = MaterialTheme.typography.titleMedium)
                Text("登録・編集された機器をkintoneへ自動送信します", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.autoSync.enabled, enabled = state.canUseKintone, onCheckedChange = { if (it) onConfirmEnable() else onAutoSyncChange(false) }, modifier = Modifier.testTag("kintone_auto_sync_switch"))
        }
        Row(Modifier.fillMaxWidth().testTag("kintone_photo_auto_sync"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("写真も自動同期", style = MaterialTheme.typography.titleMedium); Text("写真の自動同期は通信量が増える場合があります。", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = state.autoSync.photoEnabled, enabled = state.canUseKintone && state.autoSync.enabled, onCheckedChange = onPhotoAutoSyncChange, modifier = Modifier.testTag("kintone_photo_auto_sync_switch"))
        }
        Text("最終同期日時: " + if (state.autoSync.lastFinishedAt > 0) DateFormat.getDateTimeInstance().format(Date(state.autoSync.lastFinishedAt)) else "未実行")
        Text("最終同期結果: ${syncStatusLabel(state.autoSync.status)}")
        if (state.autoSync.status in setOf(com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.FAILED, com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.PARTIAL)) {
            Text("${state.autoSync.failureCount}件を送信できませんでした。", color = MaterialTheme.colorScheme.error)
            if (showDetails.value) {
                state.autoSync.lastUserMessage?.let { Text("原因：\n$it", modifier = Modifier.testTag("kintone_safe_error_message")) }
                state.autoSync.lastKintoneErrorCode?.let { Text("エラーコード：\n$it", modifier = Modifier.testTag("kintone_error_code")) }
                state.autoSync.lastHttpStatus?.let { Text("HTTPステータス：$it") }
                state.autoSync.lastErrorPath?.let { Text("エラーフィールド：\n$it", modifier = Modifier.testTag("kintone_error_field")) }
                state.autoSync.lastErrorDetail?.let { Text("内容：\n$it", modifier = Modifier.testTag("kintone_error_detail")) }
                state.autoSync.lastFailedRecordIndex?.let { Text("対象：送信データの${it + 1}件目") }
            }
            if (state.autoSync.requiresAttention) Text("確認が必要", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                TextButton(onClick = { showDetails.value = !showDetails.value }, modifier = Modifier.testTag("kintone_error_details")) { Text("詳細を確認") }
                TextButton(onClick = onSync, modifier = Modifier.testTag("kintone_retry_failed")) { Text("失敗分を再送") }
                TextButton(onClick = onVerify, modifier = Modifier.testTag("kintone_check_connection")) { Text("接続を確認") }
            }
        }
        Text("写真はAndroid側の並び順で同期されます。キャプションとメイン写真設定はkintoneには保存されません。")
        Text("Android側の写真を変更して同期すると、kintoneの写真はAndroid側の写真で置き換わります。")
        Text("選択した機器の写真も同期します。複数BSSIDは主BSSIDのみ同期されます。")
        Text("Androidで削除した機器のkintone反映は、現在未対応です。")
        state.syncPreview?.let { preview ->
            Text("送信前レビュー", style = MaterialTheme.typography.titleMedium)
            Text("対象 ${preview.total}件 / 送信可能 ${preview.valid}件")
            Text("写真同期対象：${preview.photoDeviceCount}台 / 写真アップロード：${preview.photoCount}枚")
            if (preview.errors.isNotEmpty()) Text("エラー ${preview.errors.size}件", color = MaterialTheme.colorScheme.error)
            if (preview.warnings.isNotEmpty()) Text("警告 ${preview.warnings.size}件", color = MaterialTheme.colorScheme.tertiary)
        }
        val hasSendableTargets = state.syncPreview?.valid?.let { it > 0 } ?: true
        Button(
            onClick = onSync,
            enabled = state.connection != null && state.canUseKintone && hasSendableTargets && state.operation !is OperationState.Running,
            modifier = Modifier.fillMaxWidth().testTag("kintone_sync"),
        ) { Text("今すぐ同期") }
    }
}

private fun syncStatusLabel(status: com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus) = when (status) {
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.NEVER -> "未実行"
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.WAITING -> "待機中"
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.RUNNING -> "同期中"
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.SUCCESS -> "同期成功"
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.NO_TARGETS -> "対象なし"
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.PARTIAL -> "一部失敗"
    com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus.FAILED -> "同期失敗"
}

@Composable
private fun KintoneSyncSection(state: KintoneUiState, onSync: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small), modifier = Modifier.testTag("kintone_sync_section")) {
        state.syncPreview?.let { preview ->
            Text("送信前レビュー", style = MaterialTheme.typography.titleMedium)
            Text("対象 ${preview.total}件 / 送信可能 ${preview.valid}件")
            if (preview.errors.isNotEmpty()) Text("エラー ${preview.errors.size}件", color = MaterialTheme.colorScheme.error)
            if (preview.warnings.isNotEmpty()) Text("警告 ${preview.warnings.size}件", color = MaterialTheme.colorScheme.tertiary)
        }
        val running = state.operation is OperationState.Running
        Button(onClick = onSync, enabled = !running, modifier = Modifier.fillMaxWidth().testTag("kintone_sync")) {
            Text(if (state.syncPreview == null) "送信前レビューを表示" else "kintoneへ同期")
        }
        if (state.syncPreview?.errors?.isNotEmpty() == true) Text("エラーのある行は選択して送信できません。", color = MaterialTheme.colorScheme.error)
    }
}

private fun maskDomain(domain: String): String {
    val parts = domain.split('.')
    return if (parts.size < 3) domain else parts.first().take(2) + "***." + parts.drop(1).joinToString(".")
}

@Composable
fun KintonePluginInfoScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Header("kintone連携プラグインについて", onBack)
        BenefitList(listOf(
            "kintone連携にはPro版が必要です。",
            "別売りのkintone連携プラグインが必要です。",
            "プラグイン単体では利用できません。",
            "専用QRコードで接続します。",
            "ドメイン、アプリID、APIトークンなどの手入力設定は行いません。",
        ))
    }
}

@Composable private fun Header(title: String, onBack: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") }
    Text(title, style = MaterialTheme.typography.headlineMedium)
}

@Composable private fun BenefitList(items: List<String>) = Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
    items.forEach { Text("• $it") }
}

@Composable private fun RestoreButton(state: BillingUiState, onRestore: () -> Unit) {
    TextButton(onClick = onRestore, enabled = !state.restoring && !state.purchasing, modifier = Modifier.fillMaxWidth().testTag("restore_purchase")) {
        if (state.restoring) { CircularProgressIndicator(); Text("復元中") } else Text("購入状態を復元")
    }
}
