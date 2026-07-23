package com.lazyapps.wifianalyzer.ui.export

import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lazyapps.wifianalyzer.export.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    var showColumns by remember { mutableStateOf(false) }; var showSensitiveWarning by remember { mutableStateOf(false) }; var showReport by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) scope.launch { viewModel.writeCsv(uri) } }
    fun shareCsv() { scope.launch { viewModel.shareFile().onSuccess { file -> val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); val intent = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); runCatching { context.startActivity(Intent.createChooser(intent, "CSVを共有")) }.onFailure { viewModel.clearNotice() } } } }
    Scaffold(topBar = { TopAppBar(title = { Text("データのエクスポート") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("export_screen"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("種類", style = MaterialTheme.typography.titleMedium); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { ExportType.entries.forEachIndexed { i, type -> SegmentedButton(selected = state.type == type, onClick = { viewModel.setType(type) }, shape = SegmentedButtonDefaults.itemShape(i, ExportType.entries.size), label = { Text(type.label, maxLines = 2) }) } } }
            item { Text("出力対象", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(state.scope == ExportScope.CURRENT_WORKSPACE, { viewModel.setScope(ExportScope.CURRENT_WORKSPACE) }, { Text("現在") }); FilterChip(state.scope == ExportScope.ALL_WORKSPACES, { viewModel.setScope(ExportScope.ALL_WORKSPACES) }, { Text("すべて") }) }; Text(if (state.scope == ExportScope.ALL_WORKSPACES) "すべてのワークスペース" else "ワークスペース: ${state.workspaceName}") }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(state.groupId == null && !state.ungroupedOnly, { viewModel.setGroup(null) }, { Text("全グループ") }); FilterChip(state.ungroupedOnly, { viewModel.setGroup(null, true) }, { Text("未分類") }); state.groups.forEach { (id, name) -> FilterChip(state.groupId == id, { viewModel.setGroup(id) }, { Text(name) }) } } }
            state.dataset?.let { data -> item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("機器 ${data.counts.devices}台 ・ BSSID ${data.counts.bssids}件 ・ 写真 ${data.counts.photos}枚"); Text("出力行数: ${data.rows.size}件") } } } }
            if (state.busy) { item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(28.dp)); Text("処理中…"); if (state.type == ExportType.REPORT) TextButton(onClick = viewModel::cancel) { Text("キャンセル") } } } }
            if (state.type != ExportType.REPORT) {
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("列設定", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); TextButton(onClick = { showColumns = true }, modifier = Modifier.testTag("column_settings")) { Icon(Icons.Rounded.ViewColumn, null); Text("${viewModel.activeColumns().size}列") } } }
                item { Text("プレビュー（先頭20件）", style = MaterialTheme.typography.titleMedium); Surface(Modifier.fillMaxWidth().heightIn(max = 300.dp).horizontalScroll(rememberScrollState()), tonalElevation = 1.dp) { Text(state.preview.ifBlank { "データがありません" }, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }; Text("UTF-8 BOM / カンマ区切り / CRLF / 全フィールドをダブルクォート", style = MaterialTheme.typography.bodySmall); Text("推定ファイルサイズ: ${formatBytes(state.dataset?.estimatedBytes ?: 0)}", style = MaterialTheme.typography.bodySmall) }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { OutlinedButton(enabled = !state.busy && (state.dataset?.rows?.isNotEmpty() == true), onClick = { saveLauncher.launch(viewModel.suggestedFileName()) }, modifier = Modifier.testTag("save_csv")) { Icon(Icons.Rounded.Save, null); Text("保存") }; Spacer(Modifier.width(8.dp)); Button(enabled = !state.busy && (state.dataset?.rows?.isNotEmpty() == true), onClick = { showSensitiveWarning = true }, modifier = Modifier.testTag("share_csv")) { Icon(Icons.Rounded.Share, null); Text("共有") } } }
            } else {
                item { Text("写真", style = MaterialTheme.typography.titleMedium); ReportPhotoMode.entries.forEach { mode -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(state.reportPhotoMode == mode, { viewModel.setPhotoMode(mode) }); Text(when (mode) { ReportPhotoMode.NONE -> "含めない"; ReportPhotoMode.PRIMARY -> "メイン写真だけ"; ReportPhotoMode.ALL -> "すべての写真" }) } }; if (state.reportPhotoMode != ReportPhotoMode.NONE) Text("写真にはパスワードなどの機密情報が写る可能性があります。内容を確認してから生成してください。", color = MaterialTheme.colorScheme.error); if (state.reportPhotoMode == ReportPhotoMode.ALL) Text("写真 ${state.dataset?.counts?.photos ?: 0}枚 / 元ファイル合計 ${formatBytes(state.dataset?.estimatedBytes ?: 0)}（レポート埋め込みは安全のため最大12 MB）", style = MaterialTheme.typography.bodySmall) }
                item { Button(enabled = !state.busy, onClick = { viewModel.generateReport(); showReport = true }, modifier = Modifier.testTag("generate_report")) { Icon(Icons.Rounded.Description, null); Text("レポートをプレビュー") } }
            }
            item { state.history.lastCsvAt?.let { Text("最終CSV出力: ${com.lazyapps.wifianalyzer.export.ExportFormat.dateTime(it)} / ${state.history.lastCsvType?.label} / ${state.history.lastCsvCount}件") }; state.history.lastReportAt?.let { Text("最終レポート: ${com.lazyapps.wifianalyzer.export.ExportFormat.dateTime(it)} / ${state.history.lastReportTarget}") } }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }; state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        }
    }
    if (showColumns) ColumnDialog(state, viewModel) { showColumns = false }
    if (showSensitiveWarning) AlertDialog(onDismissRequest = { showSensitiveWarning = false }, title = { Text("共有前の確認") }, text = { Text("CSVにはSSID、BSSID、シリアル番号、メモなどの機密情報が含まれる可能性があります。共有先を確認してください。") }, confirmButton = { TextButton(onClick = { showSensitiveWarning = false; shareCsv() }) { Text("共有を続ける") } }, dismissButton = { TextButton(onClick = { showSensitiveWarning = false }) { Text("キャンセル") } })
    if (showReport) state.reportHtml?.let { ReportDialog(it, onShare = { scope.launch { viewModel.shareReportFile().onSuccess { file -> val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); val intent = Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); runCatching { context.startActivity(Intent.createChooser(intent, "レポートを共有")) } } } }, onDismiss = { showReport = false }) }
}

