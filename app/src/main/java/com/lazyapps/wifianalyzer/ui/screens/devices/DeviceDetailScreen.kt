package com.lazyapps.wifianalyzer.ui.screens.devices

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.lazyapps.wifianalyzer.domain.DetectionPolicy
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.ui.components.ScreenHeader
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import java.util.Date

@Composable
fun DeviceDetailScreen(
    device: RegisteredDevice?,
    detectedAccessPoints: List<WifiAccessPoint>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMonitor: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var chooseMonitor by remember { mutableStateOf(false) }
    if (device == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("機器詳細", action = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } })
            Text("機器が見つかりません", Modifier.padding(AppSpacing.large))
        }
        return
    }
    val detected = detectedAccessPoints.isNotEmpty() || DetectionPolicy.isDetected(device.lastSeenAt, System.currentTimeMillis())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        item {
            ScreenHeader(device.displayName, if (detected) "現在検出中" else "未検出", action = {
                Row {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") }
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_device")) { Icon(Icons.Rounded.Edit, "編集") }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_device")) { Icon(Icons.Rounded.Delete, "削除") }
                }
            })
        }
        if (detectedAccessPoints.isNotEmpty()) {
            item {
                Button(onClick = {
                    if (detectedAccessPoints.size == 1) onMonitor(detectedAccessPoints.first().bssid) else chooseMonitor = true
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large)) {
                    Icon(Icons.Rounded.ShowChart, null)
                    Text("シグナルモニターを開く")
                }
            }
        }
        item {
            DetailCard("検出情報") {
                DetailLine("状態", if (detected) "現在検出中" else "未検出")
                DetailLine("最新RSSI", device.lastSeenRssi?.let { "$it dBm" } ?: "—")
                DetailLine("最終検出", relativeDate(device.lastSeenAt))
                val distance = detectedAccessPoints.maxByOrNull { it.rssi }?.distanceRange?.label ?: "—"
                DetailLine("推定距離", distance)
            }
        }
        item {
            DetailCard("機器情報") {
                DetailLine("メーカー", device.manufacturer.ifBlank { "—" })
                DetailLine("型番", device.model.ifBlank { "—" })
                DetailLine("シリアル番号", device.serialNumber.ifBlank { "—" })
                DetailLine("SSID", device.ssid.ifBlank { "—" })
                DetailLine("グループ", device.groupName ?: "未分類")
                DetailLine("設置場所", device.location.ifBlank { "—" })
                DetailLine("メモ", device.notes.ifBlank { "—" })
            }
        }
        item {
            DetailCard("BSSID一覧") {
                device.bssids.forEachIndexed { index, bssid ->
                    Text("${if (index == 0) "主 · " else ""}${bssid.bssid}", style = MaterialTheme.typography.bodyMedium)
                    Text("${bssid.band}${bssid.label.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            DetailCard("記録") {
                DetailLine("作成日時", absoluteDate(device.createdAt))
                DetailLine("更新日時", absoluteDate(device.updatedAt))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("機器を削除しますか？") },
            text = { Text("関連するBSSIDも削除されます。この操作は元に戻せません。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } },
        )
    }
    if (chooseMonitor) {
        AlertDialog(
            onDismissRequest = { chooseMonitor = false },
            title = { Text("モニターするBSSID") },
            text = {
                Column {
                    detectedAccessPoints.forEach { ap ->
                        TextButton(onClick = { chooseMonitor = false; onMonitor(ap.bssid) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${ap.bssid} · ${ap.rssi} dBm")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseMonitor = false }) { Text("閉じる") } },
        )
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text(label, Modifier.weight(.35f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(.65f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun absoluteDate(timestamp: Long): String = DateFormat.getMediumDateFormat(LocalContext.current).format(Date(timestamp)) + " " + DateFormat.getTimeFormat(LocalContext.current).format(Date(timestamp))

private fun relativeDate(timestamp: Long?): String = timestamp?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() } ?: "—"
