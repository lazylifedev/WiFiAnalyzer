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
    @Query("SELECT * FROM registered_wifi_devices WHERE workspace_id = :workspaceId ORDER BY display_name COLLATE NOCASE")
    fun observeDevices(workspaceId: Long): Flow<List<RegisteredWifiDeviceEntity>>

    @Query("SELECT * FROM wifi_device_bssids WHERE workspace_id = :workspaceId ORDER BY device_id, id")
    fun observeBssids(workspaceId: Long): Flow<List<WifiDeviceBssidEntity>>

    @Query("SELECT * FROM wifi_device_groups WHERE workspace_id = :workspaceId ORDER BY sort_order, name COLLATE NOCASE")
    fun observeGroups(workspaceId: Long): Flow<List<WifiDeviceGroupEntity>>

    @Query("SELECT * FROM wifi_device_groups WHERE workspace_id = :workspaceId ORDER BY sort_order, name COLLATE NOCASE")
    suspend fun getGroupsOnce(workspaceId: Long): List<WifiDeviceGroupEntity>

    @Query("SELECT * FROM registered_wifi_devices WHERE id = :id")
    suspend fun getDevice(id: Long): RegisteredWifiDeviceEntity?

    @Query("SELECT * FROM wifi_device_bssids WHERE device_id = :deviceId ORDER BY id")
    suspend fun getBssids(deviceId: Long): List<WifiDeviceBssidEntity>

    @Query("SELECT COUNT(*) FROM wifi_device_bssids")
    suspend fun countBssids(): Int

    @Query("SELECT * FROM wifi_device_bssids WHERE workspace_id = :workspaceId AND bssid IN (:bssids)")
    suspend fun findBssids(workspaceId: Long, bssids: List<String>): List<WifiDeviceBssidEntity>

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

    @Query("SELECT * FROM workspaces ORDER BY sort_order, id") fun observeWorkspaces(): Flow<List<WorkspaceEntity>>
    @Query("SELECT * FROM workspaces ORDER BY sort_order, id") suspend fun getWorkspacesOnce(): List<WorkspaceEntity>
    @Query("SELECT * FROM workspaces WHERE id = :id") suspend fun getWorkspace(id: Long): WorkspaceEntity?
    @Insert suspend fun insertWorkspace(workspace: WorkspaceEntity): Long
    @Update suspend fun updateWorkspace(workspace: WorkspaceEntity)
    @Query("DELETE FROM workspaces WHERE id = :id") suspend fun deleteWorkspace(id: Long)
    @Query("UPDATE workspaces SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id") suspend fun updateWorkspaceOrder(id: Long, sortOrder: Int, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM registered_wifi_devices WHERE workspace_id = :workspaceId") suspend fun countDevices(workspaceId: Long): Int
    @Query("SELECT COUNT(*) FROM wifi_device_groups WHERE workspace_id = :workspaceId") suspend fun countGroups(workspaceId: Long): Int
    @Query("SELECT COUNT(*) FROM device_photos WHERE workspace_id = :workspaceId") suspend fun countPhotos(workspaceId: Long): Int

    @Query("SELECT * FROM device_photos WHERE device_id = :deviceId ORDER BY sort_order, id") fun observePhotos(deviceId: Long): Flow<List<DevicePhotoEntity>>
    @Query("SELECT * FROM device_photos WHERE device_id = :deviceId ORDER BY sort_order, id") suspend fun getPhotos(deviceId: Long): List<DevicePhotoEntity>
    @Query("SELECT * FROM device_photos WHERE workspace_id = :workspaceId ORDER BY device_id, sort_order, id") suspend fun getPhotosForWorkspace(workspaceId: Long): List<DevicePhotoEntity>
    @Query("SELECT * FROM device_photos WHERE workspace_id = :workspaceId ORDER BY device_id, sort_order, id") fun observePhotosForWorkspace(workspaceId: Long): Flow<List<DevicePhotoEntity>>
    @Query("SELECT * FROM device_photos WHERE id = :id") suspend fun getPhoto(id: Long): DevicePhotoEntity?
    @Insert suspend fun insertPhoto(photo: DevicePhotoEntity): Long
    @Update suspend fun updatePhoto(photo: DevicePhotoEntity)
    @Query("DELETE FROM device_photos WHERE id = :id") suspend fun deletePhoto(id: Long)
    @Query("UPDATE device_photos SET is_primary = CASE WHEN id = :photoId THEN 1 ELSE 0 END, updated_at = :updatedAt WHERE device_id = :deviceId") suspend fun setPrimaryPhoto(deviceId: Long, photoId: Long, updatedAt: Long)
    @Query("UPDATE device_photos SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id") suspend fun updatePhotoOrder(id: Long, sortOrder: Int, updatedAt: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPendingDeletion(item: PendingFileDeletionEntity)
    @Query("SELECT * FROM pending_file_deletions") suspend fun getPendingDeletions(): List<PendingFileDeletionEntity>
    @Query("DELETE FROM pending_file_deletions WHERE path = :path") suspend fun deletePendingDeletion(path: String)
}