@Composable private fun ColumnDialog(state: ExportUiState, vm: ExportViewModel, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("列設定") }, text = { Column { Row(Modifier.horizontalScroll(rememberScrollState())) { TextButton(onClick = vm::selectAll) { Text("全選択") }; TextButton(onClick = vm::minimum) { Text("最小構成") }; TextButton(onClick = vm::standard) { Text("標準へ戻す") } }; LazyColumn(Modifier.heightIn(max = 480.dp)) { items(state.preset.order, key = { it }) { key -> val c = ExportColumns.forType(state.type).first { it.key == key }; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(key in state.preset.enabled, { vm.toggleColumn(key) }); Text(c.header, Modifier.weight(1f)); IconButton(onClick = { vm.moveColumn(key, -1) }) { Icon(Icons.Rounded.ArrowUpward, "上へ移動") }; IconButton(onClick = { vm.moveColumn(key, 1) }) { Icon(Icons.Rounded.ArrowDownward, "下へ移動") } } } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("完了") } }) }

@Composable private fun ReportDialog(html: String, onShare: () -> Unit, onDismiss: () -> Unit) { val context = LocalContext.current; var webView by remember { mutableStateOf<WebView?>(null) }; Dialog(onDismissRequest = onDismiss) { Surface(Modifier.fillMaxSize()) { Column { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("レポートプレビュー", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleMedium); IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "レポートを共有") }; IconButton(onClick = { webView?.let { view -> val manager = context.getSystemService(PrintManager::class.java); manager.print("WiFiAnalyzer_Report", view.createPrintDocumentAdapter("WiFiAnalyzer_Report"), PrintAttributes.Builder().build()) } }) { Icon(Icons.Rounded.Print, "印刷またはPDF保存") }; IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "閉じる") } }; AndroidView(factory = { WebView(it).apply { settings.javaScriptEnabled = false; loadDataWithBaseURL(null, html, "text/html", "UTF-8", null); webView = this } }, modifier = Modifier.fillMaxSize()) } } } }

private fun formatBytes(bytes: Long): String = when { bytes >= 1024 * 1024 -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1024.0 / 1024.0); bytes >= 1024 -> "%.1f KB".format(java.util.Locale.ROOT, bytes / 1024.0); else -> "$bytes B" }
