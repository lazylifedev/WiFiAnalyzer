package com.lazyapps.wifianalyzer.kintone

import android.content.Context
import com.lazyapps.wifianalyzer.data.registry.DevicePhotoEntity
import java.io.File
import java.security.MessageDigest

data class KintonePhotoCandidate(
    val photo: DevicePhotoEntity,
    val file: File,
    val sha256: String,
)

object KintonePhotoFingerprint {
    fun create(photos: List<KintonePhotoCandidate>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        photos.sortedWith(compareBy({ it.photo.sortOrder }, { it.photo.id })).forEach { item ->
            val part = "${item.photo.id}:${item.photo.sortOrder}:${item.sha256}:${item.photo.isPrimary};"
            digest.update(part.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class KintonePhotoSyncStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kintone_photo_sync", Context.MODE_PRIVATE)
    private fun key(workspaceUuid: String, deviceUuid: String) = "$workspaceUuid.$deviceUuid.fingerprint"
    fun read(workspaceUuid: String, deviceUuid: String): String? = prefs.getString(key(workspaceUuid, deviceUuid), null)
    fun write(workspaceUuid: String, deviceUuid: String, fingerprint: String) = prefs.edit().putString(key(workspaceUuid, deviceUuid), fingerprint).apply()
    fun removeWorkspace(workspaceUuid: String) = prefs.edit().also { editor -> prefs.all.keys.filter { it.startsWith("$workspaceUuid.") }.forEach(editor::remove) }.apply()
}
