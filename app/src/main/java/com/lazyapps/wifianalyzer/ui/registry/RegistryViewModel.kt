package com.lazyapps.wifianalyzer.ui.registry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.data.registry.DeviceRegistryRepository
import com.lazyapps.wifianalyzer.data.registry.RegistryValidationException
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository
import com.lazyapps.wifianalyzer.data.photos.PhotoRepository
import android.net.Uri
import java.io.File
import com.lazyapps.wifianalyzer.domain.DeviceBssidInput
import com.lazyapps.wifianalyzer.domain.DeviceGroup
import com.lazyapps.wifianalyzer.domain.DeviceInput
import com.lazyapps.wifianalyzer.domain.DeviceMatching
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RegistryUiState(
    val devices: List<RegisteredDevice> = emptyList(),
    val groups: List<DeviceGroup> = emptyList(),
    val draft: DeviceInput = DeviceInput(displayName = "", bssids = listOf(DeviceBssidInput("", "2.4 GHz"))),
    val errorMessage: String? = null,
    val busy: Boolean = false,
    val editBaseline: DeviceInput? = null,
)

class RegistryViewModel(application: Application) : AndroidViewModel(application) {
    private val workspaceRepository = WorkspaceRepository(application, WifiAnalyzerDatabase.get(application))
    private val repository = DeviceRegistryRepository(application, WifiAnalyzerDatabase.get(application), workspaceRepository)
    private val photoRepository = PhotoRepository(application, WifiAnalyzerDatabase.get(application))
    private val _uiState = MutableStateFlow(RegistryUiState())
    val uiState: StateFlow<RegistryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workspaceRepository.ensureUsable()
            repository.snapshot.collectLatest { snapshot ->
                _uiState.value = _uiState.value.copy(devices = snapshot.devices, groups = snapshot.groups)
            }
        }
    }

    fun enriched(accessPoints: List<WifiAccessPoint>): List<WifiAccessPoint> {
        val matches = DeviceMatching.index(_uiState.value.devices)
        return accessPoints.map { ap ->
            val match = DeviceMatching.match(ap, matches)
            ap.copy(
                isRegistered = match != null,
                registeredDeviceId = match?.deviceId,
                registeredDeviceName = match?.deviceName,
                registeredGroupName = match?.groupName,
            )
        }
    }

    fun reconcile(accessPoints: List<WifiAccessPoint>) {
        if (accessPoints.isEmpty()) return
        viewModelScope.launch { repository.reconcile(accessPoints, _uiState.value.devices) }
    }

    fun startNew(accessPoint: WifiAccessPoint? = null) {
        val workspaceId = _uiState.value.devices.firstOrNull()?.workspaceId ?: _uiState.value.groups.firstOrNull()?.workspaceId ?: 0L
        _uiState.value = _uiState.value.copy(
            draft = if (accessPoint == null) {
                DeviceInput(displayName = "", bssids = listOf(DeviceBssidInput("", "2.4 GHz")))
            } else {
                DeviceInput(
                    displayName = accessPoint.ssid.takeUnless { it == "非公開ネットワーク" }.orEmpty(),
                    ssid = accessPoint.ssid.takeUnless { it == "非公開ネットワーク" }.orEmpty(),
                    bssids = listOf(DeviceBssidInput(accessPoint.bssid, accessPoint.band.label)),
                    initialLastSeenAt = accessPoint.observedAtMillis,
                    initialLastSeenRssi = accessPoint.rssi,
                    workspaceId = workspaceId,
                )
            },
            errorMessage = null,
            editBaseline = null,
        )
    }

    fun startNew(input: DeviceInput) {
        _uiState.value = _uiState.value.copy(
            draft = input,
            errorMessage = null,
            editBaseline = _uiState.value.editBaseline?.takeIf { it.id == input.id && input.id != 0L },
        )
    }

    fun startEdit(deviceId: Long) {
        val device = _uiState.value.devices.firstOrNull { it.id == deviceId } ?: return
        val draft = DeviceInput(
                id = device.id,
                displayName = device.displayName,
                manufacturer = device.manufacturer,
                model = device.model,
                serialNumber = device.serialNumber,
                ssid = device.ssid,
                groupId = device.groupId,
                location = device.location,
                notes = device.notes,
                bssids = device.bssids.map { DeviceBssidInput(it.bssid, it.band, it.label) },
                initialLastSeenAt = device.lastSeenAt,
                initialLastSeenRssi = device.lastSeenRssi,
                workspaceId = device.workspaceId,
            )
        _uiState.value = _uiState.value.copy(
            draft = draft,
            editBaseline = draft,
            errorMessage = null,
        )
    }

    fun save(input: DeviceInput, onSuccess: (Long) -> Unit) = launchAction {
        if (input.pendingPhotoPath != null) photoRepository.ensureCapacity(input.id)
        val id = repository.save(input)
        input.pendingPhotoPath?.let { path ->
            val source = File(path)
            try { photoRepository.save(id, input.workspaceId.takeIf { it != 0L } ?: _uiState.value.devices.firstOrNull { it.id == id }?.workspaceId ?: workspaceRepository.snapshot.first().selectedId, Uri.fromFile(source)) }
            finally { source.delete() }
        }
        onSuccess(id)
    }

    fun deleteDevice(id: Long, onSuccess: () -> Unit = {}) = launchAction {
        repository.deleteDevice(id)
        onSuccess()
    }

    fun createGroup(name: String) = launchAction { repository.createGroup(name) }
    fun renameGroup(group: DeviceGroup, name: String) = launchAction { repository.renameGroup(group, name) }
    fun deleteGroup(group: DeviceGroup) = launchAction { repository.deleteGroup(group) }
    fun moveGroup(group: DeviceGroup, direction: Int) = launchAction { repository.moveGroup(group, direction) }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    private fun launchAction(block: suspend () -> Unit) {
        _uiState.value = _uiState.value.copy(busy = true, errorMessage = null)
        viewModelScope.launch {
            try {
                block()
                _uiState.value = _uiState.value.copy(busy = false)
            } catch (error: RegistryValidationException) {
                _uiState.value = _uiState.value.copy(busy = false, errorMessage = error.message)
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                _uiState.value = _uiState.value.copy(busy = false, errorMessage = "同じ値が既に登録されています")
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(busy = false, errorMessage = error.message ?: "保存できませんでした")
            }
        }
    }
}
