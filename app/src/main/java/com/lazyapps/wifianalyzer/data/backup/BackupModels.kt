package com.lazyapps.wifianalyzer.data.backup

import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_NAME = "WiFiAnalyzerBackup"
const val BACKUP_FORMAT_VERSION = 1

@Serializable enum class BackupType { all, workspace }
@Serializable data class BackupChecksum(val path: String, val size: Long, val sha256: String)
@Serializable data class BackupManifest(
    val formatName: String = BACKUP_FORMAT_NAME,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val appPackage: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val createdAt: Long,
    val backupType: BackupType,
    val workspaceCount: Int,
    val deviceCount: Int,
    val groupCount: Int,
    val bssidCount: Int,
    val photoCount: Int,
    val totalPhotoBytes: Long,
    val databaseSchemaVersion: Int,
    val deviceManufacturer: String,
    val androidVersion: String,
    val encryption: String = "none",
    val checksums: List<BackupChecksum>,
    val optionalFeatures: List<String> = emptyList(),
)
@Serializable data class BackupWorkspace(val backupId: String, val name: String, val sortOrder: Int, val createdAt: Long, val updatedAt: Long)
@Serializable data class BackupGroup(val backupId: String, val workspaceBackupId: String, val name: String, val sortOrder: Int, val createdAt: Long, val updatedAt: Long)
@Serializable data class BackupDevice(val backupId: String, val workspaceBackupId: String, val displayName: String, val manufacturer: String, val model: String, val serialNumber: String, val primaryBssid: String, val ssid: String, val groupBackupId: String?, val location: String, val notes: String, val createdAt: Long, val updatedAt: Long, val lastSeenAt: Long?, val lastSeenRssi: Int?, val isEnabled: Boolean)
@Serializable data class BackupBssid(val backupId: String, val deviceBackupId: String, val workspaceBackupId: String, val value: String, val band: String, val label: String, val createdAt: Long)
@Serializable data class BackupPhoto(val backupId: String, val deviceBackupId: String, val workspaceBackupId: String, val fileName: String, val archivePath: String, val mimeType: String, val width: Int, val height: Int, val fileSize: Long, val sortOrder: Int, val caption: String, val isPrimary: Boolean, val createdAt: Long, val updatedAt: Long)
@Serializable data class BackupData(val workspaces: List<BackupWorkspace>, val groups: List<BackupGroup>, val devices: List<BackupDevice>, val bssids: List<BackupBssid>, val photos: List<BackupPhoto>)

data class BackupPreview(val manifest: BackupManifest, val data: BackupData, val extractedDirectory: java.io.File)
enum class RestoreMode { ADD, REPLACE }
data class RestoreResult(val workspaceIds: List<Long>)

class BackupException(val code: Code, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Code { INVALID_ZIP, UNSAFE_PATH, LIMIT_EXCEEDED, MISSING_MANIFEST, UNSUPPORTED_FORMAT, INVALID_JSON, MISSING_FILE, CHECKSUM_MISMATCH, INVALID_REFERENCE, DUPLICATE_BSSID, PHOTO_WRITE_FAILED }
}
