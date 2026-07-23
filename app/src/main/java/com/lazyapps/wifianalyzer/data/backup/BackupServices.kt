package com.lazyapps.wifianalyzer.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.data.registry.*
import com.lazyapps.wifianalyzer.domain.WorkspaceName
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface BackupScope { data object All : BackupScope; data class Workspace(val id: Long) : BackupScope }

class BackupExportService(private val context: Context, private val database: WifiAnalyzerDatabase) {
    private val dao = database.registryDao()
    suspend fun export(scope: BackupScope, output: Uri, onProgress: (String, Int, Int) -> Unit = { _,_,_ -> }): BackupManifest = withContext(Dispatchers.IO) {
        val workspaces = when (scope) { BackupScope.All -> dao.getWorkspacesOnce(); is BackupScope.Workspace -> listOfNotNull(dao.getWorkspace(scope.id)) }
        if (workspaces.isEmpty()) throw BackupException(BackupException.Code.INVALID_REFERENCE, "バックアップ対象のワークスペースがありません")
        val workspaceDbIds = workspaces.map { it.id }.toSet()
        val devices = when (scope) { BackupScope.All -> dao.getAllDevices(); is BackupScope.Workspace -> dao.getDevicesOnce(scope.id) }
        val groups = when (scope) { BackupScope.All -> dao.getAllGroups(); is BackupScope.Workspace -> dao.getGroupsOnce(scope.id) }
        val bssids = when (scope) { BackupScope.All -> dao.getAllBssids(); is BackupScope.Workspace -> dao.getBssidsForWorkspace(scope.id) }
        val photos = when (scope) { BackupScope.All -> dao.getAllPhotos(); is BackupScope.Workspace -> dao.getPhotosForWorkspace(scope.id) }
        check(devices.all { it.workspaceId in workspaceDbIds })
        val workspaceIds = workspaces.associate { it.id to UUID.randomUUID().toString() }
        val groupIds = groups.associate { it.id to UUID.randomUUID().toString() }
        val deviceIds = devices.associate { it.id to UUID.randomUUID().toString() }
        val data = BackupData(
            workspaces.map { BackupWorkspace(workspaceIds.getValue(it.id), it.name, it.sortOrder, it.createdAt, it.updatedAt) },
            groups.map { BackupGroup(UUID.randomUUID().toString(), workspaceIds.getValue(it.workspaceId), it.name, it.sortOrder, it.createdAt, it.updatedAt) }.mapIndexed { index, item -> item.copy(backupId = groupIds.getValue(groups[index].id)) },
            devices.map { BackupDevice(deviceIds.getValue(it.id), workspaceIds.getValue(it.workspaceId), it.displayName, it.manufacturer, it.model, it.serialNumber, it.primaryBssid, it.ssid, it.groupId?.let(groupIds::get), it.location, it.notes, it.createdAt, it.updatedAt, it.lastSeenAt, it.lastSeenRssi, it.isEnabled) },
            bssids.map { BackupBssid(UUID.randomUUID().toString(), deviceIds.getValue(it.deviceId), workspaceIds.getValue(it.workspaceId), it.bssid, it.band, it.label, it.createdAt) },
            photos.map { photo -> val wid = workspaceIds.getValue(photo.workspaceId); val did = deviceIds.getValue(photo.deviceId); BackupPhoto(UUID.randomUUID().toString(), did, wid, photo.fileName, "photos/$wid/$did/${UUID.randomUUID()}.${photo.fileName.substringAfterLast('.', "jpg")}", photo.mimeType, photo.width, photo.height, photo.fileSize, photo.sortOrder, photo.caption, photo.isPrimary, photo.createdAt, photo.updatedAt) },
        )
        val photoByBackupId = data.photos.zip(photos).associate { it.first.backupId to it.second }
        context.contentResolver.openOutputStream(output, "w")?.use { stream ->
            BackupArchiveWriter().write(stream, data, { checksums ->
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                BackupManifest(appPackage=context.packageName, appVersionName=packageInfo.versionName ?: "unknown", appVersionCode=packageInfo.longVersionCode, createdAt=System.currentTimeMillis(), backupType=if(scope is BackupScope.All) BackupType.all else BackupType.workspace, workspaceCount=data.workspaces.size, deviceCount=data.devices.size, groupCount=data.groups.size, bssidCount=data.bssids.size, photoCount=data.photos.size, totalPhotoBytes=data.photos.sumOf { it.fileSize }, databaseSchemaVersion=WifiAnalyzerDatabase.VERSION, deviceManufacturer=Build.MANUFACTURER, androidVersion=Build.VERSION.RELEASE, checksums=checksums)
            }, { photo ->
                val source = photoByBackupId.getValue(photo.backupId)
                File(context.filesDir, "devices/${source.workspaceId}/${source.deviceId}/photos/${source.fileName}")
            }).also { onProgress("完了", data.photos.size, data.photos.size) }
        } ?: throw BackupException(BackupException.Code.INVALID_ZIP, "保存先を開けません")
    }
}

class BackupImportService(private val context: Context) {
    suspend fun inspect(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "backup-import-${UUID.randomUUID()}").apply { mkdirs() }
        val archive = File(root, "source.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> archive.outputStream().use { output -> input.copyTo(output, limit = BackupLimits.MAX_ZIP_BYTES + 1) } } ?: throw BackupException(BackupException.Code.INVALID_ZIP, "ファイルを開けません")
            if (archive.length() > BackupLimits.MAX_ZIP_BYTES) throw BackupException(BackupException.Code.LIMIT_EXCEEDED, "ZIPファイルが大きすぎます")
            BackupArchiveReader().read(archive, File(root, "expanded"))
        } catch (e: Exception) { root.deleteRecursively(); throw e }
    }
    fun discard(preview: BackupPreview) { preview.extractedDirectory.parentFile?.deleteRecursively() }
}

