package com.lazyapps.wifianalyzer.ui.workspace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.data.registry.RegistryValidationException
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository
import com.lazyapps.wifianalyzer.domain.Workspace
import com.lazyapps.wifianalyzer.domain.WorkspaceCounts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val workspaces: List<Workspace> = emptyList(), val selectedId: Long = 0, val selected: Workspace? = null,
    val busy: Boolean = false, val errorMessage: String? = null, val deleteCounts: Map<Long, WorkspaceCounts> = emptyMap(),
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    val repository = WorkspaceRepository(application, WifiAnalyzerDatabase.get(application))
    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUsable()
            repository.snapshot.collectLatest { snapshot ->
                _uiState.value = _uiState.value.copy(workspaces = snapshot.workspaces, selectedId = snapshot.selectedId, selected = snapshot.selected)
            }
        }
    }

    fun select(id: Long) = action { repository.select(id) }
    fun create(name: String) = action { repository.select(repository.create(name)) }
    fun rename(id: Long, name: String) = action { repository.rename(id, name) }
    fun move(id: Long, direction: Int) = action { repository.move(id, direction) }
    fun loadCounts(id: Long) { viewModelScope.launch { _uiState.value = _uiState.value.copy(deleteCounts = _uiState.value.deleteCounts + (id to repository.counts(id))) } }
    fun delete(id: Long) = action { repository.delete(id) }

    private fun action(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, errorMessage = null)
        viewModelScope.launch {
            try { block(); _uiState.value = _uiState.value.copy(busy = false) }
            catch (e: RegistryValidationException) { _uiState.value = _uiState.value.copy(busy = false, errorMessage = e.message) }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(busy = false, errorMessage = e.message ?: "操作に失敗しました") }
        }
    }
}
