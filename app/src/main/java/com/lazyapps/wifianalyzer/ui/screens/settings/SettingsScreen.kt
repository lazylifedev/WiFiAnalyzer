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
import androidx.compose.foundation.verticalScroll
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.text.style.TextOverflow
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.BuildConfig
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.data.WifiUiPreferencesRepository
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
import com.lazyapps.wifianalyzer.ui.permissions.PermissionStatus
import com.lazyapps.wifianalyzer.ui.permissions.PermissionSummary
import com.lazyapps.wifianalyzer.ui.permissions.AppPermissionPolicy

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
    onOpenExport: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenPro: () -> Unit = {},
    onOpenPrivacyOptions: () -> Unit = {},
    showPrivacyOptions: Boolean = false,
    onOpenKintone: () -> Unit = {},
    onRateApp: () -> Unit = {},
    permissionSummary: PermissionSummary = PermissionSummary(PermissionStatus.NOT_GRANTED, PermissionStatus.NOT_GRANTED),
    onRequestScanPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    debugForcePro: Boolean = false,
    onDebugForceProChange: (Boolean) -> Unit = {},
    debugDisplayEnabled: Boolean = false,
    onDebugDisplayEnabledChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    var showWorkspaces by remember { mutableStateOf(false) }
    var showRefreshIntervals by remember { mutableStateOf(false) }
    var showDistanceUnits by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var showBands by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item { ScreenHeader(stringResource(R.string.screen_settings)) }
        item { SectionLabel(stringResource(R.string.workspace), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(
                onClick = { showWorkspaces = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large).testTag("workspace_settings"),
                border = CardDefaults.outlinedCardBorder(),
            ) { SettingRow(stringResource(R.string.workspace), workspaceState.selected?.name ?: stringResource(R.string.default_workspace)) }
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
                val languageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                val languageLabel = when (languageTag) {
                    "en" -> stringResource(R.string.app_language_english)
                    "ja" -> stringResource(R.string.app_language_japanese)
                    else -> stringResource(R.string.app_language_system_default)
                }
                SettingRow(stringResource(R.string.app_language), languageLabel, onClick = { showLanguages = true }, modifier = Modifier.testTag("app_language"))
                SettingRow(stringResource(R.string.distance_unit), stringResource(if (distanceUnit == DistanceUnitPreference.METERS) R.string.distance_meters_value else R.string.distance_feet_value), onClick = { showDistanceUnits = true })
                SettingRow(stringResource(R.string.frequency_bands), visibleBandLabel(visibleBands), onClick = { showBands = true })
                SettingRow(stringResource(R.string.scan_request_interval), localizedRefreshIntervalLabel(refreshIntervalMillis), trailing = {
                    IconButton(onClick = { showRefreshIntervals = true }, modifier = Modifier.testTag("refresh_interval")) {
                        Icon(Icons.Rounded.ChevronRight, stringResource(R.string.change_scan_request_interval))
                    }
                })
                Text(
                    stringResource(R.string.scan_request_interval_explanation),
                    modifier = Modifier.padding(horizontal = AppSpacing.large),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingRow(stringResource(R.string.animations), trailing = { Switch(state.animationsEnabled, onAnimationChange) })
            }
        }
        item { SectionLabel(stringResource(R.string.data_section), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                SettingRow(stringResource(R.string.export_data), stringResource(R.string.csv_and_reports), onClick = onOpenExport)
                SettingRow(stringResource(R.string.import_from_csv), "CSV", onClick = onOpenImport)
                SettingRow(stringResource(R.string.backup_and_restore), "ZIP", onClick = onOpenBackup)
                SettingRow(stringResource(R.string.kintone_integration), "Pro", onClick = onOpenKintone)
            }
        }
        item { SectionLabel(stringResource(R.string.scan_section), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), border = CardDefaults.outlinedCardBorder()) {
                SettingRow(stringResource(R.string.permissions), permissionSummary.wifiScan.label(), onClick = { showPermissions = true })
            }
        }
        item { SectionLabel(stringResource(R.string.support_section), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                SettingRow(stringResource(R.string.help), stringResource(R.string.in_app_help), onClick = { showHelp = true })
                SettingRow(stringResource(R.string.show_onboarding_again), onClick = onShowOnboarding)
                SettingRow(stringResource(R.string.privacy), stringResource(R.string.data_and_permissions), onClick = { showPrivacy = true })
                SettingRow(stringResource(R.string.about_app), stringResource(R.string.version_format, packageInfo.versionName.orEmpty()), onClick = { showAbout = true })
            }
        }
        item { SectionLabel(stringResource(R.string.pro_and_integrations), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                SettingRow(stringResource(R.string.pro_version), onClick = onOpenPro)
                if (showPrivacyOptions) SettingRow(stringResource(R.string.ad_privacy_settings), onClick = onOpenPrivacyOptions)
                SettingRow(stringResource(R.string.rate_on_play_store), onClick = onRateApp)
            }
        }
        if (BuildConfig.DEBUG) {
            item { SectionLabel(stringResource(R.string.developer_section), Modifier.padding(horizontal = AppSpacing.large)) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(AppSpacing.large)) {
                        SettingRow(stringResource(R.string.force_pro_status), if (debugForcePro) stringResource(R.string.development_pro_status) else null, trailing = {
                            Switch(checked = debugForcePro, onCheckedChange = onDebugForceProChange)
                        })
                        if (debugForcePro) Text(stringResource(R.string.development_pro_status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        SettingRow(
                            stringResource(R.string.debug_display),
                            stringResource(R.string.debug_display_explanation),
                            trailing = {
                                Switch(
                                    checked = debugDisplayEnabled,
                                    onCheckedChange = onDebugDisplayEnabledChange,
                                    modifier = Modifier.testTag("debug_display_switch"),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    if (showWorkspaces) WorkspaceDialog(
        state = workspaceState, onDismiss = { showWorkspaces = false }, onSelect = onSelectWorkspace,
        onCreate = onCreateWorkspace, onRename = onRenameWorkspace, onMove = onMoveWorkspace,
        onDelete = onDeleteWorkspace, onLoadCounts = onLoadWorkspaceCounts,
    )
    if (showLanguages) {
        LanguageDialog(onDismiss = { showLanguages = false })
    }
    if (showRefreshIntervals) AlertDialog(
        onDismissRequest = { showRefreshIntervals = false },
        title = { Text(stringResource(R.string.scan_request_interval)) },
        text = {
            Column {
                WifiUiPreferencesRepository.REFRESH_INTERVAL_SECONDS.forEach { seconds ->
                    val interval = seconds * 1_000L
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = refreshIntervalMillis == interval,
                            onClick = { onRefreshIntervalChange(interval); showRefreshIntervals = false },
                            role = Role.RadioButton,
                        ).padding(vertical = AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(refreshIntervalMillis == interval, onClick = null)
                        Text(localizedRefreshIntervalLabel(interval))
                    }
                }
                Text(
                    stringResource(R.string.scan_interval_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { showRefreshIntervals = false }) { Text(stringResource(R.string.close)) } },
    )
    if (showDistanceUnits) ChoiceDialog(
        title = stringResource(R.string.distance_unit),
        choices = DistanceUnitPreference.entries.map { it to stringResource(if (it == DistanceUnitPreference.METERS) R.string.meters else R.string.feet) },
        selected = distanceUnit,
        onSelect = { onDistanceUnitChange(it); showDistanceUnits = false },
        onDismiss = { showDistanceUnits = false },
    )
    if (showBands) AlertDialog(
        onDismissRequest = { showBands = false },
        title = { Text(stringResource(R.string.frequency_bands)) },
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
        confirmButton = { TextButton(onClick = { showBands = false }) { Text(stringResource(R.string.done)) } },
    )
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        title = { Text(stringResource(R.string.about_app)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(stringResource(R.string.app_name))
            Text(stringResource(R.string.version_name_format, packageInfo.versionName.orEmpty()))
            Text(stringResource(R.string.version_code_format, packageInfo.longVersionCode))
            Text(stringResource(R.string.package_name_format, context.packageName))
            Text(stringResource(R.string.android_version_support))
            Text(stringResource(R.string.open_source_licenses_coming_soon))
            Text(stringResource(R.string.privacy_policy_coming_soon))
            Text(stringResource(R.string.terms_coming_soon))
            Text(stringResource(R.string.contact_coming_soon))
        } },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.close)) } },
    )
    if (showPermissions) AlertDialog(
        onDismissRequest = { showPermissions = false },
        title = { Text(stringResource(R.string.permissions)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            PermissionRow(stringResource(R.string.wifi_scan), permissionSummary.wifiScan, stringResource(R.string.wifi_permission_explanation))
            PermissionRow(stringResource(R.string.camera), permissionSummary.camera, stringResource(R.string.camera_permission_explanation))
            PermissionRow(stringResource(R.string.photo_picker), permissionSummary.photoPicker, stringResource(R.string.photo_picker_explanation))
        } },
        confirmButton = {
            if (permissionSummary.wifiScan == PermissionStatus.SETTINGS_REQUIRED) Button(onClick = onOpenAppSettings) { Text(stringResource(R.string.open_android_settings)) }
            else Button(onClick = onRequestScanPermission) { Text(stringResource(R.string.allow_wifi_permission)) }
        },
        dismissButton = { TextButton(onClick = { showPermissions = false }) { Text(stringResource(R.string.close)) } },
    )
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text(stringResource(R.string.help)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            HelpItem(stringResource(R.string.help_scan_title), stringResource(R.string.help_scan_body))
            HelpItem(stringResource(R.string.help_add_device_title), stringResource(R.string.help_add_device_body))
            HelpItem(stringResource(R.string.help_ocr_title), stringResource(R.string.help_ocr_body))
            HelpItem(stringResource(R.string.help_photos_title), stringResource(R.string.help_photos_body))
            HelpItem(stringResource(R.string.help_workspace_groups_title), stringResource(R.string.help_workspace_groups_body))
            HelpItem(stringResource(R.string.backup_and_restore), stringResource(R.string.help_backup_body))
            HelpItem(stringResource(R.string.help_csv_title), stringResource(R.string.help_csv_body))
            HelpItem(stringResource(R.string.faq), stringResource(R.string.help_faq_body))
            HelpItem(stringResource(R.string.about_permissions), stringResource(R.string.wifi_permission_explanation))
        } },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.close)) } },
    )
    if (showPrivacy) AlertDialog(
        onDismissRequest = { showPrivacy = false },
        title = { Text(stringResource(R.string.privacy)) },
        text = { Text(stringResource(R.string.privacy_summary)) },
        confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.close)) } },
    )
}

