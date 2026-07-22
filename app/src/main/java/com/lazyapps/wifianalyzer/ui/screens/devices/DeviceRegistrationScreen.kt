package com.lazyapps.wifianalyzer.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.lazyapps.wifianalyzer.domain.DeviceBssidInput
import com.lazyapps.wifianalyzer.domain.DeviceGroup
import com.lazyapps.wifianalyzer.domain.DeviceInput
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

@Composable
fun DeviceRegistrationScreen(
    initial: DeviceInput,
    groups: List<DeviceGroup>,
    errorMessage: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (DeviceInput) -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().testTag("registration_list"),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item {
            ScreenHeader(if (form.id == 0L) "機器を登録" else "機器を編集", "機器名とBSSIDは必須", action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") }
                    Button(enabled = !busy, onClick = { onSave(form) }, modifier = Modifier.testTag("save_device")) { Text(if (busy) "保存中…" else "保存") }
                }
            })
        }
        errorMessage?.let { message ->
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) { Text(message, Modifier.padding(AppSpacing.medium), color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
        item {
            FormSection("基本情報") {
                FormField("機器名 *", form.displayName, { form = form.copy(displayName = it) }, "device_name")
                FormField("SSID", form.ssid, { form = form.copy(ssid = it) })
                FormField("メーカー", form.manufacturer, { form = form.copy(manufacturer = it) })
                FormField("型番", form.model, { form = form.copy(model = it) })
                FormField("シリアル番号", form.serialNumber, { form = form.copy(serialNumber = it) })
            }
        }
        item {
            FormSection("グループ") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    FilterChip(selected = form.groupId == null, onClick = { form = form.copy(groupId = null) }, label = { Text("未分類") })
                    groups.forEach { group ->
                        FilterChip(selected = form.groupId == group.id, onClick = { form = form.copy(groupId = group.id) }, label = { Text(group.name) })
                    }
                }
            }
        }
        item {
            FormSection("設置情報") {
                FormField("設置場所", form.location, { form = form.copy(location = it) })
                FormField("メモ", form.notes, { form = form.copy(notes = it) }, singleLine = false)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BSSID", style = MaterialTheme.typography.titleMedium)
                    Text("先頭のBSSIDを主BSSIDとして表示します", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { form = form.copy(bssids = form.bssids + DeviceBssidInput("", "2.4 GHz")) }, modifier = Modifier.testTag("add_bssid")) {
                    Icon(Icons.Rounded.Add, null)
                    Text("追加")
                }
            }
        }
        itemsIndexed(form.bssids, key = { index, _ -> index }) { index, item ->
            BssidEditor(
                index = index,
                item = item,
                canDelete = form.bssids.size > 1,
                onChange = { updated -> form = form.copy(bssids = form.bssids.toMutableList().also { it[index] = updated }) },
                onDelete = { form = form.copy(bssids = form.bssids.toMutableList().also { it.removeAt(index) }) },
                modifier = Modifier.padding(horizontal = AppSpacing.large),
            )
        }
        item {
            Button(
                enabled = !busy,
                onClick = { onSave(form) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large).testTag("save_device_bottom"),
            ) { Text(if (busy) "保存中…" else "登録内容を保存") }
        }
    }
}

@Composable
private fun BssidEditor(index: Int, item: DeviceBssidInput, canDelete: Boolean, onChange: (DeviceBssidInput) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val bands = listOf("2.4 GHz", "5 GHz", "6 GHz")
    Card(modifier.fillMaxWidth(), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BSSID ${index + 1}${if (index == 0) "（主）" else ""}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(enabled = canDelete, onClick = onDelete) { Icon(Icons.Rounded.Delete, "BSSIDを削除") }
            }
            OutlinedTextField(
                value = item.bssid,
                onValueChange = { onChange(item.copy(bssid = it)) },
                modifier = Modifier.fillMaxWidth().testTag("bssid_$index"),
                label = { Text("AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                bands.forEach { band -> FilterChip(selected = item.band == band, onClick = { onChange(item.copy(band = band)) }, label = { Text(band) }) }
            }
            OutlinedTextField(item.label, { onChange(item.copy(label = it)) }, Modifier.fillMaxWidth(), label = { Text("ラベル（任意）") }, singleLine = true)
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FormField(label: String, value: String, onValueChange: (String) -> Unit, tag: String? = null, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
    )
}