class RestorePlanner {
    fun workspaceNames(data: BackupData, existing: List<WorkspaceEntity>, mode: RestoreMode): Map<String,String> {
        val used = if (mode == RestoreMode.ADD) existing.map { it.normalizedName }.toMutableSet() else mutableSetOf()
        return data.workspaces.associate { workspace -> uniqueRestoredName(workspace.name, used).also { used += WorkspaceName.normalized(it) }.let { workspace.backupId to it } }
    }
}

class RestoreRepository(private val context: Context, private val database: WifiAnalyzerDatabase) {
    private val dao = database.registryDao()
    suspend fun restore(preview: BackupPreview, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        BackupValidator.validate(preview.manifest, preview.data, preview.extractedDirectory)
        val data = preview.data; val plannedNames = RestorePlanner().workspaceNames(data, dao.getWorkspacesOnce(), mode)
        val staged = File(context.cacheDir, "restore-stage-${UUID.randomUUID()}").apply { mkdirs() }
        val newFiles = mutableListOf<File>(); val oldPhotoFiles = if (mode == RestoreMode.REPLACE) dao.getAllPhotos().map { File(context.filesDir, "devices/${it.workspaceId}/${it.deviceId}/photos/${it.fileName}") } else emptyList()
        try {
            data.photos.forEach { photo ->
                val source = BackupSecurity.resolve(preview.extractedDirectory, photo.archivePath)
                val target = File(staged, photo.backupId); source.copyTo(target)
                if (target.length() != photo.fileSize) throw BackupException(BackupException.Code.PHOTO_WRITE_FAILED, "写真の一時保存に失敗しました")
            }
            val workspaceMap = mutableMapOf<String,Long>(); val groupMap = mutableMapOf<String,Long>(); val deviceMap = mutableMapOf<String,Long>()
            database.withTransaction {
                if (mode == RestoreMode.REPLACE) dao.deleteAllWorkspaces()
                data.workspaces.sortedBy { it.sortOrder }.forEach { item -> workspaceMap[item.backupId] = dao.insertWorkspace(WorkspaceEntity(name=plannedNames.getValue(item.backupId), normalizedName=WorkspaceName.normalized(plannedNames.getValue(item.backupId)), sortOrder=item.sortOrder, createdAt=item.createdAt, updatedAt=item.updatedAt)) }
                data.groups.forEach { item -> groupMap[item.backupId] = dao.insertGroup(WifiDeviceGroupEntity(name=item.name, normalizedName=com.lazyapps.wifianalyzer.domain.GroupNameFormat.normalize(item.name), sortOrder=item.sortOrder, createdAt=item.createdAt, updatedAt=item.updatedAt, workspaceId=workspaceMap.getValue(item.workspaceBackupId))) }
                data.devices.forEach { item -> deviceMap[item.backupId] = dao.insertDevice(RegisteredWifiDeviceEntity(displayName=item.displayName, manufacturer=item.manufacturer, model=item.model, serialNumber=item.serialNumber, primaryBssid=item.primaryBssid, ssid=item.ssid, groupId=item.groupBackupId?.let(groupMap::getValue), location=item.location, notes=item.notes, createdAt=item.createdAt, updatedAt=item.updatedAt, lastSeenAt=item.lastSeenAt, lastSeenRssi=item.lastSeenRssi, isEnabled=item.isEnabled, workspaceId=workspaceMap.getValue(item.workspaceBackupId))) }
                dao.insertBssids(data.bssids.map { item -> WifiDeviceBssidEntity(deviceId=deviceMap.getValue(item.deviceBackupId), bssid=item.value.uppercase(), band=item.band, label=item.label, createdAt=item.createdAt, workspaceId=workspaceMap.getValue(item.workspaceBackupId)) })
                data.photos.forEach { item ->
                    val workspaceId=workspaceMap.getValue(item.workspaceBackupId); val deviceId=deviceMap.getValue(item.deviceBackupId); val name="${UUID.randomUUID()}.${item.fileName.substringAfterLast('.', "jpg")}"; val final=File(context.filesDir,"devices/$workspaceId/$deviceId/photos/$name"); final.parentFile?.mkdirs()
                    File(staged,item.backupId).copyTo(final); newFiles += final
                    dao.insertPhoto(DevicePhotoEntity(deviceId=deviceId, workspaceId=workspaceId, fileName=name, mimeType=item.mimeType, width=item.width, height=item.height, fileSize=final.length(), sortOrder=item.sortOrder, caption=item.caption, isPrimary=item.isPrimary, createdAt=item.createdAt, updatedAt=item.updatedAt))
                }
            }
            if (mode == RestoreMode.REPLACE) oldPhotoFiles.filter { it !in newFiles }.forEach { it.delete() }
            RestoreResult(workspaceMap.values.toList())
        } catch (e: Exception) { newFiles.forEach { it.delete() }; throw e }
        finally { staged.deleteRecursively() }
    }
}

private fun java.io.InputStream.copyTo(output: java.io.OutputStream, limit: Long) { val buffer=ByteArray(DEFAULT_BUFFER_SIZE); var total=0L; while(true){ val n=read(buffer); if(n<0) break; total+=n; if(total>limit) throw BackupException(BackupException.Code.LIMIT_EXCEEDED,"ZIPファイルが大きすぎます"); output.write(buffer,0,n) } }
