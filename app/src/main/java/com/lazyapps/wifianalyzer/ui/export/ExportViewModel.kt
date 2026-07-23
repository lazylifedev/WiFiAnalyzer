package com.lazyapps.wifianalyzer.ui.export

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.data.WifiUiPreferencesRepository
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository
import com.lazyapps.wifianalyzer.export.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class ExportUiState(
    val type: ExportType = ExportType.DEVICES, val scope: ExportScope = ExportScope.CURRENT_WORKSPACE, val workspaceId: Long = 0,
    val workspaceName: String = "", val targetLabel: String = "", val groupId: Long? = null, val ungroupedOnly: Boolean = false,
    val groups: List<Pair<Long, String>> = emptyList(),
    val preset: ColumnPreset = ColumnPreset(emptyList(), emptySet()), val dataset: ExportDataset? = null, val preview: String = "",
    val reportPhotoMode: ReportPhotoMode = ReportPhotoMode.PRIMARY, val reportHtml: String? = null, val history: ExportHistory = ExportHistory(),
    val busy: Boolean = false, val error: String? = null, val message: String? = null,
)

class ExportViewModel(application: Application) : AndroidViewModel(application) {
    private val db = WifiAnalyzerDatabase.get(application)
    private val workspaceRepository = WorkspaceRepository(application, db)
    private val repository = ExportRepository(application, db)
    private val preferences = ExportPreferences(application)
    private val wifiPreferences = WifiUiPreferencesRepository(application)
    private val mutable = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = mutable.asStateFlow()
    private var reportJob: Job? = null

    init { viewModelScope.launch { val ws = workspaceRepository.snapshot.first(); mutable.value = mutable.value.copy(workspaceId = ws.selectedId, workspaceName = ws.selected?.name.orEmpty(), groups = db.registryDao().getGroupsOnce(ws.selectedId).map { it.id to it.name }, history = preferences.history()); loadPresetAndData() } }
    fun setType(type: ExportType) { mutable.value = mutable.value.copy(type = type, reportHtml = null); viewModelScope.launch { loadPresetAndData() } }
    fun setScope(scope: ExportScope) { mutable.value = mutable.value.copy(scope = scope); refresh() }
    fun setGroup(groupId: Long?, ungrouped: Boolean = false) { mutable.value = mutable.value.copy(groupId = groupId, ungroupedOnly = ungrouped); refresh() }
    fun setPhotoMode(mode: ReportPhotoMode) { mutable.value = mutable.value.copy(reportPhotoMode = mode, reportHtml = null) }
    fun toggleColumn(key: String) { val s = mutable.value; val required = requiredKeys(s); val enabled = if (key in s.preset.enabled && key !in required && s.preset.enabled.size > 1) s.preset.enabled - key else s.preset.enabled + key; savePreset(s.preset.copy(enabled = enabled)) }
    fun moveColumn(key: String, delta: Int) { val s = mutable.value; val list = s.preset.order.toMutableList(); val from = list.indexOf(key); val to = from + delta; if (from !in list.indices || to !in list.indices) return; java.util.Collections.swap(list, from, to); savePreset(s.preset.copy(order = list)) }
    fun selectAll() = savePreset(mutable.value.preset.copy(enabled = mutable.value.preset.order.toSet()))
    fun minimum() = savePreset(mutable.value.preset.copy(enabled = ExportColumns.minimum(mutable.value.type) + requiredKeys(mutable.value)))
    fun standard() { val keys = ExportColumns.forType(mutable.value.type).map { it.key }; savePreset(ColumnPreset(keys, keys.toSet())) }
    fun refresh() { viewModelScope.launch { loadData() } }

