package com.lazyapps.wifianalyzer.ui.importcsv

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository
import com.lazyapps.wifianalyzer.importcsv.*
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter

enum class ImportStep { SELECT, ENCODING, MAPPING, SETTINGS, PREVIEW, CONFIRM, IMPORTING, RESULT }
data class ImportUiState(
    val step: ImportStep = ImportStep.SELECT, val fileName: String = "", val encoding: CsvEncoding = CsvEncoding.UTF8,
    val hasBom: Boolean = false, val headers: List<String> = emptyList(), val samples: List<List<String>> = emptyList(),
    val mapping: List<ImportField> = emptyList(), val settings: ImportSettings = ImportSettings(), val preview: ImportPreview? = null,
    val result: ImportResult? = null, val showErrorsOnly: Boolean = false, val busy: Boolean = false, val error: String? = null,
)

class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WifiAnalyzerDatabase.get(application)
    private val repository = ImportRepository(database)
    private val preferences = ImportPreferences(application)
    private val workspaceRepository = WorkspaceRepository(application, database)
    private val mutable = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = mutable.asStateFlow()
    private var bytes: ByteArray? = null
    private var parsedRows: List<CsvRecord> = emptyList()
    private var currentWorkspaceId = 0L
    private var job: Job? = null

    init { viewModelScope.launch { currentWorkspaceId = workspaceRepository.snapshot.first().selectedId; mutable.value = mutable.value.copy(settings = preferences.loadSettings()) } }

    fun select(uri: Uri) { job?.cancel(); job = viewModelScope.launch {
        mutable.value = ImportUiState(busy = true, settings = mutable.value.settings)
        runCatching { withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "import.csv"
            val data = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(32 * 1024); var total = 0L
                while (true) { val read = input.read(buffer); if (read < 0) break; total += read; if (total > ImportLimits.MAX_FILE_BYTES) error("ファイルサイズの上限50MBを超えています"); out.write(buffer, 0, read) }
                out.toByteArray()
            } ?: error("CSVを開けません")
            Triple(name, data, CsvEncodingDetector.detect(data))
        } }.onSuccess { (name, data, detection) -> bytes = data; parse(name, detection.encoding, detection.hasBom) }
            .onFailure { mutable.value = mutable.value.copy(busy = false, error = userMessage(it)) }
    } }

    fun setEncoding(encoding: CsvEncoding) { val name = mutable.value.fileName; parse(name, encoding, encoding == CsvEncoding.UTF8 && mutable.value.hasBom) }
    private fun parse(name: String, encoding: CsvEncoding, hasBom: Boolean) { viewModelScope.launch {
        mutable.value = mutable.value.copy(busy = true, error = null)
        runCatching { withContext(Dispatchers.Default) { CsvImportParser().readAll(ByteArrayInputStream(bytes ?: error("CSVがありません")), encoding, hasBom) } }
            .onSuccess { records ->
                require(records.isNotEmpty()) { "CSVが空です" }; val width = records.first().cells.size
                val mismatch = records.drop(1).firstOrNull { it.cells.size != width }; require(mismatch == null) { "${mismatch?.rowNumber}行目の列数が一致しません" }
                parsedRows = records.drop(1); val mapping = preferences.loadMapping(records.first().cells) ?: CsvColumnMapper.autoMap(records.first().cells)
                mutable.value = mutable.value.copy(step = ImportStep.ENCODING, fileName = name, encoding = encoding, hasBom = hasBom, headers = records.first().cells,
                    samples = records.drop(1).take(3).map { it.cells }, mapping = mapping, preview = null, result = null, busy = false)
            }.onFailure { mutable.value = mutable.value.copy(busy = false, error = userMessage(it)) }
    } }
    fun next() { when (mutable.value.step) {
        ImportStep.ENCODING -> mutable.value = mutable.value.copy(step = ImportStep.MAPPING)
        ImportStep.MAPPING -> { val errors = CsvColumnMapper.validate(mutable.value.mapping); if (errors.isEmpty()) { viewModelScope.launch { preferences.saveMapping(mutable.value.headers, mutable.value.mapping) }; mutable.value = mutable.value.copy(step = ImportStep.SETTINGS) } else mutable.value = mutable.value.copy(error = errors.joinToString("\n")) }
        ImportStep.SETTINGS -> buildPreview()
        ImportStep.PREVIEW -> mutable.value = mutable.value.copy(step = ImportStep.CONFIRM)
        else -> Unit
    } }
    fun back() { mutable.value = mutable.value.copy(step = when (mutable.value.step) { ImportStep.ENCODING -> ImportStep.SELECT; ImportStep.MAPPING -> ImportStep.ENCODING; ImportStep.SETTINGS -> ImportStep.MAPPING; ImportStep.PREVIEW -> ImportStep.SETTINGS; ImportStep.CONFIRM -> ImportStep.PREVIEW; else -> mutable.value.step }, error = null) }
    fun setMapping(index: Int, field: ImportField) { val list = mutable.value.mapping.toMutableList(); list[index] = field; mutable.value = mutable.value.copy(mapping = list, error = null) }
    fun setSettings(settings: ImportSettings) { mutable.value = mutable.value.copy(settings = settings, preview = null) }
    fun toggleErrors() { mutable.value = mutable.value.copy(showErrorsOnly = !mutable.value.showErrorsOnly) }
    private fun buildPreview() { job = viewModelScope.launch { mutable.value = mutable.value.copy(busy = true, error = null)
        runCatching { val rows = withContext(Dispatchers.Default) { parsedRows.map { ImportValidator.map(it, mutable.value.mapping) } }; repository.plan(rows, mutable.value.settings, currentWorkspaceId) }
            .onSuccess { mutable.value = mutable.value.copy(step = ImportStep.PREVIEW, preview = it, busy = false) }
            .onFailure { mutable.value = mutable.value.copy(busy = false, error = userMessage(it)) }
    } }
    fun execute() { if (mutable.value.busy) return; job = viewModelScope.launch { val preview = mutable.value.preview ?: return@launch; val settings = mutable.value.settings
        mutable.value = mutable.value.copy(step = ImportStep.IMPORTING, busy = true, error = null)
        runCatching { repository.execute(preview, settings) }.onSuccess { result -> preferences.save(mutable.value.fileName, settings, result, true); mutable.value = mutable.value.copy(step = ImportStep.RESULT, result = result, busy = false) }
            .onFailure { preferences.save(mutable.value.fileName, settings, null, false); mutable.value = mutable.value.copy(step = ImportStep.CONFIRM, busy = false, error = userMessage(it)) }
    } }
    fun cancel() { job?.cancel(); mutable.value = mutable.value.copy(step = if (mutable.value.preview == null) ImportStep.SELECT else ImportStep.PREVIEW, busy = false, error = "処理をキャンセルしました") }
    fun writeErrorCsv(uri: Uri) { viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write("\uFEFF元行番号,行状態,エラー内容,ワークスペース,機器名,シリアル番号,主BSSID\r\n")
                    mutable.value.preview?.rows?.filter { it.status in setOf(ImportRowStatus.ERROR, ImportRowStatus.CONFLICT) }?.forEach { row ->
                        val values = listOf(row.source.sourceRow.toString(), row.status.name, row.messages.joinToString(" / "), row.source.workspace, row.source.deviceName, row.source.serial, row.source.primaryBssid)
                        writer.write(values.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" } + "\r\n")
                    }
                }
            } ?: error("保存先を開けません")
        } }.onFailure { mutable.value = mutable.value.copy(error = userMessage(it)) }
    } }
    private fun userMessage(error: Throwable): String =
        getApplication<Application>().getString(OperationErrorMapper.classify(error).messageRes)
}
