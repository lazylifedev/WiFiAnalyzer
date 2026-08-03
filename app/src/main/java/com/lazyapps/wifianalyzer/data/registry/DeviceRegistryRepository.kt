package com.lazyapps.wifianalyzer.data.registry

import androidx.room.withTransaction
import android.content.Context
import com.lazyapps.wifianalyzer.domain.BssidFormat
import com.lazyapps.wifianalyzer.domain.DetectionPolicy
import com.lazyapps.wifianalyzer.domain.DeviceBssidInput
import com.lazyapps.wifianalyzer.domain.DeviceGroup
import com.lazyapps.wifianalyzer.domain.DeviceInput
import com.lazyapps.wifianalyzer.domain.DeviceMatching
import com.lazyapps.wifianalyzer.domain.GroupNameFormat
import com.lazyapps.wifianalyzer.domain.RegisteredBssid
import com.lazyapps.wifianalyzer.domain.RegisteredDevice
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

data class RegistrySnapshot(
    val workspaceId: Long = 0,
    val devices: List<RegisteredDevice> = emptyList(),
    val groups: List<DeviceGroup> = emptyList(),
)

enum class RegistryError {
    WORKSPACE_NOT_FOUND, DEVICE_NAME_REQUIRED, BSSID_REQUIRED, INVALID_BSSID,
    DUPLICATE_BSSID_INPUT, BSSID_ALREADY_REGISTERED, GROUP_NOT_FOUND,
    GROUP_NAME_REQUIRED, GROUP_NAME_TOO_LONG, DUPLICATE_GROUP,
    WORKSPACE_NAME_REQUIRED, DUPLICATE_WORKSPACE, DUPLICATE_VALUE,
    PHOTO_LIMIT, DEVICE_NOT_FOUND, INVALID_PHOTO, PHOTO_TOO_LARGE, PHOTO_OUT_OF_MEMORY, PHOTO_WRITE_FAILED,
}

class RegistryValidationException(val error: RegistryError, vararg val arguments: Any) : IllegalArgumentException(error.name)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceRegistryRepository(private val context: Context, private val database: WifiAnalyzerDatabase, private val workspaceRepository: WorkspaceRepository) {
    private val dao = database.registryDao()

    val snapshot: Flow<RegistrySnapshot> = workspaceRepository.snapshot.flatMapLatest { workspace -> combine(
        dao.observeDevices(workspace.selectedId), dao.observeBssids(workspace.selectedId), dao.observeGroups(workspace.selectedId), dao.observePhotosForWorkspace(workspace.selectedId),
    ) { devices, bssids, groups, photos ->
        val groupMap = groups.associateBy { it.id }
        val bssidMap = bssids.groupBy { it.deviceId }
        RegistrySnapshot(
            workspaceId = workspace.selectedId,
            devices = devices.map { entity ->
                RegisteredDevice(
                    id = entity.id,
                    displayName = entity.displayName,
                    manufacturer = entity.manufacturer,
                    model = entity.model,
                    serialNumber = entity.serialNumber,
                    ssid = entity.ssid,
                    groupId = entity.groupId,
                    groupName = entity.groupId?.let(groupMap::get)?.name,
                    location = entity.location,
                    notes = entity.notes,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    lastSeenAt = entity.lastSeenAt,
                    lastSeenRssi = entity.lastSeenRssi,
                    isEnabled = entity.isEnabled,
                    bssids = bssidMap[entity.id].orEmpty().map { RegisteredBssid(it.id, it.bssid, it.band, it.label) },
                    workspaceId = entity.workspaceId,
                    photoCount = photos.count { it.deviceId == entity.id },
                )
            },
            groups = groups.map { DeviceGroup(it.id, it.name, it.sortOrder, it.workspaceId) },
        )
    } }

    suspend fun save(input: DeviceInput): Long = database.withTransaction {
        val selectedWorkspaceId = workspaceRepository.snapshot.first().selectedId
        val workspaceId = input.workspaceId.takeIf { it != 0L } ?: selectedWorkspaceId
        if (dao.getWorkspace(workspaceId) == null) throw RegistryValidationException(RegistryError.WORKSPACE_NOT_FOUND)
        val displayName = input.displayName.trim()
        if (displayName.isBlank()) throw RegistryValidationException(RegistryError.DEVICE_NAME_REQUIRED)
        if (input.bssids.isEmpty()) throw RegistryValidationException(RegistryError.BSSID_REQUIRED)
        val normalized = input.bssids.map { item ->
            val address = BssidFormat.normalize(item.bssid)
                ?: throw RegistryValidationException(RegistryError.INVALID_BSSID, item.bssid)
            item.copy(bssid = address, label = item.label.trim())
        }
        if (normalized.map { it.bssid }.distinct().size != normalized.size) {
            throw RegistryValidationException(RegistryError.DUPLICATE_BSSID_INPUT)
        }
        val conflicts = dao.findBssids(workspaceId, normalized.map { it.bssid }).filter { it.deviceId != input.id }
        if (conflicts.isNotEmpty()) throw RegistryValidationException(RegistryError.BSSID_ALREADY_REGISTERED, conflicts.first().bssid)
        if (input.groupId != null && snapshotOnceGroups(workspaceId).none { it.id == input.groupId }) {
            throw RegistryValidationException(RegistryError.GROUP_NOT_FOUND)
        }

        val now = System.currentTimeMillis()
        val existing = input.id.takeIf { it != 0L }?.let { dao.getDevice(it) }
        val entity = RegisteredWifiDeviceEntity(
            id = existing?.id ?: 0,
            displayName = displayName,
            manufacturer = input.manufacturer.trim(),
            model = input.model.trim(),
            serialNumber = input.serialNumber.trim(),
            primaryBssid = normalized.first().bssid,
            ssid = input.ssid.trim(),
            groupId = input.groupId,
            location = input.location.trim(),
            notes = input.notes.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastSeenAt = existing?.lastSeenAt ?: input.initialLastSeenAt,
            lastSeenRssi = existing?.lastSeenRssi ?: input.initialLastSeenRssi,
            isEnabled = existing?.isEnabled ?: true,
            workspaceId = workspaceId,
        )
        val deviceId = if (existing == null) dao.insertDevice(entity) else {
            dao.updateDevice(entity)
            dao.deleteBssidsForDevice(entity.id)
            entity.id
        }
        dao.insertBssids(normalized.map { it.toEntity(deviceId, workspaceId, now) })
        deviceId
    }