    suspend fun writeCsv(uri: Uri): Result<Unit> { val initial = mutable.value; mutable.value = initial.copy(busy = true, error = null); return runCatching {
        val s = initial; val data = s.dataset ?: error("出力データがありません"); val columns = activeColumns(s); require(columns.isNotEmpty())
        withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { CsvWriter.write(it, columns, data.rows.asSequence()) } ?: error("保存先を開けません") }
        preferences.recordCsv(s.type, data.rows.size, true); mutable.value = mutable.value.copy(busy = false, history = preferences.history(), message = "CSVを保存しました")
    }.onFailure { preferences.recordCsv(initial.type, initial.dataset?.rows?.size ?: 0, false); mutable.value = mutable.value.copy(busy = false, error = it.message) } }

    suspend fun shareFile(): Result<File> = runCatching { val s = mutable.value; val data = s.dataset ?: error("出力データがありません"); val file = File(getApplication<Application>().cacheDir, suggestedFileName()).also { cleanupShareCache(); it.parentFile?.mkdirs() }; withContext(Dispatchers.IO) { file.outputStream().use { CsvWriter.write(it, activeColumns(s), data.rows.asSequence()) } }; preferences.recordCsv(s.type, data.rows.size, true); file }
    suspend fun shareReportFile(): Result<File> = runCatching { val html = mutable.value.reportHtml ?: error("先にレポートを生成してください"); withContext(Dispatchers.IO) { cleanupShareCache(); File(getApplication<Application>().cacheDir, "WiFiAnalyzer_Report_${ExportFormat.fileStamp(System.currentTimeMillis())}.html").apply { writeText(html, Charsets.UTF_8) } } }
    fun deleteAfterSharing(file: File) { viewModelScope.launch(Dispatchers.IO) { delay(SHARE_CACHE_LIFETIME_MS); file.delete() } }
    fun generateReport() { val s = mutable.value; mutable.value = s.copy(busy = true, error = null); reportJob?.cancel(); reportJob = viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { val unit = wifiPreferences.preferences.first().distanceUnit; val (label, devices) = repository.reportDevices(target(s), unit, s.reportPhotoMode); label to ReportGenerator.generate("Wi-Fi機器レポート", ExportFormat.dateTime(System.currentTimeMillis()).orEmpty(), devices.asSequence()) } }.onSuccess { (label, html) -> preferences.recordReport(label, true); mutable.value = s.copy(busy = false, targetLabel = label, reportHtml = html, history = preferences.history()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) return@onFailure; preferences.recordReport(s.targetLabel, false); mutable.value = s.copy(busy = false, error = it.message) } } }
    fun cancel() { reportJob?.cancel(); mutable.value = mutable.value.copy(busy = false, message = "処理をキャンセルしました") }
    fun suggestedFileName(): String { val s = mutable.value; val target = if (s.scope == ExportScope.ALL_WORKSPACES) "All" else ExportFormat.safeFilePart(s.workspaceName); return "WiFiAnalyzer_${s.type.filePart}_${target}_${ExportFormat.fileStamp(System.currentTimeMillis())}.csv" }
    fun activeColumns() = activeColumns(mutable.value)
    fun clearNotice() { mutable.value = mutable.value.copy(error = null, message = null) }

    private suspend fun loadPresetAndData() { val type = mutable.value.type; val preset = if (type == ExportType.REPORT) ColumnPreset(emptyList(), emptySet()) else preferences.preset(type).first(); mutable.value = mutable.value.copy(preset = preset); loadData() }
    private suspend fun loadData() { val s = mutable.value; mutable.value = s.copy(busy = true, error = null); val unit = wifiPreferences.preferences.first().distanceUnit; val dataType = if (s.type == ExportType.REPORT) ExportType.PHOTOS else s.type; runCatching { repository.dataset(dataType, target(s), unit) }.onSuccess { data -> val now = mutable.value; mutable.value = now.copy(busy = false, dataset = data, targetLabel = data.targetLabel, preview = if (now.type == ExportType.REPORT) "" else CsvWriter.preview(activeColumns(now), data.rows.asSequence())) }.onFailure { mutable.value = mutable.value.copy(busy = false, error = it.message) } }
    private fun savePreset(preset: ColumnPreset) { mutable.value = mutable.value.copy(preset = preset); viewModelScope.launch { preferences.save(mutable.value.type, preset); loadData() } }
    private fun activeColumns(s: ExportUiState) = s.preset.order.filter { it in s.preset.enabled || it in requiredKeys(s) }.mapNotNull { key -> ExportColumns.forType(s.type).firstOrNull { it.key == key } }
    private fun requiredKeys(s: ExportUiState) = buildSet { if (s.scope == ExportScope.ALL_WORKSPACES) add("workspaceName"); ExportColumns.forType(s.type).filter { it.required }.forEach { add(it.key) } }
    private fun target(s: ExportUiState) = ExportTarget(s.scope, s.workspaceId, s.groupId, s.ungroupedOnly)
    private fun cleanupShareCache() { getApplication<Application>().cacheDir.listFiles()?.filter { it.name.startsWith("WiFiAnalyzer_") }?.forEach { it.delete() } }
    private companion object { const val SHARE_CACHE_LIFETIME_MS = 10 * 60 * 1000L }
}
