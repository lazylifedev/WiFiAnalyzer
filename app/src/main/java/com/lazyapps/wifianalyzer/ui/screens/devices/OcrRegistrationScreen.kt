package com.lazyapps.wifianalyzer.ui.screens.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrRegistrationScreen(onBack: () -> Unit) {
    var manufacturer by remember { mutableStateOf("Ruckus Networks") }
    var model by remember { mutableStateOf("AX3000-AP") }
    var serial by remember { mutableStateOf("A1B2C3D4E5F6G7H8") }
    var mac by remember { mutableStateOf("38:45:3B:05:DD:C8") }
    var ssid by remember { mutableStateOf("Office-AP_5G") }
    var group by remember { mutableStateOf("本社") }
    var confirmed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ocr_title)) },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("ocr_back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(250.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Icon(Icons.Rounded.CameraAlt, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.camera_preview_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier.fillMaxWidth(.78f).height(132.dp).border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                )
                Text(
                    stringResource(R.string.ocr_instruction),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(AppSpacing.medium),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    Text(stringResource(R.string.ocr_result), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.ocr_result_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ResultField(stringResource(R.string.manufacturer), manufacturer, { manufacturer = it })
                    ResultField(stringResource(R.string.model), model, { model = it })
                    ResultField(stringResource(R.string.serial_number), serial, { serial = it })
                    ResultField(stringResource(R.string.mac_bssid), mac, { mac = it })
                    ResultField(stringResource(R.string.ssid), ssid, { ssid = it })
                    ResultField(stringResource(R.string.group), group, { group = it })
                    Button(onClick = { confirmed = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.register_device))
                    }
                    if (confirmed) Text(stringResource(R.string.register_complete), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ResultField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Preview(showBackground = true, backgroundColor = 0xFF07111F)
@Composable
private fun OcrPreview() = WifiAnalyzerTheme(mode = ThemeMode.DARK) { OcrRegistrationScreen({}) }
