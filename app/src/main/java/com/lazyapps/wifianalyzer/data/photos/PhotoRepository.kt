package com.lazyapps.wifianalyzer.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.data.registry.DevicePhotoEntity
import com.lazyapps.wifianalyzer.data.registry.PendingFileDeletionEntity
import com.lazyapps.wifianalyzer.data.registry.RegistryValidationException
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.domain.DevicePhoto
import com.lazyapps.wifianalyzer.domain.DevicePhotoPolicy
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context, private val database: WifiAnalyzerDatabase) {
    private val dao = database.registryDao()

    fun observe(deviceId: Long): Flow<List<DevicePhoto>> = dao.observePhotos(deviceId).map { items -> items.map(DevicePhotoEntity::domain) }
    fun file(photo: DevicePhoto): File = File(context.filesDir, "devices/${photo.workspaceId}/${photo.deviceId}/photos/${photo.fileName}")
    suspend fun ensureCapacity(deviceId: Long) { if (deviceId != 0L && dao.getPhotos(deviceId).size >= DevicePhotoPolicy.MAX_PHOTOS_PER_DEVICE) throw RegistryValidationException("写真は9枚まで登録できます") }

    suspend fun save(deviceId: Long, workspaceId: Long, source: Uri): Long = withContext(Dispatchers.IO) {
        val existing = dao.getPhotos(deviceId)
        if (existing.size >= DevicePhotoPolicy.MAX_PHOTOS_PER_DEVICE) throw RegistryValidationException("写真は9枚まで登録できます")
        val device = dao.getDevice(deviceId) ?: throw RegistryValidationException("対象機器が見つかりません")
        if (device.workspaceId != workspaceId) throw RegistryValidationException("対象ワークスペースが変更または削除されました")
        val directory = File(context.filesDir, "devices/$workspaceId/$deviceId/photos").apply { mkdirs() }
        val temp = File(directory, ".${UUID.randomUUID()}.tmp")
        val final = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            val bitmap = decodeOriented(source)
            val scaled = scale(bitmap)
            if (scaled !== bitmap) bitmap.recycle()
            temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            val width = scaled.width; val height = scaled.height
            scaled.recycle()
            if (!temp.renameTo(final)) throw IllegalStateException("画像ファイルを確定できません")
            val now = System.currentTimeMillis()
            try {
                database.withTransaction { dao.insertPhoto(DevicePhotoEntity(deviceId = deviceId, workspaceId = workspaceId, fileName = final.name, mimeType = "image/jpeg", width = width, height = height, fileSize = final.length(), sortOrder = existing.size, isPrimary = existing.isEmpty(), createdAt = now, updatedAt = now)) }
            } catch (e: Exception) { final.delete(); throw e }
        } finally { temp.delete() }
    }

    suspend fun delete(photoId: Long) = delete(setOf(photoId))
    suspend fun delete(photoIds: Set<Long>) = withContext(Dispatchers.IO) {
        val photos = photoIds.mapNotNull { dao.getPhoto(it) }
        if (photos.isEmpty()) return@withContext
        val deviceIds = photos.map { it.deviceId }.toSet()
        database.withTransaction {
            photos.forEach { photo ->
                dao.insertPendingDeletion(PendingFileDeletionEntity(relative(photo), System.currentTimeMillis()))
                dao.deletePhoto(photo.id)
            }
            deviceIds.forEach { deviceId -> dao.getPhotos(deviceId).forEachIndexed { index, item -> dao.updatePhotoOrder(item.id, index, System.currentTimeMillis()) } }
        }
        photos.forEach { deletePending(relative(it)) }
    }

    suspend fun setPrimary(photoId: Long) { val photo = dao.getPhoto(photoId) ?: return; dao.setPrimaryPhoto(photo.deviceId, photoId, System.currentTimeMillis()) }
    suspend fun caption(photoId: Long, caption: String) { dao.getPhoto(photoId)?.let { dao.updatePhoto(it.copy(caption = caption.trim(), updatedAt = System.currentTimeMillis())) } }
    suspend fun move(photoId: Long, direction: Int) = database.withTransaction {
        val photo = dao.getPhoto(photoId) ?: return@withTransaction
        val all = dao.getPhotos(photo.deviceId); val index = all.indexOfFirst { it.id == photoId }; val other = index + direction
        if (index !in all.indices || other !in all.indices) return@withTransaction
        val now = System.currentTimeMillis(); dao.updatePhotoOrder(all[index].id, all[other].sortOrder, now); dao.updatePhotoOrder(all[other].id, all[index].sortOrder, now)
    }

    suspend fun retryPending() = withContext(Dispatchers.IO) { dao.getPendingDeletions().forEach { deletePending(it.path) } }
    private suspend fun deletePending(path: String) { val target = File(context.filesDir, path); if (!target.exists() || target.delete()) dao.deletePendingDeletion(path) }
    private fun relative(photo: DevicePhotoEntity) = "devices/${photo.workspaceId}/${photo.deviceId}/photos/${photo.fileName}"

    private fun decodeOriented(uri: Uri): Bitmap {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { if (it.length > MAX_SOURCE_BYTES) throw RegistryValidationException("画像ファイルが大きすぎます") }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw RegistryValidationException("画像を読み込めません")
        if (bytes.size > MAX_SOURCE_BYTES) throw RegistryValidationException("画像ファイルが大きすぎます")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw RegistryValidationException("対応していない画像形式です")
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > DevicePhotoPolicy.MAX_LONG_EDGE * 2) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw RegistryValidationException("画像を読み込めません")
        val rotation = context.contentResolver.openInputStream(uri)?.use { input -> ExifInterface(input).rotationDegrees } ?: 0
        if (rotation == 0) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true).also { bitmap.recycle() }
    }
    private fun scale(bitmap: Bitmap): Bitmap { val edge = maxOf(bitmap.width, bitmap.height); if (edge <= DevicePhotoPolicy.MAX_LONG_EDGE) return bitmap; val ratio = DevicePhotoPolicy.MAX_LONG_EDGE.toFloat() / edge; return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true) }
    private companion object { const val MAX_SOURCE_BYTES = 30 * 1024 * 1024 }
}

private fun DevicePhotoEntity.domain() = DevicePhoto(id, deviceId, workspaceId, fileName, mimeType, width, height, fileSize, sortOrder, caption, isPrimary, createdAt, updatedAt)
