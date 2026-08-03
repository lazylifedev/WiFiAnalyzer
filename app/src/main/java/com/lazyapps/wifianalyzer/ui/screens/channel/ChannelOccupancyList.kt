package com.lazyapps.wifianalyzer.ui.screens.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.model.ChannelOccupancy
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.ui.components.RegisteredBadge
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

data class ChannelKey(val band: WifiBand, val channel: Int)

@Composable
fun ChannelOccupancyCard(
    band: WifiBand,
    usage: ChannelOccupancy,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(band.name, usage.channel) { mutableStateOf(false) }
    val strongest = usage.accessPoints.maxByOrNull { it.rssi }
    val color = when {
        usage.estimatedCongestion >= .8f -> MaterialTheme.colorScheme.error
        usage.estimatedCongestion >= .55f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier.fillMaxWidth().clickable { expanded = !expanded }, border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("CH ${usage.channel}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${(usage.estimatedCongestion * 100).toInt()}%", color = color, fontWeight = FontWeight.Bold)
            }
            Text(pluralStringResource(R.plurals.network_count, usage.accessPoints.size, usage.accessPoints.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { usage.estimatedCongestion },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            strongest?.let { Text("${it.registeredDeviceName ?: it.ssid}   ${it.rssi} dBm", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            AnimatedVisibility(expanded) {
                Column {
                    usage.accessPoints.forEach { AccessPointLine(it, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun AccessPointLine(accessPoint: WifiAccessPoint, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(accessPoint.bssid) }.padding(vertical = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Column(Modifier.weight(1f)) {
            Text(accessPoint.registeredDeviceName ?: accessPoint.ssid, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${accessPoint.bssid} ・ ${accessPoint.channelWidthMhz}MHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (accessPoint.isRegistered) RegisteredBadge()
        Text("${accessPoint.rssi} dBm", style = MaterialTheme.typography.labelMedium)
    }
}
