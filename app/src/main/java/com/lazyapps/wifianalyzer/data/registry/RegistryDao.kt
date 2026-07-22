package com.lazyapps.wifianalyzer.data.registry

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistryDao {
    @Query("SELECT * FROM registered_wifi_devices ORDER BY display_name COLLATE NOCASE")
    fun observeDevices(): Flow<List<RegisteredWifiDeviceEntity>>

    @Query("SELECT * FROM wifi_device_bssids ORDER BY device_id, id")
    fun observeBssids(): Flow<List<WifiDeviceBssidEntity>>

    @Query("SELECT * FROM wifi_device_groups ORDER BY sort_order, name COLLATE NOCASE")
    fun observeGroups(): Flow<List<WifiDeviceGroupEntity>>

    @Query("SELECT * FROM wifi_device_groups ORDER BY sort_order, name COLLATE NOCASE")
    suspend fun getGroupsOnce(): List<WifiDeviceGroupEntity>

    @Query("SELECT * FROM registered_wifi_devices WHERE id = :id")
    suspend fun getDevice(id: Long): RegisteredWifiDeviceEntity?

    @Query("SELECT * FROM wifi_device_bssids WHERE device_id = :deviceId ORDER BY id")
    suspend fun getBssids(deviceId: Long): List<WifiDeviceBssidEntity>

    @Query("SELECT COUNT(*) FROM wifi_device_bssids")
    suspend fun countBssids(): Int

    @Query("SELECT * FROM wifi_device_bssids WHERE bssid IN (:bssids)")
    suspend fun findBssids(bssids: List<String>): List<WifiDeviceBssidEntity>

    @Insert
    suspend fun insertDevice(device: RegisteredWifiDeviceEntity): Long

    @Update
    suspend fun updateDevice(device: RegisteredWifiDeviceEntity)

    @Insert
    suspend fun insertBssids(bssids: List<WifiDeviceBssidEntity>)

    @Query("DELETE FROM wifi_device_bssids WHERE device_id = :deviceId")
    suspend fun deleteBssidsForDevice(deviceId: Long)

    @Query("DELETE FROM registered_wifi_devices WHERE id = :deviceId")
    suspend fun deleteDevice(deviceId: Long)

    @Insert
    suspend fun insertGroup(group: WifiDeviceGroupEntity): Long

    @Update
    suspend fun updateGroup(group: WifiDeviceGroupEntity)

    @Delete
    suspend fun deleteGroup(group: WifiDeviceGroupEntity)

    @Query("UPDATE wifi_device_groups SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateGroupOrder(id: Long, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE registered_wifi_devices SET last_seen_at = :seenAt, last_seen_rssi = :rssi, updated_at = CASE WHEN updated_at > :seenAt THEN updated_at ELSE :seenAt END WHERE id = :deviceId")
    suspend fun updateLastSeen(deviceId: Long, seenAt: Long, rssi: Int)
}
