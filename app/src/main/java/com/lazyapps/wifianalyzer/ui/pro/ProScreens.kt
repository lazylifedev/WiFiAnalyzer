package com.lazyapps.wifianalyzer.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.lazyapps.wifianalyzer.billing.BillingUiState
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.billing.ProEntitlementState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.kintone.KintoneUiState
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
) {
    val confirmDisconnect = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val confirmAutoSync = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Header("kintone連携", onBack)
        if (!access.canUseKintone) {
            Text("Pro版で利用できます。", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onOpenPro, modifier = Modifier.fillMaxWidth().testTag("kintone_open_pro")) { Text("Pro版について") }
        } else {
            Text(if (state.connection == null) "状態：未接続" else "状態：接続済み", style = MaterialTheme.typography.titleLarge)
            state.connection?.let { connection ->
                KintoneAutoSyncSection(state, onSync, onAutoSyncChange) { confirmAutoSync.value = true }
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
            state.errorCode?.let { Text("接続できませんでした（${it.name}）", color = MaterialTheme.colorScheme.error) }
            (state.operation as? OperationState.Success)?.let { Text("処理が完了しました", color = MaterialTheme.colorScheme.primary) }
            state.message?.let { Text(it, modifier = Modifier.testTag("kintone_message")) }
            state.syncResult?.let { result ->
                Card(Modifier.fillMaxWidth().testTag("kintone_sync_result")) { Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text("同期結果", style = MaterialTheme.typography.titleLarge)
                    Text("対象 ${result.total}件 / 成功 ${result.succeeded}件 / 失敗 ${result.failed}件 / 未送信 ${result.skipped}件")
                    if (result.failed > 0) Text("失敗した機器だけ再送できます。自動再試行は行いません。", color = MaterialTheme.colorScheme.error)
                } }
            }
            OutlinedButton(onClick = onPluginInfo, modifier = Modifier.fillMaxWidth()) { Text("プラグインについて") }
        }
    }
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
private fun KintoneAutoSyncSection(state: KintoneUiState, onSync: () -> Unit, onAutoSyncChange: (Boolean) -> Unit, onConfirmEnable: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small), modifier = Modifier.fillMaxWidth().testTag("kintone_sync_section")) {
        Row(Modifier.fillMaxWidth().testTag("kintone_auto_sync"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("登録機器を自動同期", style = MaterialTheme.typography.titleMedium)
                Text("登録・編集された機器をkintoneへ自動送信します", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.autoSync.enabled, enabled = state.canUseKintone, onCheckedChange = { if (it) onConfirmEnable() else onAutoSyncChange(false) }, modifier = Modifier.testTag("kintone_auto_sync_switch"))
        }
        Text("最終同期日時: " + if (state.autoSync.lastFinishedAt > 0) DateFormat.getDateTimeInstance().format(Date(state.autoSync.lastFinishedAt)) else "未実行")
        Text("最終同期結果: ${state.autoSync.status.name}")
        Text("現在、写真は同期されません。複数BSSIDは主BSSIDのみ同期されます。")
        Text("Androidで削除した機器のkintone反映は、現在未対応です。")
        Button(onClick = onSync, enabled = state.operation !is OperationState.Running, modifier = Modifier.fillMaxWidth().testTag("kintone_sync")) { Text("今すぐ同期") }
    }
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
