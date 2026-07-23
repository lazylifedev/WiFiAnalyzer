package com.lazyapps.wifianalyzer.data.backup

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupArchiveWriter(private val json: Json = BackupJson) {
    fun write(output: OutputStream, data: BackupData, manifestFactory: (List<BackupChecksum>) -> BackupManifest, photoFile: (BackupPhoto) -> File): BackupManifest {
        val jsonEntries = linkedMapOf(
            "data/workspaces.json" to json.encodeToString(data.workspaces).encodeToByteArray(),
            "data/groups.json" to json.encodeToString(data.groups).encodeToByteArray(),
            "data/devices.json" to json.encodeToString(data.devices).encodeToByteArray(),
            "data/bssids.json" to json.encodeToString(data.bssids).encodeToByteArray(),
            "data/photos.json" to json.encodeToString(data.photos).encodeToByteArray(),
        )
        val checksums = jsonEntries.map { (path, bytes) -> BackupChecksum(path, bytes.size.toLong(), BackupSecurity.sha256(bytes)) }.toMutableList()
        data.photos.forEach { photo ->
            val file = photoFile(photo)
            if (!file.isFile) throw BackupException(BackupException.Code.MISSING_FILE, "写真が見つかりません: ${photo.fileName}")
            checksums += BackupChecksum(photo.archivePath, file.length(), BackupSecurity.sha256(file))
        }
        val manifest = manifestFactory(checksums)
        ZipOutputStream(output.buffered()).use { zip ->
            jsonEntries.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
            data.photos.forEach { photo -> zip.putNextEntry(ZipEntry(photo.archivePath)); photoFile(photo).inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(json.encodeToString(manifest).encodeToByteArray()); zip.closeEntry()
        }
        return manifest
    }
}

class BackupArchiveReader(private val json: Json = BackupJson) {
    fun read(zipFile: File, destination: File): BackupPreview {
        if (zipFile.length() > BackupLimits.MAX_ZIP_BYTES) limit("ZIPファイルが大きすぎます")
        destination.mkdirs()
        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > BackupLimits.MAX_ENTRIES) limit("ZIPエントリ数が上限を超えています")
                val names = mutableSetOf<String>(); var total = 0L
                entries.forEach { entry ->
                    val path = BackupSecurity.validatePath(entry.name)
                    if (!names.add(path.lowercase())) throw BackupException(BackupException.Code.UNSAFE_PATH, "ZIP内に重複パスがあります: $path")
                    if (entry.isDirectory) return@forEach
                    val declared = entry.size
                    if (declared < 0 || declared > BackupLimits.MAX_FILE_BYTES) limit("ZIP内ファイルが大きすぎます: $path")
                    if (path.endsWith(".json") && declared > BackupLimits.MAX_JSON_BYTES) limit("JSONが大きすぎます: $path")
                    if (path.startsWith("photos/") && declared > BackupLimits.MAX_PHOTO_BYTES) limit("写真が大きすぎます: $path")
                    if (entry.compressedSize > 0 && declared / entry.compressedSize > BackupLimits.MAX_COMPRESSION_RATIO) limit("圧縮率が高すぎます: $path")
                    total += declared; if (total > BackupLimits.MAX_EXPANDED_BYTES) limit("展開サイズが上限を超えています")
                    val target = BackupSecurity.resolve(destination, path); target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    if (target.length() != declared) throw BackupException(BackupException.Code.INVALID_ZIP, "ZIPエントリのサイズが一致しません: $path")
                }
            }
            val manifestFile = File(destination, "manifest.json")
            if (!manifestFile.isFile) throw BackupException(BackupException.Code.MISSING_MANIFEST, "manifest.json がありません")
            val manifest = decode<BackupManifest>(manifestFile)
            if (manifest.formatName != BACKUP_FORMAT_NAME || manifest.formatVersion < 1) throw BackupException(BackupException.Code.UNSUPPORTED_FORMAT, "対応していないバックアップ形式です")
            if (manifest.formatVersion > BACKUP_FORMAT_VERSION) throw BackupException(BackupException.Code.UNSUPPORTED_FORMAT, "このバックアップは新しいバージョンのアプリで作成されています")
            manifest.checksums.forEach { expected ->
                val file = BackupSecurity.resolve(destination, expected.path)
                if (!file.isFile) throw BackupException(BackupException.Code.MISSING_FILE, "ファイルがありません: ${expected.path}")
                if (file.length() != expected.size || BackupSecurity.sha256(file) != expected.sha256) throw BackupException(BackupException.Code.CHECKSUM_MISMATCH, "チェックサム不一致: ${expected.path}")
            }
            val data = BackupData(decode(File(destination,"data/workspaces.json")), decode(File(destination,"data/groups.json")), decode(File(destination,"data/devices.json")), decode(File(destination,"data/bssids.json")), decode(File(destination,"data/photos.json")))
            BackupValidator.validate(manifest, data, destination)
            return BackupPreview(manifest, data, destination)
        } catch (error: BackupException) { throw error }
        catch (error: Exception) { throw BackupException(BackupException.Code.INVALID_ZIP, "バックアップを読み込めません", error) }
    }
    private inline fun <reified T> decode(file: File): T {
        if (!file.isFile) throw BackupException(BackupException.Code.MISSING_FILE, "ファイルがありません: ${file.name}")
        return try { json.decodeFromString(file.readText()) } catch (e: Exception) { throw BackupException(BackupException.Code.INVALID_JSON, "JSONが不正です: ${file.name}", e) }
    }
}

