package com.lazyapps.wifianalyzer.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.lazyapps.wifianalyzer.domain.DeviceBssidInput
import com.lazyapps.wifianalyzer.R
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
    baseline: DeviceInput? = null,
    groupCreateDialogVisible: Boolean = false,
    newGroupName: String = "",
    groupNameValidationError: String? = null,
    isCreatingGroup: Boolean = false,
    onShowGroupCreate: () -> Unit = {},
    onDismissGroupCreate: () -> Unit = {},
    onNewGroupNameChange: (String) -> Unit = {},
    onCreateGroup: ((Long) -> Unit) -> Unit = {},
) {
    var form by remember(initial) { mutableStateOf(initial) }
    var showGroupPicker by remember { mutableStateOf(false) }
    val manufacturerLabel = stringResource(R.string.manufacturer)
    val modelLabel = stringResource(R.string.model)
    val serialNumberLabel = stringResource(R.string.serial_number)
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().testTag("registration_list"),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item {
            ScreenHeader(stringResource(if (form.id == 0L) R.string.registration_add_device else R.string.registration_edit_device), stringResource(R.string.registration_required_hint), action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    Button(enabled = !busy, onClick = { onSave(form) }, modifier = Modifier.testTag("save_device")) { Text(stringResource(if (busy) R.string.saving else R.string.save)) }
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
        baseline?.let { before ->
            val changes = listOf(
                manufacturerLabel to (before.manufacturer to form.manufacturer),
                modelLabel to (before.model to form.model),
                serialNumberLabel to (before.serialNumber to form.serialNumber),
                "SSID" to (before.ssid to form.ssid),
                "BSSID" to (before.bssids.joinToString { it.bssid } to form.bssids.joinToString { it.bssid }),
            ).filter { (_, values) -> values.first != values.second }
            if (changes.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Text(stringResource(R.string.changes), style = MaterialTheme.typography.titleMedium)
                        val notEntered = stringResource(R.string.not_entered)
                        changes.forEach { (label, values) -> Text(stringResource(R.string.change_value_format, label, values.first.ifBlank { notEntered }, values.second.ifBlank { notEntered })) }
                        Text(stringResource(R.string.review_before_saving), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            FormSection(stringResource(R.string.basic_information)) {
                FormField(stringResource(R.string.device_name_required), form.displayName, { form = form.copy(displayName = it) }, "device_name")
                FormField("SSID", form.ssid, { form = form.copy(ssid = it) })
                FormField(stringResource(R.string.manufacturer), form.manufacturer, { form = form.copy(manufacturer = it) })
                FormField(stringResource(R.string.model), form.model, { form = form.copy(model = it) })
                FormField(stringResource(R.string.serial_number), form.serialNumber, { form = form.copy(serialNumber = it) })
            }
        }
        item {
            FormSection(stringResource(R.string.group)) {
                val selectedName = groups.firstOrNull { it.id == form.groupId }?.name ?: stringResource(R.string.uncategorized)
                OutlinedButton(onClick = { showGroupPicker = true }, modifier = Modifier.fillMaxWidth().testTag("group_picker")) {
                    Text(selectedName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            FormSection(stringResource(R.string.installation_details)) {
                FormField(stringResource(R.string.installation_location), form.location, { form = form.copy(location = it) })
                FormField(stringResource(R.string.notes), form.notes, { form = form.copy(notes = it) }, singleLine = false)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BSSID", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.primary_bssid_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { form = form.copy(bssids = form.bssids + DeviceBssidInput("", "2.4 GHz")) }, modifier = Modifier.testTag("add_bssid")) {
                    Icon(Icons.Rounded.Add, null)
                    Text(stringResource(R.string.add))
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
            ) { Text(stringResource(if (busy) R.string.saving else R.string.save_registration)) }
        }
    }

    if (showGroupPicker) AlertDialog(
        onDismissRequest = { showGroupPicker = false },
        title = { Text(stringResource(R.string.select_group)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).testTag("group_picker_list")) {
                item {
                    GroupChoice(stringResource(R.string.uncategorized), form.groupId == null) { form = form.copy(groupId = null); showGroupPicker = false }
                }
                itemsIndexed(groups, key = { _, group -> group.id }) { _, group ->
                    GroupChoice(group.name, form.groupId == group.id) { form = form.copy(groupId = group.id); showGroupPicker = false }
                }
                item {
                    TextButton(onClick = onShowGroupCreate, modifier = Modifier.fillMaxWidth().testTag("create_group_from_form")) {
                        Icon(Icons.Rounded.Add, null)
                        Text(stringResource(R.string.create_new_group))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showGroupPicker = false }) { Text(stringResource(R.string.close)) } },
    )

    if (groupCreateDialogVisible) AlertDialog(
        onDismissRequest = onDismissGroupCreate,
        modifier = Modifier.imePadding(),
        title = { Text(stringResource(R.string.new_group)) },
        text = {
            OutlinedTextField(
                value = newGroupName,
                onValueChange = onNewGroupNameChange,
                label = { Text(stringResource(R.string.group_name)) },
                supportingText = groupNameValidationError?.let { message -> { Text(message) } },
                isError = groupNameValidationError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_group_name"),
            )
        },
        dismissButton = { TextButton(enabled = !isCreatingGroup, onClick = onDismissGroupCreate) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            Button(enabled = !isCreatingGroup, onClick = { onCreateGroup { id -> form = form.copy(groupId = id); showGroupPicker = false } }, modifier = Modifier.testTag("confirm_create_group")) {
                Text(stringResource(if (isCreatingGroup) R.string.creating else R.string.create))
            }
        },
    )
}

@Composable
private fun GroupChoice(name: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = null)
        Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BssidEditor(index: Int, item: DeviceBssidInput, canDelete: Boolean, onChange: (DeviceBssidInput) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val bands = listOf("2.4 GHz", "5 GHz", "6 GHz")
    Card(modifier.fillMaxWidth(), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (index == 0) R.string.primary_bssid_number else R.string.bssid_number, index + 1), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(enabled = canDelete, onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete_bssid)) }
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
            OutlinedTextField(item.label, { onChange(item.copy(label = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.optional_label)) }, singleLine = true)
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
