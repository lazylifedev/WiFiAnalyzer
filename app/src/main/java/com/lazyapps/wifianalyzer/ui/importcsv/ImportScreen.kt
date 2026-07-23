package com.lazyapps.wifianalyzer.ui.importcsv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.wifianalyzer.importcsv.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ImportScreen(onBack: () -> Unit, vm: ImportViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::select) }
    val errorSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::writeErrorCsv) }
    Scaffold(topBar = { TopAppBar(title = { Text("CSVからインポート") }, navigationIcon = { TextButton(onClick = if (state.step == ImportStep.SELECT) onBack else vm::back) { Text("戻る") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("csv_import_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stepLabel(state.step), style = MaterialTheme.typography.headlineSmall); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            when (state.step) {
                ImportStep.SELECT -> item { Button(onClick = { picker.launch(arrayOf("text/csv", "text/plain", "application/vnd.ms-excel", "*/*")) }, modifier = Modifier.testTag("select_csv")) { Text("CSVを選択") } }
                ImportStep.ENCODING -> item { Text(state.fileName); Text("検出: ${state.encoding.label}${if (state.hasBom) " (BOM)" else ""}"); ChoiceRow("UTF-8", state.encoding == CsvEncoding.UTF8) { vm.setEncoding(CsvEncoding.UTF8) }; ChoiceRow("Windows-31J", state.encoding == CsvEncoding.WINDOWS_31J) { vm.setEncoding(CsvEncoding.WINDOWS_31J) }; NextButton(vm::next) }
                ImportStep.MAPPING -> { items(state.headers.indices.toList()) { index -> MappingRow(state.headers[index], state.samples.firstOrNull()?.getOrNull(index).orEmpty(), state.mapping[index]) { vm.setMapping(index, it) } }; item { NextButton(vm::next) } }
                ImportStep.SETTINGS -> item { Settings(state.settings, vm::setSettings); NextButton(vm::next) }
                ImportStep.PREVIEW -> { val p = state.preview!!; item { Text("総行数 ${p.total} / 新規 ${p.additions} / 更新 ${p.updates} / スキップ ${p.skips} / エラー ${p.errors} / 競合 ${p.conflicts}"); Text("作成予定: ワークスペース ${p.workspaceNames.size}、グループ ${p.groupNames.size} / 警告 ${p.warnings}"); FilterChip(state.showErrorsOnly, vm::toggleErrors, { Text("エラー・競合のみ") }) }; items(p.rows.filter { !state.showErrorsOnly || it.status in setOf(ImportRowStatus.ERROR, ImportRowStatus.CONFLICT) }.take(50)) { Text("${it.source.sourceRow}: ${it.status}  ${it.source.deviceName} ${it.messages.joinToString()}") }; item { NextButton(vm::next) } }
                ImportStep.CONFIRM -> item { val p = state.preview!!; Text("${p.additions + p.updates}件をインポートします。削除は行いません。${if (state.settings.errorMode == ErrorMode.VALID_ROWS_ONLY) "エラー行を除外して実行します。" else "エラー時は全件ロールバックします。"}"); Button(onClick = vm::execute, enabled = !state.busy, modifier = Modifier.testTag("execute_import")) { Text("インポート実行") } }
                ImportStep.IMPORTING -> item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("インポート中…"); OutlinedButton(onClick = vm::cancel) { Text("キャンセル") } }
                ImportStep.RESULT -> item { val r = state.result!!; Text("追加 ${r.added} / 更新 ${r.updated} / スキップ ${r.skipped} / エラー ${r.errors}"); Text("作成ワークスペース ${r.workspacesCreated} / 作成グループ ${r.groupsCreated} / 登録BSSID ${r.bssidsRegistered}"); Text("実行時間 ${r.elapsedMillis}ms"); if (r.errors > 0) OutlinedButton(onClick = { errorSaver.launch("WiFiAnalyzer_ImportErrors.csv") }, modifier = Modifier.testTag("save_import_errors")) { Text("エラー結果をCSV保存") }; Button(onClick = onBack) { Text("完了") } }
            }
        }
    }
}

@Composable private fun NextButton(onClick: () -> Unit) = Button(onClick, Modifier.testTag("import_next")) { Text("次へ") }
@Composable private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) = Row { RadioButton(selected, onClick); Text(label, Modifier.padding(top = 12.dp)) }
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MappingRow(header: String, sample: String, selected: ImportField, onSelect: (ImportField) -> Unit) { var open by remember { mutableStateOf(false) }; Column { Text("$header  例: ${sample.take(50)}"); ExposedDropdownMenuBox(open, { open = it }) { OutlinedTextField(selected.label, {}, readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(open, { open = false }) { ImportField.entries.forEach { field -> DropdownMenuItem({ Text(field.label) }, { onSelect(field); open = false }) } } } } }
@Composable private fun Settings(s: ImportSettings, set: (ImportSettings) -> Unit) { Text("インポート方式"); ChoiceRow("新規追加のみ", s.mode == ImportMode.ADD_ONLY) { set(s.copy(mode = ImportMode.ADD_ONLY)) }; ChoiceRow("追加＋更新", s.mode == ImportMode.ADD_AND_UPDATE) { set(s.copy(mode = ImportMode.ADD_AND_UPDATE)) }; Text("照合キー"); enumChoices(MatchKey.entries, s.matchKey) { set(s.copy(matchKey = it)) }; Text("ワークスペース"); ChoiceRow("CSV列を使用", s.workspaceMode == WorkspaceMode.CSV) { set(s.copy(workspaceMode = WorkspaceMode.CSV)) }; ChoiceRow("現在のワークスペース", s.workspaceMode == WorkspaceMode.CURRENT) { set(s.copy(workspaceMode = WorkspaceMode.CURRENT)) }; Text("エラー行"); ChoiceRow("1件でもあれば全件中止", s.errorMode == ErrorMode.ABORT_ALL) { set(s.copy(errorMode = ErrorMode.ABORT_ALL)) }; ChoiceRow("正常行だけ取り込む", s.errorMode == ErrorMode.VALID_ROWS_ONLY) { set(s.copy(errorMode = ErrorMode.VALID_ROWS_ONLY)) }; Text("空欄セル"); ChoiceRow("既存値を維持", s.blankMode == BlankMode.KEEP) { set(s.copy(blankMode = BlankMode.KEEP)) }; ChoiceRow("既存値を消去", s.blankMode == BlankMode.CLEAR) { set(s.copy(blankMode = BlankMode.CLEAR)) }; Text("BSSID更新"); ChoiceRow("既存BSSIDへ追加", s.bssidMode == BssidUpdateMode.APPEND) { set(s.copy(bssidMode = BssidUpdateMode.APPEND)) }; ChoiceRow("CSVのBSSIDへ置換", s.bssidMode == BssidUpdateMode.REPLACE) { set(s.copy(bssidMode = BssidUpdateMode.REPLACE)) } }
@Composable private fun <T> enumChoices(values: List<T>, selected: T, set: (T) -> Unit) { values.forEach { ChoiceRow(it.toString(), selected == it) { set(it) } } }
private fun stepLabel(step: ImportStep) = when(step) { ImportStep.SELECT -> "1. CSVを選択"; ImportStep.ENCODING -> "2. 文字コード確認"; ImportStep.MAPPING -> "3. 列マッピング"; ImportStep.SETTINGS -> "4. 取込設定"; ImportStep.PREVIEW -> "5. プレビュー"; ImportStep.CONFIRM -> "6. 実行確認"; ImportStep.IMPORTING -> "7. インポート"; ImportStep.RESULT -> "8. 結果" }
