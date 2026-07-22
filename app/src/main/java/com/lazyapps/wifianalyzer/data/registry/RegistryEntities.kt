package com.lazyapps.wifianalyzer.data.registry

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wifi_device_groups",
    indices = [Index(value = ["normalized_name"], unique = true)],
)
data class WifiDeviceGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "registered_wifi_devices",
    foreignKeys = [
        ForeignKey(
            entity = WifiDeviceGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("group_id"), Index("display_name")],
)
data class RegisteredWifiDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "display_name") val displayName: String,
    val manufacturer: String = "",
    val model: String = "",
    @ColumnInfo(name = "serial_number") val serialNumber: String = "",
    @ColumnInfo(name = "primary_bssid") val primaryBssid: String,
    val ssid: String = "",
    @ColumnInfo(name = "group_id") val groupId: Long? = null,
    val location: String = "",
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long? = null,
    @ColumnInfo(name = "last_seen_rssi") val lastSeenRssi: Int? = null,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
)

@Entity(
    tableName = "wifi_device_bssids",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredWifiDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("device_id"), Index(value = ["bssid"], unique = true)],
)
data class WifiDeviceBssidEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_id") val deviceId: Long,
    val bssid: String,
    val band: String,
    val label: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
