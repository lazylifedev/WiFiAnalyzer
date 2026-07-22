package com.lazyapps.wifianalyzer.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiNetwork
import com.lazyapps.wifianalyzer.sampledata.SampleData
import com.lazyapps.wifianalyzer.ui.components.BandSelector
import com.lazyapps.wifianalyzer.ui.components.RegisteredBadge
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme

@Composable
fun HomeScreen() {
    var band by remember { mutableStateOf(WifiBand.BAND_24) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        item { ScreenHeader(stringResource(R.string.screen_home), stringResource(R.string.last_updated)) }
        item { BandSelector(band, { band = it }, Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = AppSpacing.large, end = AppSpacing.small, top = AppSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.nearby_access_points), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.access_point_count, SampleData.nearbyNetworks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {}) { Icon(Icons.Rounded.Tune, stringResource(R.string.sort_filter)) }
            }
        }
        items(SampleData.nearbyNetworks, key = { it.bssid }) { network ->
            NetworkRow(network, Modifier.padding(horizontal = AppSpacing.large))
        }
    }
}

@Composable
private fun NetworkRow(network: WifiNetwork, modifier: Modifier = Modifier) {
    val signalColor = when {
        network.dbm >= -65 -> MaterialTheme.colorScheme.primary
        network.dbm >= -78 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val signalLabel = stringResource(
        when {
            network.dbm >= -55 -> R.string.signal_excellent
            network.dbm >= -67 -> R.string.signal_good
            network.dbm >= -79 -> R.string.signal_fair
            else -> R.string.signal_weak
        },
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.medium, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        ) {
            Icon(Icons.Rounded.Wifi, null, tint = signalColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(network.ssid, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    if (network.registered) RegisteredBadge()
                }
                Text(network.bssid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${network.security}  •  ${stringResource(R.string.channel_format, network.channel)}  •  ${stringResource(R.string.frequency_format, network.frequencyMhz)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(network.dbm.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = signalColor)
                Text(stringResource(R.string.signal_detail, signalLabel), style = MaterialTheme.typography.labelSmall, color = signalColor, maxLines = 1)
                Text(stringResource(R.string.estimated_distance, network.distance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07111F)
@Composable
private fun HomePreview() = WifiAnalyzerTheme(mode = ThemeMode.DARK) { HomeScreen() }
