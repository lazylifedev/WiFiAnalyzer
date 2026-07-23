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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
) {
    val confirmDisconnect = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Header("kintone連携", onBack)
        if (!access.canUseKintone) {
            Text("Pro版で利用できます。", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onOpenPro, modifier = Modifier.fillMaxWidth().testTag("kintone_open_pro")) { Text("Pro版について") }
        } else {
            Text(if (state.connection == null) "状態：未接続" else "状態：接続済み", style = MaterialTheme.typography.titleLarge)
            state.connection?.let { connection ->
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
            if (state.operation is OperationState.Running) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text("確認中…", Modifier.padding(start = AppSpacing.small)) }
            state.errorCode?.let { Text("接続できませんでした（${it.name}）", color = MaterialTheme.colorScheme.error) }
            (state.operation as? OperationState.Success)?.let { Text("処理が完了しました", color = MaterialTheme.colorScheme.primary) }
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
