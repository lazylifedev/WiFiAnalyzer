package com.lazyapps.wifianalyzer.ui.screens.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.components.SectionLabel
import com.lazyapps.wifianalyzer.ui.theme.AccentColor
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.ThemeUiState
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.ui.theme.lightSeed
import com.lazyapps.wifianalyzer.ui.workspace.WorkspaceUiState
import com.lazyapps.wifianalyzer.domain.Workspace

@Composable
fun SettingsScreen(
    state: ThemeUiState,
    onModeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentColor) -> Unit,
    onAnimationChange: (Boolean) -> Unit,
    refreshIntervalMillis: Long = 20_000L,
    onRefreshIntervalChange: (Long) -> Unit = {},
    distanceUnit: DistanceUnitPreference = DistanceUnitPreference.METERS,
    onDistanceUnitChange: (DistanceUnitPreference) -> Unit = {},
    visibleBands: Set<WifiBand> = WifiBand.entries.toSet(),
    onVisibleBandsChange: (Set<WifiBand>) -> Unit = {},
    workspaceState: WorkspaceUiState = WorkspaceUiState(),
    onSelectWorkspace: (Long) -> Unit = {},
    onCreateWorkspace: (String) -> Unit = {},
    onRenameWorkspace: (Long, String) -> Unit = { _, _ -> },
    onMoveWorkspace: (Long, Int) -> Unit = { _, _ -> },
    onDeleteWorkspace: (Long) -> Unit = {},
    onLoadWorkspaceCounts: (Long) -> Unit = {},
    onOpenBackup: () -> Unit = {},
) {
    val context = LocalContext.current
    val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    var showWorkspaces by remember { mutableStateOf(false) }
    var showRefreshIntervals by remember { mutableStateOf(false) }
    var showDistanceUnits by remember { mutableStateOf(false) }
    var showBands by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item { ScreenHeader(stringResource(R.string.screen_settings)) }
        item { SectionLabel("ワークスペース", Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(
                onClick = { showWorkspaces = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large).testTag("workspace_settings"),
                border = CardDefaults.outlinedCardBorder(),
            ) { SettingRow("ワークスペース", workspaceState.selected?.name ?: "default") }
        }
        item { SectionLabel(stringResource(R.string.theme_section), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Column(Modifier.padding(horizontal = AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeModeCard(mode, state.mode == mode, { onModeChange(mode) }, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        AccentColor.entries.forEach { accent ->
                            val accentLabel = stringResource(accent.labelRes())
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .selectable(selected = accent == state.accent, onClick = { onAccentChange(accent) }, role = Role.RadioButton)
                                    .semantics { contentDescription = accentLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    modifier = Modifier.size(34.dp).then(if (accent == state.accent) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                    shape = CircleShape,
                                    color = accent.lightSeed(),
                                    content = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        item { SectionLabel(stringResource(R.string.display_section), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                SettingRow(stringResource(R.string.distance_unit), if (distanceUnit == DistanceUnitPreference.METERS) "メートル (m)" else "フィート (ft)", onClick = { showDistanceUnits = true })
                SettingRow(stringResource(R.string.frequency_bands), visibleBandLabel(visibleBands), onClick = { showBands = true })
                SettingRow("Wi-Fi自動更新", refreshIntervalLabel(refreshIntervalMillis), trailing = {
                    IconButton(onClick = { showRefreshIntervals = true }, modifier = Modifier.testTag("refresh_interval")) {
                        Icon(Icons.Rounded.ChevronRight, "更新間隔を変更")
                    }
                })
                Text(
                    "端末やAndroidの制限により、設定した間隔で更新できない場合があります。",
                    modifier = Modifier.padding(horizontal = AppSpacing.large),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingRow(stringResource(R.string.animations), trailing = { Switch(state.animationsEnabled, onAnimationChange) })
            }
        }
        item { SectionLabel(stringResource(R.string.other_section), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                SettingRow("バックアップと復元", "ZIP", onClick = onOpenBackup)
                SettingRow(stringResource(R.string.about_app), "バージョン ${packageInfo.versionName}", onClick = { showAbout = true })
            }
        }
    }
    if (showWorkspaces) WorkspaceDialog(
        state = workspaceState, onDismiss = { showWorkspaces = false }, onSelect = onSelectWorkspace,
        onCreate = onCreateWorkspace, onRename = onRenameWorkspace, onMove = onMoveWorkspace,
        onDelete = onDeleteWorkspace, onLoadCounts = onLoadWorkspaceCounts,
    )
    if (showRefreshIntervals) AlertDialog(
        onDismissRequest = { showRefreshIntervals = false },
        title = { Text("Wi-Fi自動更新") },
        text = {
            Column {
                listOf(10_000L, 15_000L, 20_000L, 30_000L, 60_000L, 120_000L, 300_000L).forEach { interval ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = refreshIntervalMillis == interval,
                            onClick = { onRefreshIntervalChange(interval); showRefreshIntervals = false },
                            role = Role.RadioButton,
                        ).padding(vertical = AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(refreshIntervalMillis == interval, onClick = null)
                        Text(refreshIntervalLabel(interval))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showRefreshIntervals = false }) { Text("閉じる") } },
    )
    if (showDistanceUnits) ChoiceDialog(
        title = "距離単位",
        choices = DistanceUnitPreference.entries.map { it to if (it == DistanceUnitPreference.METERS) "メートル" else "フィート" },
        selected = distanceUnit,
        onSelect = { onDistanceUnitChange(it); showDistanceUnits = false },
        onDismiss = { showDistanceUnits = false },
    )
    if (showBands) AlertDialog(
        onDismissRequest = { showBands = false },
        title = { Text("表示対象周波数帯") },
        text = { Column { WifiBand.entries.forEach { band ->
            val selected = band in visibleBands
            Row(
                Modifier.fillMaxWidth().selectable(selected, onClick = {
                    val next = if (selected) visibleBands - band else visibleBands + band
                    if (next.isNotEmpty()) onVisibleBandsChange(next)
                }, role = Role.Checkbox).padding(vertical = AppSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Checkbox(selected, null)
                Text(band.label)
            }
        } } },
        confirmButton = { TextButton(onClick = { showBands = false }) { Text("完了") } },
    )
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        title = { Text("アプリについて") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("Wi-Fiアナライザー")
            Text("バージョン名: ${packageInfo.versionName}")
            Text("バージョンコード: ${packageInfo.longVersionCode}")
            Text("パッケージ名: ${context.packageName}")
            Text("Android対応バージョン: Android 8.0以上")
            Text("オープンソースライセンス: 準備中")
            Text("プライバシーポリシー: 準備中")
            Text("利用規約: 準備中")
            Text("問い合わせ: 準備中")
        } },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("閉じる") } },
    )
}

internal fun refreshIntervalLabel(milliseconds: Long): String = when (milliseconds) {
    10_000L -> "10秒"
    15_000L -> "15秒"
    20_000L -> "20秒"
    30_000L -> "30秒"
    60_000L -> "1分"
    120_000L -> "2分"
    300_000L -> "5分"
    else -> "${milliseconds / 1_000L}秒"
}

internal fun visibleBandLabel(bands: Set<WifiBand>): String = WifiBand.entries.filter { it in bands }.joinToString(" / ") {
    when (it) { WifiBand.BAND_24 -> "2.4"; WifiBand.BAND_5 -> "5"; WifiBand.BAND_6 -> "6" }
} + " GHz"

@Composable
private fun <T> ChoiceDialog(title: String, choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column {
        choices.forEach { (choice, label) -> Row(
            Modifier.fillMaxWidth().selectable(choice == selected, onClick = { onSelect(choice) }, role = Role.RadioButton).padding(vertical = AppSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) { RadioButton(choice == selected, null); Text(label) } }
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
}

@Composable
private fun WorkspaceDialog(
    state: WorkspaceUiState, onDismiss: () -> Unit, onSelect: (Long) -> Unit, onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit, onMove: (Long, Int) -> Unit, onDelete: (Long) -> Unit, onLoadCounts: (Long) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Workspace?>(null) }
    var deleting by remember { mutableStateOf<Workspace?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ワークスペース") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("現在選択中: ${state.selected?.name ?: "—"}", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it }, Modifier.weight(1f).testTag("workspace_name"), label = { Text("追加する名前") }, singleLine = true)
                IconButton(enabled = !state.busy && newName.isNotBlank(), onClick = { onCreate(newName); newName = "" }, modifier = Modifier.testTag("workspace_add")) { Icon(Icons.Rounded.Add, "ワークスペースを追加") }
            }
            state.workspaces.forEachIndexed { index, workspace ->
                Card(
                    onClick = { onSelect(workspace.id) },
                    colors = CardDefaults.cardColors(containerColor = if (workspace.id == state.selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("workspace_${workspace.id}"),
                ) { Row(Modifier.fillMaxWidth().padding(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (workspace.id == state.selectedId) "選択中・" else "") + workspace.name, Modifier.weight(1f))
                    IconButton(enabled = index > 0 && !state.busy, onClick = { onMove(workspace.id, -1) }) { Icon(Icons.Rounded.ArrowUpward, "上へ移動") }
                    IconButton(enabled = index < state.workspaces.lastIndex && !state.busy, onClick = { onMove(workspace.id, 1) }) { Icon(Icons.Rounded.ArrowDownward, "下へ移動") }
                    IconButton(enabled = !state.busy, onClick = { editing = workspace }) { Icon(Icons.Rounded.Edit, "名前変更") }
                    IconButton(enabled = !state.busy, onClick = { onLoadCounts(workspace.id); deleting = workspace }) { Icon(Icons.Rounded.Delete, "削除") }
                } }
            }
        } },
        confirmButton = { Button(onClick = onDismiss) { Text("閉じる") } },
    )
    editing?.let { workspace ->
        var value by remember(workspace.id) { mutableStateOf(workspace.name) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("名前変更") }, text = { OutlinedTextField(value, { value = it }, singleLine = true) }, confirmButton = { Button(onClick = { onRename(workspace.id, value); editing = null }) { Text("変更") } }, dismissButton = { Button(onClick = { editing = null }) { Text("キャンセル") } })
    }
    deleting?.let { workspace ->
        val counts = state.deleteCounts[workspace.id]
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("「${workspace.name}」を削除しますか？") }, text = { Text("登録機器 ${counts?.devices ?: 0}台\nグループ ${counts?.groups ?: 0}件\n写真 ${counts?.photos ?: 0}枚\n\nこのワークスペース内のデータも削除されます。最後の1件の場合は新しいdefaultを作成します。") }, confirmButton = { Button(enabled = !state.busy, onClick = { onDelete(workspace.id); deleting = null }) { Text("削除") } }, dismissButton = { Button(onClick = { deleting = null }) { Text("キャンセル") } })
    }
}

private fun AccentColor.labelRes(): Int = when (this) {
    AccentColor.BLUE -> R.string.accent_blue
    AccentColor.INDIGO -> R.string.accent_indigo
    AccentColor.PURPLE -> R.string.accent_purple
    AccentColor.CYAN -> R.string.accent_cyan
    AccentColor.GREEN -> R.string.accent_green
    AccentColor.ORANGE -> R.string.accent_orange
    AccentColor.PINK -> R.string.accent_pink
}

@Composable
private fun ThemeModeCard(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, label) = when (mode) {
        ThemeMode.SYSTEM -> Icons.Rounded.PhoneAndroid to stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> Icons.Rounded.LightMode to stringResource(R.string.theme_light)
        ThemeMode.DARK -> Icons.Rounded.DarkMode to stringResource(R.string.theme_dark)
    }
    Card(
        modifier = modifier.testTag("theme_${mode.name}").selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.medium), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.selectable(false, onClick = onClick) else Modifier).padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        value?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        trailing?.invoke() ?: Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() = WifiAnalyzerTheme { SettingsScreen(ThemeUiState(), {}, {}, {}) }
