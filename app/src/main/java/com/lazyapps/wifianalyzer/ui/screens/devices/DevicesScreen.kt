package com.lazyapps.wifianalyzer.ui.screens.devices

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ads.AdConfiguration
import com.lazyapps.wifianalyzer.ads.InlineNativeAdContent
import com.lazyapps.wifianalyzer.ads.InlineNativeAdPolicy
import com.lazyapps.wifianalyzer.ads.rememberInlineNativeAd
import com.lazyapps.wifianalyzer.domain.DetectionPolicy
import com.lazyapps.wifianalyzer.domain.DeviceGroup
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.components.SmoothScanProgressIndicator
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

private enum class DeviceSort { NAME, RECENT, RSSI, REGISTERED }

@Composable
fun DevicesScreen(
    devices: List<RegisteredDevice>,
    groups: List<DeviceGroup>,
    errorMessage: String?,
    onAddDevice: () -> Unit,
    onScanLabel: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onDeleteDevice: (Long) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (DeviceGroup, String) -> Unit,
    onDeleteGroup: (DeviceGroup) -> Unit,
    onMoveGroup: (DeviceGroup, Int) -> Unit,
    isRefreshing: Boolean = false,
    scanState: ScanUiState? = null,
    onRefresh: () -> Unit = {},
    workspaceName: String? = null,
    showInlineNativeAd: Boolean = false,
    inlineAdContent: (@Composable (Int, Modifier) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf<Long?>(null) }
    var uncategorizedOnly by rememberSaveable { mutableStateOf(false) }
    var sortName by rememberSaveable { mutableStateOf(DeviceSort.NAME.name) }
    val sort = DeviceSort.valueOf(sortName)
    var deleteTarget by remember { mutableStateOf<RegisteredDevice?>(null) }
    var showGroups by remember { mutableStateOf(false) }
    var showAddMethods by remember { mutableStateOf(false) }
    var showGroupFilter by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchVisible) { if (searchVisible) { focusRequester.requestFocus(); keyboard?.show() } }
    BackHandler(searchVisible) { searchVisible = false; query = ""; keyboard?.hide() }
    val visible = devices.asSequence()
        .filter { !uncategorizedOnly && selectedGroup == null || uncategorizedOnly && it.groupId == null || selectedGroup == it.groupId }
        .filter { query.isBlank() || listOf(it.displayName, it.manufacturer, it.model, it.ssid, it.primaryBssid, it.groupName.orEmpty()).any { value -> value.contains(query, true) } }
        .let { sequence ->
            when (sort) {
                DeviceSort.NAME -> sequence.sortedBy { it.displayName.lowercase() }
                DeviceSort.RECENT -> sequence.sortedByDescending { it.lastSeenAt ?: 0L }
                DeviceSort.RSSI -> sequence.sortedByDescending { it.lastSeenRssi ?: Int.MIN_VALUE }
                DeviceSort.REGISTERED -> sequence.sortedByDescending { it.createdAt }
            }
        }.toList()
    val nativeAdIndices = InlineNativeAdPolicy.deviceInsertionIndices(visible.size)
        .takeIf { showInlineNativeAd }.orEmpty()
    val firstNativeAd = if (inlineAdContent == null) rememberInlineNativeAd(
        unitId = AdConfiguration.devicesNativeUnitId,
        enabled = showInlineNativeAd,
        requestEligible = nativeAdIndices.isNotEmpty(),
        debugPlacement = "saved_devices_1",
    ) else null
    val secondNativeAd = if (inlineAdContent == null) rememberInlineNativeAd(
        unitId = AdConfiguration.devicesNativeUnitId,
        enabled = showInlineNativeAd,
        requestEligible = nativeAdIndices.size >= 2,
        debugPlacement = "saved_devices_2",
    ) else null

    Column(Modifier.fillMaxSize()) {
    scanState?.let { SmoothScanProgressIndicator(it, progressTag = "devices_scan_progress") }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f), indicator = {}) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("devices_screen"),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        item {
            ScreenHeader(stringResource(R.string.screen_devices), listOfNotNull(workspaceName, pluralStringResource(R.plurals.device_count, devices.size, devices.size)).joinToString(" · "), autoSizeTitle = true, action = {
                Row {
                    IconButton(onClick = { searchVisible = true }, modifier = Modifier.testTag("show_device_search")) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
                    IconButton(onClick = { showAddMethods = true }, modifier = Modifier.testTag("add_device")) { Icon(Icons.Rounded.Add, stringResource(R.string.add_device)) }
                    IconButton(onClick = { showMore = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(showMore, { showMore = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.filter_by_group)) }, { showMore = false; showGroupFilter = true }, leadingIcon = { Icon(Icons.Rounded.FilterList, null) })
                        DeviceSort.entries.forEach { option -> DropdownMenuItem({ Text(stringResource(R.string.sort_by_format, sortLabel(option))) }, { sortName = option.name; showMore = false }, leadingIcon = { if (sort == option) Icon(Icons.Rounded.SwapVert, null) }) }
                        DropdownMenuItem({ Text(stringResource(R.string.manage_groups)) }, { showMore = false; showGroups = true }, leadingIcon = { Icon(Icons.Rounded.Settings, null) })
                    }
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
        if (searchVisible) item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large).focusRequester(focusRequester).testTag("device_search"),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(R.string.search_devices), maxLines = 1) },
                trailingIcon = { IconButton(onClick = { query = ""; searchVisible = false; keyboard?.hide() }) { Icon(Icons.Rounded.Close, stringResource(R.string.close_search)) } },
            )
        }
        if (selectedGroup != null || uncategorizedOnly) item {
            FilterChip(
                selected = true,
                onClick = { selectedGroup = null; uncategorizedOnly = false },
                label = { Text(if (uncategorizedOnly) stringResource(R.string.uncategorized) else groups.firstOrNull { it.id == selectedGroup }?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { Icon(Icons.Rounded.Close, stringResource(R.string.clear_filter)) },
                modifier = Modifier.padding(horizontal = AppSpacing.large),
            )
        }
        if (visible.isEmpty()) {
            item {
                Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(stringResource(if (devices.isEmpty()) R.string.no_saved_devices else R.string.no_matching_devices), style = MaterialTheme.typography.titleMedium)
                    if (devices.isEmpty()) {
                        Text(stringResource(R.string.no_saved_devices_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onScanLabel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_with_ocr)) }
                        OutlinedButton(onClick = onAddDevice, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_manually)) }
                    }
                }
            }
        }
        val adListIndices = nativeAdIndices.mapIndexed { adIndex, deviceCount -> deviceCount + adIndex }
        val renderedItemCount = visible.size + adListIndices.size
        items(
            count = renderedItemCount,
            key = { listIndex ->
                val adIndex = adListIndices.indexOf(listIndex)
                if (adIndex >= 0) "devices_inline_native_ad_${adIndex + 1}"
                else "device_${visible[listIndex - adListIndices.count { it < listIndex }].id}"
            },
        ) { listIndex ->
            val adIndex = adListIndices.indexOf(listIndex)
            if (adIndex >= 0) {
                val adModifier = Modifier.padding(horizontal = AppSpacing.large, vertical = AppSpacing.small)
                val tag = "devices_inline_native_ad_${adIndex + 1}"
                if (inlineAdContent != null) inlineAdContent(adIndex, adModifier)
                else InlineNativeAdContent(if (adIndex == 0) firstNativeAd else secondNativeAd, tag, adModifier)
            } else {
                val deviceIndex = listIndex - adListIndices.count { it < listIndex }
                val device = visible[deviceIndex]
                DeviceRow(device, { onOpenDevice(device.id) }, { deleteTarget = device }, Modifier.padding(horizontal = AppSpacing.large))
            }
        }
    }
    }

    deleteTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_device_title)) },
            text = { Text(stringResource(R.string.delete_device_message, device.displayName)) },
            confirmButton = { TextButton(onClick = { onDeleteDevice(device.id); deleteTarget = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showGroups) {
        GroupManagementDialog(groups, onCreateGroup, onRenameGroup, onDeleteGroup, onMoveGroup) { showGroups = false }
    }
    if (showGroupFilter) AlertDialog(
        onDismissRequest = { showGroupFilter = false },
        title = { Text(stringResource(R.string.filter_by_group)) },
        text = { Column {
            FilterChip(selectedGroup == null && !uncategorizedOnly, { selectedGroup = null; uncategorizedOnly = false; showGroupFilter = false }, { Text(stringResource(R.string.group_count_format, stringResource(R.string.group_all), devices.size)) })
            FilterChip(uncategorizedOnly, { selectedGroup = null; uncategorizedOnly = true; showGroupFilter = false }, { Text(stringResource(R.string.group_count_format, stringResource(R.string.uncategorized), devices.count { it.groupId == null })) })
            groups.forEach { group -> FilterChip(selectedGroup == group.id, { selectedGroup = group.id; uncategorizedOnly = false; showGroupFilter = false }, { Text("${group.name} (${devices.count { it.groupId == group.id }})", maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
        } },
        confirmButton = { TextButton(onClick = { showGroupFilter = false }) { Text(stringResource(R.string.close)) } },
    )
    if (showAddMethods) {
        AlertDialog(
            onDismissRequest = { showAddMethods = false },
            title = { Text(stringResource(R.string.choose_add_method)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Button(
                        onClick = { showAddMethods = false; onScanLabel() },
                        modifier = Modifier.fillMaxWidth().testTag("add_by_camera"),
                    ) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.scan_label_with_camera)) }
                    OutlinedButton(
                        onClick = { showAddMethods = false; onAddDevice() },
                        modifier = Modifier.fillMaxWidth().testTag("add_manually"),
                    ) { Text(stringResource(R.string.enter_manually)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddMethods = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    }
}

@Composable private fun sortLabel(sort: DeviceSort): String = when (sort) {
    DeviceSort.NAME -> stringResource(R.string.sort_name)
    DeviceSort.RECENT -> stringResource(R.string.sort_last_detected)
    DeviceSort.RSSI -> stringResource(R.string.rssi)
    DeviceSort.REGISTERED -> stringResource(R.string.sort_date_added)
}

@Composable
private fun DeviceRow(device: RegisteredDevice, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val detected = DetectionPolicy.isDetected(device.lastSeenAt, System.currentTimeMillis())
    var menu by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(device.id) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.medium, vertical = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(end = 96.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(device.displayName, Modifier.clickable(onClick = onOpen), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (device.ssid.isNotBlank()) Text(device.ssid, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.device_group_status, device.groupName ?: stringResource(R.string.uncategorized), stringResource(if (detected) R.string.detected else R.string.not_detected)), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = if (detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${device.lastSeenRssi?.let { "$it dBm" } ?: stringResource(R.string.rssi_not_available)} ﾂｷ ${relativeTime(device.lastSeenAt)}", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expanded) Column(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("saved_device_details_${device.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) { DetailLine("BSSID", device.primaryBssid); DetailLine(stringResource(R.string.workspace), device.workspaceId.takeIf { it != 0L }?.toString().orEmpty()); DetailLine(stringResource(R.string.manufacturer), device.manufacturer); DetailLine(stringResource(R.string.model), device.model); DetailLine(stringResource(R.string.serial_number), device.serialNumber); DetailLine(stringResource(R.string.location_label), device.location); DetailLine(stringResource(R.string.notes), device.notes) }
            }
            Icon(Icons.Rounded.Wifi, null, tint = if (detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 76.dp).size(18.dp))
            Box(Modifier.align(Alignment.TopEnd)) { IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text(stringResource(R.string.open_details)) }, { menu = false; onOpen() }); DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }) } }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).testTag("saved_device_expand_${device.id}")) { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, stringResource(if (expanded) R.string.hide_details else R.string.show_details)) }
        }
    }
}