    suspend fun deleteDevice(id: Long) {
        val paths = mutableListOf<String>()
        database.withTransaction {
            dao.getPhotos(id).forEach { photo ->
                val path = "devices/${photo.workspaceId}/${photo.deviceId}/photos/${photo.fileName}"
                paths += path; dao.insertPendingDeletion(PendingFileDeletionEntity(path, System.currentTimeMillis()))
            }
            dao.deleteDevice(id)
        }
        paths.forEach { path -> val file = java.io.File(context.filesDir, path); if (!file.exists() || file.delete()) dao.deletePendingDeletion(path) }
    }

    suspend fun createGroup(name: String): Long = createGroup(workspaceRepository.snapshot.first().selectedId, name)

    suspend fun createGroup(workspaceId: Long, name: String): Long = database.withTransaction {
        if (dao.getWorkspace(workspaceId) == null) throw RegistryValidationException(RegistryError.WORKSPACE_NOT_FOUND)
        val trimmed = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFKC)
        val normalized = GroupNameFormat.normalize(name)
        if (normalized.isBlank()) throw RegistryValidationException(RegistryError.GROUP_NAME_REQUIRED)
        if (trimmed.length > 50) throw RegistryValidationException(RegistryError.GROUP_NAME_TOO_LONG)
        val current = snapshotOnceGroups(workspaceId)
        if (current.any { it.normalizedName == normalized }) throw RegistryValidationException(RegistryError.DUPLICATE_GROUP)
        val now = System.currentTimeMillis()
        dao.insertGroup(WifiDeviceGroupEntity(name = trimmed, normalizedName = normalized, sortOrder = (current.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = now, updatedAt = now, workspaceId = workspaceId))
    }

    suspend fun renameGroup(group: DeviceGroup, name: String) {
        val trimmed = name.trim()
        val normalized = GroupNameFormat.normalize(name)
        if (normalized.isBlank()) throw RegistryValidationException(RegistryError.GROUP_NAME_REQUIRED)
        val current = snapshotOnceGroups(group.workspaceId)
        if (current.any { it.id != group.id && it.normalizedName == normalized }) throw RegistryValidationException(RegistryError.DUPLICATE_GROUP)
        val old = current.first { it.id == group.id }
        dao.updateGroup(old.copy(name = trimmed, normalizedName = normalized, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteGroup(group: DeviceGroup) {
        val entity = snapshotOnceGroups(group.workspaceId).firstOrNull { it.id == group.id } ?: return
        dao.deleteGroup(entity)
    }

    suspend fun moveGroup(group: DeviceGroup, direction: Int) = database.withTransaction {
        val groups = snapshotOnceGroups(group.workspaceId).sortedBy { it.sortOrder }
        val index = groups.indexOfFirst { it.id == group.id }
        val otherIndex = index + direction
        if (index !in groups.indices || otherIndex !in groups.indices) return@withTransaction
        val now = System.currentTimeMillis()
        dao.updateGroupOrder(groups[index].id, groups[otherIndex].sortOrder, now)
        dao.updateGroupOrder(groups[otherIndex].id, groups[index].sortOrder, now)
    }

    suspend fun reconcile(accessPoints: List<WifiAccessPoint>, devices: List<RegisteredDevice>) {
        val index = DeviceMatching.index(devices)
        val bestByDevice = accessPoints.mapNotNull { ap -> DeviceMatching.match(ap, index)?.let { it.deviceId to ap } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, matches) -> matches.maxBy { it.rssi } }
        val devicesById = devices.associateBy { it.id }
        bestByDevice.forEach { (deviceId, ap) ->
            val device = devicesById[deviceId] ?: return@forEach
            if (DetectionPolicy.shouldUpdate(device.lastSeenAt, device.lastSeenRssi, ap.observedAtMillis, ap.rssi)) {
                dao.updateLastSeen(deviceId, ap.observedAtMillis, ap.rssi)
            }
        }
    }

    private suspend fun snapshotOnceGroups(workspaceId: Long): List<WifiDeviceGroupEntity> = dao.getGroupsOnce(workspaceId)
}

private fun DeviceBssidInput.toEntity(deviceId: Long, workspaceId: Long, createdAt: Long) = WifiDeviceBssidEntity(
    deviceId = deviceId,
    bssid = bssid,
    band = band,
    label = label,
    createdAt = createdAt,
    workspaceId = workspaceId,
)
