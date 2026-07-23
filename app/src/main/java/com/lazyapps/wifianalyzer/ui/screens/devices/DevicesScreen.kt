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
import com.lazyapps.wifianalyzer.domain.DetectionPolicy
import com.lazyapps.wifianalyzer.domain.DeviceGroup
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
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
    onRefresh: () -> Unit = {},
    workspaceName: String? = null,
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

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        item {
            ScreenHeader("登録済み機器", listOfNotNull(workspaceName, "${devices.size}台").joinToString(" ・ "), autoSizeTitle = true, action = {
                Row {
                    IconButton(onClick = { searchVisible = true }, modifier = Modifier.testTag("show_device_search")) { Icon(Icons.Rounded.Search, "検索") }
                    IconButton(onClick = { showAddMethods = true }, modifier = Modifier.testTag("add_device")) { Icon(Icons.Rounded.Add, "機器を新規登録") }
                    IconButton(onClick = { showMore = true }) { Icon(Icons.Rounded.MoreVert, "その他") }
                    DropdownMenu(showMore, { showMore = false }) {
                        DropdownMenuItem({ Text("グループで絞り込み") }, { showMore = false; showGroupFilter = true }, leadingIcon = { Icon(Icons.Rounded.FilterList, null) })
                        DeviceSort.entries.forEach { option -> DropdownMenuItem({ Text("並び順: ${sortLabel(option)}") }, { sortName = option.name; showMore = false }, leadingIcon = { if (sort == option) Icon(Icons.Rounded.SwapVert, null) }) }
                        DropdownMenuItem({ Text("グループ管理") }, { showMore = false; showGroups = true }, leadingIcon = { Icon(Icons.Rounded.Settings, null) })
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
                placeholder = { Text("機器名、SSID、BSSIDを検索", maxLines = 1) },
                trailingIcon = { IconButton(onClick = { query = ""; searchVisible = false; keyboard?.hide() }) { Icon(Icons.Rounded.Close, "検索を閉じる") } },
            )
        }
        if (selectedGroup != null || uncategorizedOnly) item {
            FilterChip(
                selected = true,
                onClick = { selectedGroup = null; uncategorizedOnly = false },
                label = { Text(if (uncategorizedOnly) "未分類" else groups.firstOrNull { it.id == selectedGroup }?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { Icon(Icons.Rounded.Close, "絞り込みを解除") },
                modifier = Modifier.padding(horizontal = AppSpacing.large),
            )
        }
        if (visible.isEmpty()) {
            item {
                Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(if (devices.isEmpty()) "登録済み機器はまだありません" else "条件に一致する登録機器はありません", style = MaterialTheme.typography.titleMedium)
                    if (devices.isEmpty()) {
                        Text("スキャン結果から登録するか、ラベルを読み取る、または手動で追加できます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onScanLabel, modifier = Modifier.fillMaxWidth()) { Text("OCRで機器登録") }
                        OutlinedButton(onClick = onAddDevice, modifier = Modifier.fillMaxWidth()) { Text("手動で機器登録") }
                    }
                }
            }
        }
        items(visible, key = { it.id }) { device ->
            DeviceRow(device, { onOpenDevice(device.id) }, { deleteTarget = device }, Modifier.padding(horizontal = AppSpacing.large))
        }
    }
    }

    deleteTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("機器を削除しますか？") },
            text = { Text("「${device.displayName}」と関連するBSSIDを削除します。グループは削除されません。") },
            confirmButton = { TextButton(onClick = { onDeleteDevice(device.id); deleteTarget = null }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") } },
        )
    }
    if (showGroups) {
        GroupManagementDialog(groups, onCreateGroup, onRenameGroup, onDeleteGroup, onMoveGroup) { showGroups = false }
    }
    if (showGroupFilter) AlertDialog(
        onDismissRequest = { showGroupFilter = false },
        title = { Text("グループで絞り込み") },
        text = { Column {
            FilterChip(selectedGroup == null && !uncategorizedOnly, { selectedGroup = null; uncategorizedOnly = false; showGroupFilter = false }, { Text("すべて (${devices.size})") })
            FilterChip(uncategorizedOnly, { selectedGroup = null; uncategorizedOnly = true; showGroupFilter = false }, { Text("未分類 (${devices.count { it.groupId == null }})") })
            groups.forEach { group -> FilterChip(selectedGroup == group.id, { selectedGroup = group.id; uncategorizedOnly = false; showGroupFilter = false }, { Text("${group.name} (${devices.count { it.groupId == group.id }})", maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
        } },
        confirmButton = { TextButton(onClick = { showGroupFilter = false }) { Text("閉じる") } },
    )
    if (showAddMethods) {
        AlertDialog(
            onDismissRequest = { showAddMethods = false },
            title = { Text("登録方法を選択") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Button(
                        onClick = { showAddMethods = false; onScanLabel() },
                        modifier = Modifier.fillMaxWidth().testTag("add_by_camera"),
                    ) { Icon(Icons.Rounded.Add, null); Text("ラベルをカメラで読み取る") }
                    OutlinedButton(
                        onClick = { showAddMethods = false; onAddDevice() },
                        modifier = Modifier.fillMaxWidth().testTag("add_manually"),
                    ) { Text("手動で入力する") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddMethods = false }) { Text("キャンセル") } },
        )
    }
}

private fun sortLabel(sort: DeviceSort): String = when (sort) {
    DeviceSort.NAME -> "名前"
    DeviceSort.RECENT -> "最終検出"
    DeviceSort.RSSI -> "RSSI"
    DeviceSort.REGISTERED -> "登録日時"
}

@Composable
private fun DeviceRow(device: RegisteredDevice, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val detected = DetectionPolicy.isDetected(device.lastSeenAt, System.currentTimeMillis())
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Icon(Icons.Rounded.Wifi, null, tint = if (detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(device.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val makerModel = listOf(device.manufacturer, device.model).filter { it.isNotBlank() }.joinToString(" / ")
                if (makerModel.isNotBlank()) Text(makerModel, style = MaterialTheme.typography.bodySmall)
                if (device.ssid.isNotBlank()) Text("SSID: ${device.ssid}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("BSSID: ${device.primaryBssid}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${device.groupName ?: "未分類"} · ${if (detected) "現在検出中" else "未検出"}", style = MaterialTheme.typography.labelSmall, color = if (detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${device.lastSeenRssi?.let { "$it dBm" } ?: "RSSI —"} · ${relativeTime(device.lastSeenAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "機器メニュー") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("詳細を開く") }, { menu = false; onOpen() })
                    DropdownMenuItem({ Text("削除") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                }
            }
        }
    }
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
        title = { Text("グループ管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, Modifier.weight(1f).testTag("group_name_input"), label = { Text("新しいグループ") }, singleLine = true)
                    IconButton(onClick = { if (newName.isNotBlank()) { onCreate(newName); newName = "" } }, modifier = Modifier.testTag("group_create")) { Icon(Icons.Rounded.Add, "作成") }
                }
                Text("未分類はグループ未設定の機器に自動表示されます。", style = MaterialTheme.typography.bodySmall)
                groups.forEachIndexed { index, group ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(enabled = index > 0, onClick = { onMove(group, -1) }) { Text("↑") }
                        TextButton(enabled = index < groups.lastIndex, onClick = { onMove(group, 1) }) { Text("↓") }
                        IconButton(onClick = { editing = group }) { Icon(Icons.Rounded.Edit, "名前変更") }
                        IconButton(onClick = { deleting = group }) { Icon(Icons.Rounded.Delete, "削除") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
    editing?.let { group ->
        var name by remember(group.id) { mutableStateOf(group.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("グループ名を変更") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(group, name); editing = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("キャンセル") } },
        )
    }
    deleting?.let { group ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("グループを削除しますか？") },
            text = { Text("所属機器は削除せず「未分類」へ移動します。") },
            confirmButton = { TextButton(onClick = { onDelete(group); deleting = null }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } },
        )
    }
}

private fun relativeTime(timestamp: Long?): String = timestamp?.let {
    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
} ?: "未検出"
