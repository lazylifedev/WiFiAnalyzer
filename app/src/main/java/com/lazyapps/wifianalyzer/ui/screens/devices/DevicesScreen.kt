package com.lazyapps.wifianalyzer.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.RegisteredDevice
import com.lazyapps.wifianalyzer.sampledata.SampleData
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.components.SignalBars
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme

@Composable
fun DevicesScreen(onAddDevice: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("すべて") }
    val groups = listOf(
        "すべて" to stringResource(R.string.group_all),
        "本社" to stringResource(R.string.group_head_office),
        "2階" to stringResource(R.string.group_second_floor),
        "会議室" to stringResource(R.string.group_meeting_room),
        "倉庫" to stringResource(R.string.group_warehouse),
    )
    val visible = SampleData.devices.filter {
        (selectedGroup == "すべて" || it.group == selectedGroup) &&
            (query.isBlank() || it.name.contains(query, true) || it.address.contains(query, true))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        item {
            ScreenHeader(stringResource(R.string.screen_devices), action = {
                Button(onClick = onAddDevice) {
                    Icon(Icons.Rounded.Add, null)
                    Text(stringResource(R.string.add_device))
                }
            })
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(R.string.search_devices), maxLines = 1) },
            )
        }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
            ) {
                groups.forEach { (group, label) ->
                    FilterChip(selected = selectedGroup == group, onClick = { selectedGroup = group }, label = { Text(label) })
                }
            }
        }
        visible.groupBy { it.group }.forEach { (group, devices) ->
            item(key = "group-$group") {
                Text("$group（${devices.size}）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = AppSpacing.large, vertical = AppSpacing.small))
            }
            items(devices, key = { it.address }) { device ->
                DeviceRow(device, Modifier.padding(horizontal = AppSpacing.large))
            }
        }
    }
}

@Composable
private fun DeviceRow(device: RegisteredDevice, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Icon(Icons.Rounded.Wifi, null, tint = if (device.detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (device.detected) stringResource(R.string.detected) else stringResource(R.string.not_detected),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.last_detected, device.lastSeen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SignalBars(device.signalLevel)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07111F)
@Composable
private fun DevicesPreview() = WifiAnalyzerTheme(mode = ThemeMode.DARK) { DevicesScreen({}) }
