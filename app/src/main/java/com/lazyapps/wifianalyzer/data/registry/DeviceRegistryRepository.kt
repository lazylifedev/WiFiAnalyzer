package com.lazyapps.wifianalyzer.data.registry

import androidx.room.withTransaction
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

data class RegistrySnapshot(
    val devices: List<RegisteredDevice> = emptyList(),
    val groups: List<DeviceGroup> = emptyList(),
)

class RegistryValidationException(message: String) : IllegalArgumentException(message)

class DeviceRegistryRepository(private val database: WifiAnalyzerDatabase) {
    private val dao = database.registryDao()

    val snapshot: Flow<RegistrySnapshot> = combine(
        dao.observeDevices(), dao.observeBssids(), dao.observeGroups(),
    ) { devices, bssids, groups ->
        val groupMap = groups.associateBy { it.id }
        val bssidMap = bssids.groupBy { it.deviceId }
        RegistrySnapshot(
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
                )
            },
            groups = groups.map { DeviceGroup(it.id, it.name, it.sortOrder) },
        )
    }

    suspend fun save(input: DeviceInput): Long = database.withTransaction {
        val displayName = input.displayName.trim()
        if (displayName.isBlank()) throw RegistryValidationException("機器名を入力してください")
        if (input.bssids.isEmpty()) throw RegistryValidationException("BSSIDを1件以上入力してください")
        val normalized = input.bssids.map { item ->
            val address = BssidFormat.normalize(item.bssid)
                ?: throw RegistryValidationException("BSSID「${item.bssid}」の形式が正しくありません")
            item.copy(bssid = address, label = item.label.trim())
        }
        if (normalized.map { it.bssid }.distinct().size != normalized.size) {
            throw RegistryValidationException("同じBSSIDが複数入力されています")
        }
        val conflicts = dao.findBssids(normalized.map { it.bssid }).filter { it.deviceId != input.id }
        if (conflicts.isNotEmpty()) throw RegistryValidationException("BSSID ${conflicts.first().bssid} は別の機器に登録済みです")

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
        )
        val deviceId = if (existing == null) dao.insertDevice(entity) else {
            dao.updateDevice(entity)
            dao.deleteBssidsForDevice(entity.id)
            entity.id
        }
        dao.insertBssids(normalized.map { it.toEntity(deviceId, now) })
        deviceId
    }

    suspend fun deleteDevice(id: Long) = dao.deleteDevice(id)

    suspend fun createGroup(name: String): Long {
        val trimmed = name.trim()
        val normalized = GroupNameFormat.normalize(name)
        if (normalized.isBlank()) throw RegistryValidationException("グループ名を入力してください")
        val current = snapshotOnceGroups()
        if (current.any { it.normalizedName == normalized }) throw RegistryValidationException("同名のグループが既にあります")
        val now = System.currentTimeMillis()
        return dao.insertGroup(WifiDeviceGroupEntity(name = trimmed, normalizedName = normalized, sortOrder = (current.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = now, updatedAt = now))
    }

    suspend fun renameGroup(group: DeviceGroup, name: String) {
        val trimmed = name.trim()
        val normalized = GroupNameFormat.normalize(name)
        if (normalized.isBlank()) throw RegistryValidationException("グループ名を入力してください")
        val current = snapshotOnceGroups()
        if (current.any { it.id != group.id && it.normalizedName == normalized }) throw RegistryValidationException("同名のグループが既にあります")
        val old = current.first { it.id == group.id }
        dao.updateGroup(old.copy(name = trimmed, normalizedName = normalized, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteGroup(group: DeviceGroup) {
        val entity = snapshotOnceGroups().firstOrNull { it.id == group.id } ?: return
        dao.deleteGroup(entity)
    }

    suspend fun moveGroup(group: DeviceGroup, direction: Int) = database.withTransaction {
        val groups = snapshotOnceGroups().sortedBy { it.sortOrder }
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

    private suspend fun snapshotOnceGroups(): List<WifiDeviceGroupEntity> = dao.getGroupsOnce()
}

private fun DeviceBssidInput.toEntity(deviceId: Long, createdAt: Long) = WifiDeviceBssidEntity(
    deviceId = deviceId,
    bssid = bssid,
    band = band,
    label = label,
    createdAt = createdAt,
)
