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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.lazyapps.wifianalyzer.billing.BillingUiState
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.billing.ProEntitlementState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

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
fun KintoneScreen(access: FeatureAccessPolicy, onBack: () -> Unit, onOpenPro: () -> Unit, onPluginInfo: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Header("kintone連携", onBack)
        if (!access.canUseKintone) {
            Text("Pro版で利用できます。", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onOpenPro, modifier = Modifier.fillMaxWidth().testTag("kintone_open_pro")) { Text("Pro版について") }
        } else {
            Text("kintone連携プラグインが必要です。", style = MaterialTheme.typography.titleLarge)
            Button(onClick = {}, modifier = Modifier.fillMaxWidth().testTag("kintone_scan_qr")) { Text("連携QRコードを読み取る（準備中）") }
            OutlinedButton(onClick = onPluginInfo, modifier = Modifier.fillMaxWidth()) { Text("プラグインについて") }
        }
    }
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
