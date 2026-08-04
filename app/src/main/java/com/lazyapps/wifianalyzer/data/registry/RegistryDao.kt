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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(items: List<SignalHistoryEntity>)
    @Query("SELECT * FROM signal_history WHERE workspace_id = :workspaceId AND bssid = :bssid ORDER BY timestamp_millis ASC")
    fun observeHistory(workspaceId: Long, bssid: String): Flow<List<SignalHistoryEntity>>
    @Query("SELECT * FROM signal_history WHERE workspace_id = :workspaceId AND bssid = :bssid ORDER BY timestamp_millis DESC LIMIT :limit")
    fun observeLatestHistory(workspaceId: Long, bssid: String, limit: Int): Flow<List<SignalHistoryEntity>>
    @Query("DELETE FROM signal_history WHERE timestamp_millis < :cutoff")
    suspend fun deleteHistoryBefore(cutoff: Long): Int
    @Query("DELETE FROM signal_history WHERE timestamp_millis < :cutoff30")
    suspend fun deleteHistoryBeforeLongTerm(cutoff30: Long): Int
    @Query("DELETE FROM signal_history WHERE timestamp_millis < :cutoff24 AND NOT EXISTS (SELECT 1 FROM wifi_device_bssids b WHERE b.workspace_id = signal_history.workspace_id AND b.bssid = signal_history.bssid)")
    suspend fun deleteUnregisteredHistoryBefore(cutoff24: Long): Int
    @Query("DELETE FROM signal_history WHERE timestamp_millis < :cutoff24 AND EXISTS (SELECT 1 FROM wifi_device_bssids b WHERE b.workspace_id = signal_history.workspace_id AND b.bssid = signal_history.bssid) AND id NOT IN (SELECT MAX(h.id) FROM signal_history h WHERE h.timestamp_millis < :cutoff24 GROUP BY h.workspace_id, h.bssid, h.timestamp_millis / 300000)")
    suspend fun compactRegisteredHistory(cutoff24: Long): Int
    @Query("DELETE FROM signal_history WHERE timestamp_millis < :cutoff24 AND id NOT IN (SELECT id FROM signal_history h WHERE h.timestamp_millis < :cutoff24 AND h.workspace_id = signal_history.workspace_id AND h.bssid = signal_history.bssid ORDER BY h.timestamp_millis DESC, h.id DESC LIMIT 10000)")
    suspend fun capLongTermHistory(cutoff24: Long): Int
    @Query("SELECT * FROM kintone_connections WHERE workspace_id = :workspaceId") fun observeKintoneConnection(workspaceId: Long): Flow<KintoneConnectionEntity?>
    @Query("SELECT * FROM kintone_connections WHERE workspace_id = :workspaceId") suspend fun getKintoneConnection(workspaceId: Long): KintoneConnectionEntity?
    @Query("SELECT COUNT(*) FROM kintone_connections WHERE domain = :domain AND app_id = :appId AND workspace_id != :workspaceId") suspend fun countOtherKintoneConnections(domain: String, appId: Long, workspaceId: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertKintoneConnection(connection: KintoneConnectionEntity)
    @Query("DELETE FROM kintone_connections WHERE workspace_id = :workspaceId") suspend fun deleteKintoneConnection(workspaceId: Long)
    @Query("UPDATE kintone_connections SET last_verified_at = :verifiedAt, last_verification_status = :status WHERE workspace_id = :workspaceId") suspend fun updateKintoneVerification(workspaceId: Long, verifiedAt: Long, status: String)
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
    @Query("SELECT * FROM registered_wifi_devices ORDER BY workspace_id, id") suspend fun getAllDevices(): List<RegisteredWifiDeviceEntity>
    @Query("SELECT * FROM registered_wifi_devices WHERE workspace_id = :workspaceId ORDER BY id") suspend fun getDevicesOnce(workspaceId: Long): List<RegisteredWifiDeviceEntity>
    @Query("SELECT * FROM wifi_device_groups ORDER BY workspace_id, sort_order, id") suspend fun getAllGroups(): List<WifiDeviceGroupEntity>
    @Query("SELECT * FROM wifi_device_bssids ORDER BY workspace_id, device_id, id") suspend fun getAllBssids(): List<WifiDeviceBssidEntity>
    @Query("SELECT * FROM wifi_device_bssids WHERE workspace_id = :workspaceId ORDER BY device_id, id") suspend fun getBssidsForWorkspace(workspaceId: Long): List<WifiDeviceBssidEntity>
    @Query("SELECT * FROM device_photos ORDER BY workspace_id, device_id, sort_order, id") suspend fun getAllPhotos(): List<DevicePhotoEntity>
    @Query("DELETE FROM workspaces") suspend fun deleteAllWorkspaces()

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