object BackupValidator {
    fun validate(manifest: BackupManifest, data: BackupData, root: File) {
        if (data.workspaces.isEmpty()) throw BackupException(BackupException.Code.INVALID_REFERENCE, "ワークスペースが0件です")
        if (data.photos.size > BackupLimits.MAX_PHOTOS) limit("写真数が上限を超えています")
        fun unique(values: List<String>) { if (values.size != values.distinct().size) throw BackupException(BackupException.Code.INVALID_REFERENCE, "backupIdが重複しています") }
        unique(data.workspaces.map { it.backupId }); unique(data.groups.map { it.backupId }); unique(data.devices.map { it.backupId }); unique(data.bssids.map { it.backupId }); unique(data.photos.map { it.backupId })
        val workspaceIds = data.workspaces.map { it.backupId }.toSet(); val groupIds = data.groups.map { it.backupId }.toSet(); val deviceIds = data.devices.map { it.backupId }.toSet()
        if (data.groups.any { it.workspaceBackupId !in workspaceIds } || data.devices.any { it.workspaceBackupId !in workspaceIds || (it.groupBackupId != null && it.groupBackupId !in groupIds) } || data.bssids.any { it.deviceBackupId !in deviceIds || it.workspaceBackupId !in workspaceIds } || data.photos.any { it.deviceBackupId !in deviceIds || it.workspaceBackupId !in workspaceIds }) throw BackupException(BackupException.Code.INVALID_REFERENCE, "backupId参照が不正です")
        val duplicates = data.bssids.groupBy { it.workspaceBackupId to it.value.uppercase() }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) throw BackupException(BackupException.Code.DUPLICATE_BSSID, "同一ワークスペース内でBSSIDが重複しています")
        if (data.photos.any { !BackupSecurity.resolve(root, it.archivePath).isFile }) throw BackupException(BackupException.Code.MISSING_FILE, "写真が欠落しています")
        if (data.photos.any { BackupSecurity.resolve(root, it.archivePath).length() != it.fileSize }) throw BackupException(BackupException.Code.CHECKSUM_MISMATCH, "写真サイズが一致しません")
        val required = setOf("data/workspaces.json","data/groups.json","data/devices.json","data/bssids.json","data/photos.json") + data.photos.map { it.archivePath }
        if (manifest.checksums.map { it.path }.toSet() != required) throw BackupException(BackupException.Code.CHECKSUM_MISMATCH, "チェックサム一覧が不完全です")
        if (manifest.workspaceCount != data.workspaces.size || manifest.deviceCount != data.devices.size || manifest.groupCount != data.groups.size || manifest.bssidCount != data.bssids.size || manifest.photoCount != data.photos.size) throw BackupException(BackupException.Code.INVALID_REFERENCE, "manifestの件数が一致しません")
    }
}

internal val BackupJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
private fun limit(message: String): Nothing = throw BackupException(BackupException.Code.LIMIT_EXCEEDED, message)
