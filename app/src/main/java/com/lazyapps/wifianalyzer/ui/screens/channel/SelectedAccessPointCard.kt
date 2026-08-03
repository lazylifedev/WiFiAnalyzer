package com.lazyapps.wifianalyzer.ui.screens.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.ui.components.RegisteredBadge
import com.lazyapps.wifianalyzer.ui.components.localizedLabel
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

@Composable
fun SelectedAccessPointCard(
    accessPoint: WifiAccessPoint,
    useFeet: Boolean,
    onOpen: () -> Unit,
    onRegister: (WifiAccessPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = accessPoint.registeredDeviceName ?: accessPoint.ssid
    Card(
        modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (accessPoint.isRegistered) RegisteredBadge()
            }
            Text(accessPoint.bssid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("CH ${accessPoint.channel} ・ ${accessPoint.frequencyMhz}MHz ・ ${accessPoint.channelWidthMhz}MHz")
            Text(stringResource(R.string.selected_ap_signal_detail, accessPoint.rssi, accessPoint.signalQuality.localizedLabel(), accessPoint.distanceRange.localizedLabel(useFeet)))
            accessPoint.registeredGroupName?.let {
                Text(stringResource(R.string.group_format, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (!accessPoint.isRegistered) {
                TextButton(onClick = { onRegister(accessPoint) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
