package com.lazyapps.wifianalyzer.ui.screens.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.ChannelUsage
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.sampledata.SampleData
import com.lazyapps.wifianalyzer.ui.components.BandSelector
import com.lazyapps.wifianalyzer.ui.components.RegisteredBadge
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme

@Composable
fun ChannelScreen() {
    var band by remember { mutableStateOf(WifiBand.BAND_24) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item { ScreenHeader(stringResource(R.string.screen_channel)) }
        item { BandSelector(band, { band = it }, Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.occupancy_legend_free), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.occupancy_legend_busy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        items(SampleData.channelUsage, key = { it.channel }) { usage ->
            ChannelCard(usage, Modifier.padding(horizontal = AppSpacing.large))
        }
    }
}

@Composable
private fun ChannelCard(usage: ChannelUsage, modifier: Modifier = Modifier) {
    val barColor = when {
        usage.occupancy >= .8f -> MaterialTheme.colorScheme.error
        usage.occupancy >= .55f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.channel_format, usage.channel), style = MaterialTheme.typography.titleLarge)
                Text("  ${stringResource(R.string.frequency_format, usage.frequencyMhz)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.occupancy_percent, (usage.occupancy * 100).toInt()), color = barColor, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { usage.occupancy },
                modifier = Modifier.fillMaxWidth(),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(stringResource(R.string.network_count, usage.networks.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            usage.networks.forEach { network ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(network.ssid, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                    if (network.registered) RegisteredBadge()
                    Text(stringResource(R.string.signal_dbm, network.dbm), style = MaterialTheme.typography.labelMedium)
                    Text(network.distance, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07111F)
@Composable
private fun ChannelPreview() = WifiAnalyzerTheme(mode = ThemeMode.DARK) { ChannelScreen() }