@Composable private fun PermissionStatus.label() = when (this) {
    PermissionStatus.GRANTED -> stringResource(R.string.permission_granted)
    PermissionStatus.NOT_GRANTED -> stringResource(R.string.permission_not_granted)
    PermissionStatus.PARTIALLY_GRANTED -> stringResource(R.string.permission_partially_granted)
    PermissionStatus.SETTINGS_REQUIRED -> stringResource(R.string.permission_settings_required)
}

@Composable private fun PermissionRow(title: String, status: PermissionStatus, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.permission_status_format, title, status.label()), style = MaterialTheme.typography.titleSmall)
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HelpItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun localizedRefreshIntervalLabel(milliseconds: Long): String = when (milliseconds) {
    60_000L -> stringResource(R.string.duration_1_minute)
    120_000L -> stringResource(R.string.duration_2_minutes)
    300_000L -> stringResource(R.string.duration_5_minutes)
    else -> stringResource(R.string.duration_seconds, milliseconds / 1_000L)
}

internal fun refreshIntervalLabel(milliseconds: Long): String = when (milliseconds) {
    3_000L -> "3秒"; 5_000L -> "5秒"; 10_000L -> "10秒"; 15_000L -> "15秒"; 20_000L -> "20秒"
    30_000L -> "30秒"; 60_000L -> "1分"; 120_000L -> "2分"; 300_000L -> "5分"
    else -> "${milliseconds / 1_000L}秒"
}

