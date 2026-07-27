package com.lazyapps.wifianalyzer.ui.photos

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.data.photos.PhotoRepository
import com.lazyapps.wifianalyzer.data.registry.RegistryValidationException
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.domain.DevicePhoto
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncScheduler
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorCategory
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class DevicePhotoUiState(
    val photos: List<DevicePhoto> = emptyList(), val busy: Boolean = false, val error: String? = null,
    val selectedPhotoIds: Set<Long> = emptySet(), val selectionMode: Boolean = false, val reorderMode: Boolean = false,
)

class DevicePhotoViewModel(application: Application) : AndroidViewModel(application) {
    val repository = PhotoRepository(application, WifiAnalyzerDatabase.get(application))
    private val autoSync = KintoneAutoSyncScheduler(application)
    private val _state = MutableStateFlow(DevicePhotoUiState())
    val state: StateFlow<DevicePhotoUiState> = _state.asStateFlow()
    private var deviceId = 0L; private var workspaceId = 0L; private var observeJob: Job? = null
    init { viewModelScope.launch { repository.retryPending() } }
    fun bind(deviceId: Long, workspaceId: Long) { if (this.deviceId == deviceId && this.workspaceId == workspaceId) return; this.deviceId = deviceId; this.workspaceId = workspaceId; observeJob?.cancel(); observeJob = viewModelScope.launch { repository.observe(deviceId).collectLatest { _state.value = _state.value.copy(photos = it) } } }
    fun add(uris: List<Uri>) = action { uris.take(9 - _state.value.photos.size).forEach { uri -> try { repository.save(deviceId, workspaceId, uri) } finally { if (uri.scheme == "file") uri.path?.let { java.io.File(it).delete() } } }; autoSync.requestPhotoChange(workspaceId) }
    fun delete(id: Long) = action { repository.delete(id); autoSync.requestPhotoChange(workspaceId) }
    fun deleteSelected() = action { repository.delete(_state.value.selectedPhotoIds); autoSync.requestPhotoChange(workspaceId); _state.value = _state.value.copy(selectedPhotoIds = emptySet(), selectionMode = false) }
    fun enterSelection(id: Long) { _state.value = _state.value.copy(selectionMode = true, reorderMode = false, selectedPhotoIds = setOf(id)) }
    fun toggleSelection(id: Long) { val next = _state.value.selectedPhotoIds.toMutableSet().apply { if (!add(id)) remove(id) }; _state.value = _state.value.copy(selectedPhotoIds = next, selectionMode = next.isNotEmpty()) }
    fun clearSelection() { _state.value = _state.value.copy(selectedPhotoIds = emptySet(), selectionMode = false) }
    fun selectAll() { _state.value = _state.value.copy(selectedPhotoIds = _state.value.photos.map { it.id }.toSet(), selectionMode = true) }
    fun setReorderMode(enabled: Boolean) { _state.value = _state.value.copy(reorderMode = enabled, selectionMode = false, selectedPhotoIds = emptySet()) }
    fun primary(id: Long) = action { repository.setPrimary(id); autoSync.requestPhotoChange(workspaceId) }
    fun caption(id: Long, value: String) = action { repository.caption(id, value) }
    fun move(id: Long, direction: Int) = action { repository.move(id, direction); autoSync.requestPhotoChange(workspaceId) }
    private fun action(block: suspend () -> Unit) { if (_state.value.busy) return; _state.value = _state.value.copy(busy = true, error = null); viewModelScope.launch { try { block(); _state.value = _state.value.copy(busy = false) } catch (e: RegistryValidationException) { _state.value = _state.value.copy(busy = false, error = e.message) } catch (e: Exception) { _state.value = _state.value.copy(busy = false, error = message(OperationErrorMapper.classify(e))) } } }
    private fun message(category: OperationErrorCategory) = getApplication<Application>().getString(category.messageRes)
}
