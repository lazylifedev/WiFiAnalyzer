package com.lazyapps.wifianalyzer.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.data.registry.DevicePhotoEntity
import com.lazyapps.wifianalyzer.data.registry.PendingFileDeletionEntity
import com.lazyapps.wifianalyzer.data.registry.RegistryValidationException
import com.lazyapps.wifianalyzer.data.registry.RegistryError
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.domain.DevicePhoto
import com.lazyapps.wifianalyzer.domain.DevicePhotoPolicy
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context, private val database: WifiAnalyzerDatabase) {
    private val dao = database.registryDao()

    fun observe(deviceId: Long): Flow<List<DevicePhoto>> = dao.observePhotos(deviceId).map { items -> items.map(DevicePhotoEntity::domain) }
    fun file(photo: DevicePhoto): File = File(context.filesDir, "devices/${photo.workspaceId}/${photo.deviceId}/photos/${photo.fileName}")
    suspend fun ensureCapacity(deviceId: Long) { if (deviceId != 0L && dao.getPhotos(deviceId).size >= DevicePhotoPolicy.MAX_PHOTOS_PER_DEVICE) throw RegistryValidationException(RegistryError.PHOTO_LIMIT) }

    suspend fun save(deviceId: Long, workspaceId: Long, source: Uri): Long = withContext(Dispatchers.IO) {
        val existing = dao.getPhotos(deviceId)
        if (existing.size >= DevicePhotoPolicy.MAX_PHOTOS_PER_DEVICE) throw RegistryValidationException(RegistryError.PHOTO_LIMIT)
        val device = dao.getDevice(deviceId) ?: throw RegistryValidationException(RegistryError.DEVICE_NOT_FOUND)
        if (device.workspaceId != workspaceId) throw RegistryValidationException(RegistryError.WORKSPACE_NOT_FOUND)
        val directory = File(context.filesDir, "devices/$workspaceId/$deviceId/photos").apply { mkdirs() }
        val temp = File(directory, ".${UUID.randomUUID()}.tmp")
        val final = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            val bitmap = try {
                decodeOriented(source)
            } catch (_: OutOfMemoryError) {
                throw RegistryValidationException(RegistryError.PHOTO_OUT_OF_MEMORY)
            }
            val scaled = try {
                scale(bitmap)
            } catch (_: OutOfMemoryError) {
                bitmap.recycle()
                throw RegistryValidationException(RegistryError.PHOTO_OUT_OF_MEMORY)
            }
            if (scaled !== bitmap) bitmap.recycle()
            val width = scaled.width; val height = scaled.height
            val compressed = try {
                temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            } finally {
                scaled.recycle()
            }
            if (!compressed) throw RegistryValidationException(RegistryError.INVALID_PHOTO)
            if (!temp.renameTo(final)) throw RegistryValidationException(RegistryError.PHOTO_WRITE_FAILED)
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
        val resolver = context.contentResolver
        val declaredSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (declaredSize > MAX_SOURCE_BYTES) throw RegistryValidationException(RegistryError.PHOTO_TOO_LARGE)
        var boundedCopy: File? = null
        return try {
            val source = if (declaredSize >= 0L) {
                ImageDecoder.createSource(resolver, uri)
            } else {
                boundedCopy = copyUnknownLengthSource(uri)
                ImageDecoder.createSource(boundedCopy!!)
            }
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                if (width <= 0 || height <= 0) throw RegistryValidationException(RegistryError.INVALID_PHOTO)
                val edge = maxOf(width, height)
                if (edge > DevicePhotoPolicy.MAX_LONG_EDGE) {
                    val ratio = DevicePhotoPolicy.MAX_LONG_EDGE.toFloat() / edge
                    decoder.setTargetSize(
                        maxOf(1, (width * ratio).toInt()),
                        maxOf(1, (height * ratio).toInt()),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (error: RegistryValidationException) {
            throw error
        } catch (_: ImageDecoder.DecodeException) {
            throw RegistryValidationException(RegistryError.INVALID_PHOTO)
        } catch (_: IOException) {
            throw RegistryValidationException(RegistryError.INVALID_PHOTO)
        } catch (_: SecurityException) {
            throw RegistryValidationException(RegistryError.INVALID_PHOTO)
        } catch (_: IllegalArgumentException) {
            throw RegistryValidationException(RegistryError.INVALID_PHOTO)
        } finally {
            boundedCopy?.delete()
        }
    }

    private fun copyUnknownLengthSource(uri: Uri): File {
        val copy = File.createTempFile("photo-source-", ".image", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw RegistryValidationException(RegistryError.INVALID_PHOTO)
            input.use { source ->
                copy.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_SOURCE_BYTES) throw RegistryValidationException(RegistryError.PHOTO_TOO_LARGE)
                        target.write(buffer, 0, count)
                    }
                }
            }
            return copy
        } catch (error: Exception) {
            copy.delete()
            throw error
        }
    }
    private fun scale(bitmap: Bitmap): Bitmap { val edge = maxOf(bitmap.width, bitmap.height); if (edge <= DevicePhotoPolicy.MAX_LONG_EDGE) return bitmap; val ratio = DevicePhotoPolicy.MAX_LONG_EDGE.toFloat() / edge; return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true) }
    private companion object { const val MAX_SOURCE_BYTES = 30 * 1024 * 1024 }
}

private fun DevicePhotoEntity.domain() = DevicePhoto(id, deviceId, workspaceId, fileName, mimeType, width, height, fileSize, sortOrder, caption, isPrimary, createdAt, updatedAt)
