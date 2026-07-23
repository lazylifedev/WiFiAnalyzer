package com.lazyapps.wifianalyzer.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

data class OnboardingPage(val title: String, val body: String, val icon: ImageVector)

val onboardingPages = listOf(
    OnboardingPage("Wi-Fiの状態を見える化", "周辺ネットワーク、チャネルの混雑、電波の強さを確認できます。", Icons.Rounded.Wifi),
    OnboardingPage("機器を登録して管理", "BSSIDで照合し、写真、グループ、メモと一緒に管理できます。", Icons.Rounded.Devices),
    OnboardingPage("データを安全に保管", "ZIPバックアップ、CSV入出力、PDF・印刷を利用できます。", Icons.Rounded.Backup),
    OnboardingPage("端末内で大切に扱います", "Wi-Fi情報と登録データは端末内で処理・保存します。共有やバックアップを選んだときだけ外部へ出ます。", Icons.Rounded.PrivacyTip),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val item = onboardingPages[page]
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.xLarge),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onComplete, modifier = Modifier.testTag("onboarding_skip")) { Text("スキップ") }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(item.body, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${page + 1} / ${onboardingPages.size}",
                modifier = Modifier.semantics { contentDescription = "${onboardingPages.size}ページ中${page + 1}ページ" }.testTag("onboarding_indicator"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            if (page > 0) OutlinedButton(onClick = { page-- }, Modifier.weight(1f)) { Text("戻る") }
            Button(
                onClick = { if (page == onboardingPages.lastIndex) onComplete() else page++ },
                modifier = Modifier.weight(1f).testTag(if (page == onboardingPages.lastIndex) "onboarding_complete" else "onboarding_next"),
            ) { Text(if (page == onboardingPages.lastIndex) "利用を開始" else "次へ") }
        }
    }
}