@Composable
private fun CompactDeviceRow(device: RegisteredDevice, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val detected = DetectionPolicy.isDetected(device.lastSeenAt, System.currentTimeMillis())
    var menu by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(device.id) { mutableStateOf(false) }
    val detailsDescription = stringResource(if (expanded) R.string.hide_details else R.string.show_details)
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.medium, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Wifi, null, tint = if (detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(device.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (device.ssid.isNotBlank()) Text(device.ssid, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.device_group_status, device.groupName ?: stringResource(R.string.uncategorized), stringResource(if (detected) R.string.detected else R.string.not_detected)), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = if (detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${device.lastSeenRssi?.let { "$it dBm" } ?: stringResource(R.string.rssi_not_available)} · ${relativeTime(device.lastSeenAt)}", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.open_details)) }, { menu = false; onOpen() })
                    DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                }
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("saved_device_expand_${device.id}")) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, detailsDescription)
            }
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp).testTag("saved_device_details_${device.id}"), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                DetailLine("BSSID", device.primaryBssid)
                DetailLine(stringResource(R.string.workspace), device.workspaceId.takeIf { it != 0L }?.toString().orEmpty())
                DetailLine(stringResource(R.string.manufacturer), device.manufacturer)
                DetailLine(stringResource(R.string.model), device.model)
                DetailLine(stringResource(R.string.serial_number), device.serialNumber)
                DetailLine(stringResource(R.string.location_label), device.location)
                DetailLine(stringResource(R.string.notes), device.notes)
            }
        }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isNotBlank()) Text("$label: $value", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GroupManagementDialog(
    groups: List<DeviceGroup>,
    onCreate: (String) -> Unit,
    onRename: (DeviceGroup, String) -> Unit,
    onDelete: (DeviceGroup) -> Unit,
    onMove: (DeviceGroup, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<DeviceGroup?>(null) }
    var deleting by remember { mutableStateOf<DeviceGroup?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_groups)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, Modifier.weight(1f).testTag("group_name_input"), label = { Text(stringResource(R.string.new_group)) }, singleLine = true)
                    IconButton(onClick = { if (newName.isNotBlank()) { onCreate(newName); newName = "" } }, modifier = Modifier.testTag("group_create")) { Icon(Icons.Rounded.Add, stringResource(R.string.create)) }
                }
                Text(stringResource(R.string.uncategorized_explanation), style = MaterialTheme.typography.bodySmall)
                groups.forEachIndexed { index, group ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(enabled = index > 0, onClick = { onMove(group, -1) }) { Text("↑") }
                        TextButton(enabled = index < groups.lastIndex, onClick = { onMove(group, 1) }) { Text("↓") }
                        IconButton(onClick = { editing = group }) { Icon(Icons.Rounded.Edit, stringResource(R.string.rename)) }
                        IconButton(onClick = { deleting = group }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    editing?.let { group ->
        var name by remember(group.id) { mutableStateOf(group.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.rename_group)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(group, name); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { group ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_group_title)) },
            text = { Text(stringResource(R.string.delete_group_message)) },
            confirmButton = { TextButton(onClick = { onDelete(group); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable private fun relativeTime(timestamp: Long?): String = timestamp?.let {
    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
} ?: stringResource(R.string.not_detected)