internal fun visibleBandLabel(bands: Set<WifiBand>): String = WifiBand.entries.filter { it in bands }.joinToString(" / ") {
    when (it) { WifiBand.BAND_24 -> "2.4"; WifiBand.BAND_5 -> "5"; WifiBand.BAND_6 -> "6" }
} + " GHz"

private data class AppLanguageChoice(val tag: String, val labelRes: Int)

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val selectedTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val choices = listOf(
        AppLanguageChoice("", R.string.app_language_system_default),
        AppLanguageChoice("ja", R.string.app_language_japanese),
        AppLanguageChoice("en", R.string.app_language_english),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_language)) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag("app_language_${choice.tag.ifEmpty { "system" }}")
                            .selectable(
                                selected = selectedTag == choice.tag,
                                onClick = {
                                    onDismiss()
                                    AppCompatDelegate.setApplicationLocales(
                                        if (choice.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                        else LocaleListCompat.forLanguageTags(choice.tag),
                                    )
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedTag == choice.tag, onClick = null)
                        Text(stringResource(choice.labelRes))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun <T> ChoiceDialog(title: String, choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column {
        choices.forEach { (choice, label) -> Row(
            Modifier.fillMaxWidth().selectable(choice == selected, onClick = { onSelect(choice) }, role = Role.RadioButton).padding(vertical = AppSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) { RadioButton(choice == selected, null); Text(label) } }
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
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
        title = { Text(stringResource(R.string.workspace)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.current_workspace_format, state.selected?.name ?: stringResource(R.string.not_available)), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it }, Modifier.weight(1f).testTag("workspace_name"), label = { Text(stringResource(R.string.name_to_add)) }, singleLine = true)
                IconButton(enabled = !state.busy && newName.isNotBlank(), onClick = { onCreate(newName); newName = "" }, modifier = Modifier.testTag("workspace_add")) { Icon(Icons.Rounded.Add, stringResource(R.string.add_workspace)) }
            }
            state.workspaces.forEachIndexed { index, workspace ->
                Card(
                    onClick = { onSelect(workspace.id) },
                    colors = CardDefaults.cardColors(containerColor = if (workspace.id == state.selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("workspace_${workspace.id}"),
                ) { Row(Modifier.fillMaxWidth().padding(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (workspace.id == state.selectedId) stringResource(R.string.selected_prefix) else "") + workspace.name, Modifier.weight(1f))
                    IconButton(enabled = index > 0 && !state.busy, onClick = { onMove(workspace.id, -1) }) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.move_up)) }
                    IconButton(enabled = index < state.workspaces.lastIndex && !state.busy, onClick = { onMove(workspace.id, 1) }) { Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.move_down)) }
                    IconButton(enabled = !state.busy, onClick = { editing = workspace }) { Icon(Icons.Rounded.Edit, stringResource(R.string.rename)) }
                    IconButton(enabled = !state.busy, onClick = { onLoadCounts(workspace.id); deleting = workspace }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                } }
            }
        } },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    editing?.let { workspace ->
        var value by remember(workspace.id) { mutableStateOf(workspace.name) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(stringResource(R.string.rename)) }, text = { OutlinedTextField(value, { value = it }, singleLine = true) }, confirmButton = { Button(onClick = { onRename(workspace.id, value); editing = null }) { Text(stringResource(R.string.change)) } }, dismissButton = { Button(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } })
    }
    deleting?.let { workspace ->
        val counts = state.deleteCounts[workspace.id]
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_workspace_title, workspace.name)) }, text = { Text(stringResource(R.string.delete_workspace_message, counts?.devices ?: 0, counts?.groups ?: 0, counts?.photos ?: 0)) }, confirmButton = { Button(enabled = !state.busy, onClick = { onDelete(workspace.id); deleting = null }) { Text(stringResource(R.string.delete)) } }, dismissButton = { Button(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
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
private fun SettingRow(title: String, value: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().then(if (onClick != null) Modifier.selectable(false, onClick = onClick) else Modifier).padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        value?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        trailing?.invoke() ?: Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() = WifiAnalyzerTheme { SettingsScreen(ThemeUiState(), {}, {}, {}) }
