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
            if (!file.isFile) throw BackupException(BackupException.Code.MISSING_FILE, "PHOTO_MISSING:${photo.fileName}")
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
        if (zipFile.length() > BackupLimits.MAX_ZIP_BYTES) limit("ZIP_LIMIT_EXCEEDED")
        destination.mkdirs()
        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > BackupLimits.MAX_ENTRIES) limit("ENTRY_LIMIT_EXCEEDED")
                val names = mutableSetOf<String>(); var total = 0L
                entries.forEach { entry ->
                    val path = BackupSecurity.validatePath(entry.name)
                    if (!names.add(path.lowercase())) throw BackupException(BackupException.Code.UNSAFE_PATH, "DUPLICATE_PATH:$path")
                    if (entry.isDirectory) return@forEach
                    val declared = entry.size
                    if (declared < 0 || declared > BackupLimits.MAX_FILE_BYTES) limit("FILE_LIMIT_EXCEEDED:$path")
                    if (path.endsWith(".json") && declared > BackupLimits.MAX_JSON_BYTES) limit("JSON_LIMIT_EXCEEDED:$path")
                    if (path.startsWith("photos/") && declared > BackupLimits.MAX_PHOTO_BYTES) limit("PHOTO_LIMIT_EXCEEDED:$path")
                    if (entry.compressedSize > 0 && declared / entry.compressedSize > BackupLimits.MAX_COMPRESSION_RATIO) limit("COMPRESSION_RATIO_EXCEEDED:$path")
                    total += declared; if (total > BackupLimits.MAX_EXPANDED_BYTES) limit("EXPANDED_LIMIT_EXCEEDED")
                    val target = BackupSecurity.resolve(destination, path); target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    if (target.length() != declared) throw BackupException(BackupException.Code.INVALID_ZIP, "ENTRY_SIZE_MISMATCH:$path")
                }
            }
            val manifestFile = File(destination, "manifest.json")
            if (!manifestFile.isFile) throw BackupException(BackupException.Code.MISSING_MANIFEST, "MANIFEST_MISSING")
            val manifest = decode<BackupManifest>(manifestFile)
            if (manifest.formatName != BACKUP_FORMAT_NAME || manifest.formatVersion < 1) throw BackupException(BackupException.Code.UNSUPPORTED_FORMAT, "UNSUPPORTED_FORMAT")
            if (manifest.formatVersion > BACKUP_FORMAT_VERSION) throw BackupException(BackupException.Code.UNSUPPORTED_FORMAT, "NEWER_FORMAT_VERSION")
            manifest.checksums.forEach { expected ->
                val file = BackupSecurity.resolve(destination, expected.path)
                if (!file.isFile) throw BackupException(BackupException.Code.MISSING_FILE, "FILE_MISSING:${expected.path}")
                if (file.length() != expected.size || BackupSecurity.sha256(file) != expected.sha256) throw BackupException(BackupException.Code.CHECKSUM_MISMATCH, "CHECKSUM_MISMATCH:${expected.path}")
            }
            val data = BackupData(decode(File(destination,"data/workspaces.json")), decode(File(destination,"data/groups.json")), decode(File(destination,"data/devices.json")), decode(File(destination,"data/bssids.json")), decode(File(destination,"data/photos.json")))
            BackupValidator.validate(manifest, data, destination)
            return BackupPreview(manifest, data, destination)
        } catch (error: BackupException) { throw error }
        catch (error: Exception) { throw BackupException(BackupException.Code.INVALID_ZIP, "ARCHIVE_UNREADABLE", error) }
    }
    private inline fun <reified T> decode(file: File): T {
        if (!file.isFile) throw BackupException(BackupException.Code.MISSING_FILE, "FILE_MISSING:${file.name}")
        return try { json.decodeFromString(file.readText()) } catch (e: Exception) { throw BackupException(BackupException.Code.INVALID_JSON, "INVALID_JSON:${file.name}", e) }
    }
}

object BackupValidator {
    fun validate(manifest: BackupManifest, data: BackupData, root: File) {
        if (data.workspaces.isEmpty()) throw BackupException(BackupException.Code.INVALID_REFERENCE, "NO_WORKSPACE")
        if (data.photos.size > BackupLimits.MAX_PHOTOS) limit("PHOTO_COUNT_LIMIT_EXCEEDED")
        fun unique(values: List<String>) { if (values.size != values.distinct().size) throw BackupException(BackupException.Code.INVALID_REFERENCE, "DUPLICATE_BACKUP_ID") }
        unique(data.workspaces.map { it.backupId }); unique(data.groups.map { it.backupId }); unique(data.devices.map { it.backupId }); unique(data.bssids.map { it.backupId }); unique(data.photos.map { it.backupId })
        val workspaceIds = data.workspaces.map { it.backupId }.toSet(); val groupIds = data.groups.map { it.backupId }.toSet(); val deviceIds = data.devices.map { it.backupId }.toSet()
        if (data.groups.any { it.workspaceBackupId !in workspaceIds } || data.devices.any { it.workspaceBackupId !in workspaceIds || (it.groupBackupId != null && it.groupBackupId !in groupIds) } || data.bssids.any { it.deviceBackupId !in deviceIds || it.workspaceBackupId !in workspaceIds } || data.photos.any { it.deviceBackupId !in deviceIds || it.workspaceBackupId !in workspaceIds }) throw BackupException(BackupException.Code.INVALID_REFERENCE, "INVALID_BACKUP_REFERENCE")
        val duplicates = data.bssids.groupBy { it.workspaceBackupId to it.value.uppercase() }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) throw BackupException(BackupException.Code.DUPLICATE_BSSID, "DUPLICATE_BSSID")
        if (data.photos.any { !BackupSecurity.resolve(root, it.archivePath).isFile }) throw BackupException(BackupException.Code.MISSING_FILE, "PHOTO_MISSING")
        if (data.photos.any { BackupSecurity.resolve(root, it.archivePath).length() != it.fileSize }) throw BackupException(BackupException.Code.CHECKSUM_MISMATCH, "PHOTO_SIZE_MISMATCH")
        val required = setOf("data/workspaces.json","data/groups.json","data/devices.json","data/bssids.json","data/photos.json") + data.photos.map { it.archivePath }
        if (manifest.checksums.map { it.path }.toSet() != required) throw BackupException(BackupException.Code.CHECKSUM_MISMATCH, "CHECKSUM_LIST_MISMATCH")
        if (manifest.workspaceCount != data.workspaces.size || manifest.deviceCount != data.devices.size || manifest.groupCount != data.groups.size || manifest.bssidCount != data.bssids.size || manifest.photoCount != data.photos.size) throw BackupException(BackupException.Code.INVALID_REFERENCE, "MANIFEST_COUNT_MISMATCH")
    }
}

internal val BackupJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
private fun limit(message: String): Nothing = throw BackupException(BackupException.Code.LIMIT_EXCEEDED, message)
