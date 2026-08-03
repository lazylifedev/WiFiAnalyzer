package com.lazyapps.wifianalyzer.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

data class OnboardingPage(@StringRes val titleRes: Int, @StringRes val bodyRes: Int, val icon: ImageVector)

val onboardingPages = listOf(
    OnboardingPage(R.string.onboarding_wifi_title, R.string.onboarding_wifi_body, Icons.Rounded.Wifi),
    OnboardingPage(R.string.onboarding_devices_title, R.string.onboarding_devices_body, Icons.Rounded.Devices),
    OnboardingPage(R.string.onboarding_data_title, R.string.onboarding_data_body, Icons.Rounded.Backup),
    OnboardingPage(R.string.onboarding_privacy_title, R.string.onboarding_privacy_body, Icons.Rounded.PrivacyTip),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val item = onboardingPages[page]
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(AppSpacing.xLarge),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onComplete, modifier = Modifier.testTag("onboarding_skip")) { Text(stringResource(R.string.skip), color = MaterialTheme.colorScheme.primary) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(item.titleRes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(item.bodyRes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${page + 1} / ${onboardingPages.size}",
                modifier = Modifier.semantics { contentDescription = "${page + 1} / ${onboardingPages.size}" }.testTag("onboarding_indicator"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            if (page > 0) OutlinedButton(onClick = { page-- }, Modifier.weight(1f)) { Text(stringResource(R.string.back)) }
            Button(
                onClick = { if (page == onboardingPages.lastIndex) onComplete() else page++ },
                modifier = Modifier.weight(1f).testTag(if (page == onboardingPages.lastIndex) "onboarding_complete" else "onboarding_next"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text(stringResource(if (page == onboardingPages.lastIndex) R.string.onboarding_start else R.string.next)) }
        }
    }
}
