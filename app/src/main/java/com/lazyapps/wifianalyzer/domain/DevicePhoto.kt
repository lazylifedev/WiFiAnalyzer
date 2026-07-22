package com.lazyapps.wifianalyzer.domain

data class DevicePhoto(
    val id: Long, val deviceId: Long, val workspaceId: Long, val fileName: String, val mimeType: String,
    val width: Int, val height: Int, val fileSize: Long, val sortOrder: Int, val caption: String,
    val isPrimary: Boolean, val createdAt: Long, val updatedAt: Long,
)

object DevicePhotoPolicy { const val MAX_PHOTOS_PER_DEVICE = 9; const val MAX_LONG_EDGE = 2048 }
