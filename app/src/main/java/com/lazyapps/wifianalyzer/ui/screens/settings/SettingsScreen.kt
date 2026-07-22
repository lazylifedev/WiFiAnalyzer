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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.components.SectionLabel
import com.lazyapps.wifianalyzer.ui.theme.AccentColor
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.ThemeUiState
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.ui.theme.lightSeed

@Composable
fun SettingsScreen(
    state: ThemeUiState,
    onModeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentColor) -> Unit,
    onAnimationChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item { ScreenHeader(stringResource(R.string.screen_settings)) }
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
                SettingRow(stringResource(R.string.distance_unit), stringResource(R.string.distance_unit_value))
                SettingRow(stringResource(R.string.frequency_bands), stringResource(R.string.frequency_bands_value))
                SettingRow(stringResource(R.string.animations), trailing = { Switch(state.animationsEnabled, onAnimationChange) })
            }
        }
        item { SectionLabel(stringResource(R.string.other_section), Modifier.padding(horizontal = AppSpacing.large)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                SettingRow(stringResource(R.string.about_app), stringResource(R.string.about_value))
            }
        }
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
private fun SettingRow(title: String, value: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        value?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        trailing?.invoke() ?: Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() = WifiAnalyzerTheme { SettingsScreen(ThemeUiState(), {}, {}, {}) }
