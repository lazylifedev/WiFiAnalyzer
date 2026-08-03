package com.lazyapps.wifianalyzer.export

import android.content.Context
import android.util.Base64
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.data.registry.DevicePhotoEntity
import com.lazyapps.wifianalyzer.data.registry.RegisteredWifiDeviceEntity
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.pow

class ExportRepository(private val context: Context, database: WifiAnalyzerDatabase) {
    private val dao = database.registryDao()

    suspend fun dataset(type: ExportType, target: ExportTarget, unit: DistanceUnitPreference): ExportDataset = withContext(Dispatchers.IO) {
        val workspaces = dao.getWorkspacesOnce().let { all -> if (target.scope == ExportScope.ALL_WORKSPACES) all else all.filter { it.id == target.workspaceId } }
        val workspaceIds = workspaces.map { it.id }.toSet()
        val workspaceNames = workspaces.associate { it.id to it.name }
        val groups = dao.getAllGroups().filter { it.workspaceId in workspaceIds }.associateBy { it.id }
        val allDevices = dao.getAllDevices().filter { it.workspaceId in workspaceIds }.filterTarget(target)
        val deviceIds = allDevices.map { it.id }.toSet()
        val bssids = dao.getAllBssids().filter { it.deviceId in deviceIds }
        val photos = dao.getAllPhotos().filter { it.deviceId in deviceIds }
        val bssidMap = bssids.groupBy { it.deviceId }; val photoMap = photos.groupBy { it.deviceId }
        val rows = when (type) {
            ExportType.DEVICES -> allDevices.map { d -> deviceRow(d, workspaceNames[d.workspaceId].orEmpty(), groups[d.groupId]?.name, bssidMap[d.id].orEmpty().map { it.bssid }, photoMap[d.id].orEmpty(), unit) }
            ExportType.BSSIDS -> bssids.mapNotNull { b -> allDevices.firstOrNull { it.id == b.deviceId }?.let { d -> bssidRow(d, b.bssid, b.band, b.label, workspaceNames[d.workspaceId].orEmpty(), groups[d.groupId]?.name, unit) } }
            ExportType.PHOTOS -> photos.mapNotNull { p -> allDevices.firstOrNull { it.id == p.deviceId }?.let { d -> photoRow(d, p, workspaceNames[d.workspaceId].orEmpty(), groups[d.groupId]?.name) } }
            ExportType.REPORT -> emptyList()
        }
        val estimate = if (type == ExportType.PHOTOS) photos.sumOf { it.fileSize } else rows.sumOf { row -> row.values.values.sumOf { (it?.length ?: 0) + 3 }.toLong() }
        ExportDataset(type, rows, ExportCounts(allDevices.size, bssids.size, photos.size), if (target.scope == ExportScope.ALL_WORKSPACES) "All" else workspaces.firstOrNull()?.name.orEmpty(), estimate)
    }

    suspend fun reportDevices(target: ExportTarget, unit: DistanceUnitPreference, mode: ReportPhotoMode, locale: Locale, detectedLabel: String, notDetectedLabel: String, allWorkspacesLabel: String): Pair<String, List<ReportDevice>> = withContext(Dispatchers.IO) {
        val workspaces = dao.getWorkspacesOnce().let { if (target.scope == ExportScope.ALL_WORKSPACES) it else it.filter { w -> w.id == target.workspaceId } }
        val ids = workspaces.map { it.id }.toSet(); val names = workspaces.associate { it.id to it.name }; val groups = dao.getAllGroups().associateBy { it.id }
        val devices = dao.getAllDevices().filter { it.workspaceId in ids }.filterTarget(target)
        val bssids = dao.getAllBssids().groupBy { it.deviceId }; val photos = dao.getAllPhotos().groupBy { it.deviceId }
        var remainingPhotoBytes = MAX_REPORT_PHOTO_BYTES
        val rows = devices.map { d ->
            val selectedPhotos = when (mode) { ReportPhotoMode.NONE -> emptyList(); ReportPhotoMode.PRIMARY -> photos[d.id].orEmpty().filter { it.isPrimary }.take(1); ReportPhotoMode.ALL -> photos[d.id].orEmpty() }
            ReportDevice(names[d.workspaceId].orEmpty(), groups[d.groupId]?.name, d.displayName, d.manufacturer, d.model, d.serialNumber, d.ssid, bssids[d.id].orEmpty().map { it.bssid }, d.location, d.notes, if (isDetected(d)) detectedLabel else notDetectedLabel, rssi(d), distance(d.lastSeenRssi, unit, locale), ExportFormat.dateTime(d.lastSeenAt, locale), selectedPhotos.map { photo ->
                val file = photoFile(photo); val uri = if (file.isFile && file.length() <= remainingPhotoBytes && file.length() <= MAX_SINGLE_PHOTO_BYTES) photoDataUri(file, photo.mimeType).also { if (it != null) remainingPhotoBytes -= file.length() } else null
                ReportPhoto(photo.caption, uri)
            })
        }
        (if (target.scope == ExportScope.ALL_WORKSPACES) allWorkspacesLabel else workspaces.firstOrNull()?.name.orEmpty()) to rows
    }

    private fun List<RegisteredWifiDeviceEntity>.filterTarget(target: ExportTarget) = filter { d ->
        (target.deviceId == null || d.id == target.deviceId) && when { target.ungroupedOnly -> d.groupId == null; target.groupId != null -> d.groupId == target.groupId; else -> true }
    }
    private fun base(d: RegisteredWifiDeviceEntity, workspace: String, group: String?) = mutableMapOf<String, String?>("workspaceName" to workspace, "groupName" to group, "deviceName" to d.displayName, "manufacturer" to d.manufacturer, "model" to d.model, "serialNumber" to d.serialNumber, "ssid" to d.ssid, "primaryBssid" to d.primaryBssid, "location" to d.location, "notes" to d.notes, "detectedStatus" to detected(d), "latestRssi" to rssi(d), "lastSeenAt" to ExportFormat.dateTime(d.lastSeenAt), "createdAt" to ExportFormat.dateTime(d.createdAt), "updatedAt" to ExportFormat.dateTime(d.updatedAt))
    private fun deviceRow(d: RegisteredWifiDeviceEntity, workspace: String, group: String?, bssids: List<String>, photos: List<DevicePhotoEntity>, unit: DistanceUnitPreference) = ExportRow(base(d, workspace, group).apply { put("allBssids", bssids.joinToString("; ")); put("estimatedDistance", distance(d.lastSeenRssi, unit)); put("photoCount", photos.size.toString()); put("primaryPhotoCaption", photos.firstOrNull { it.isPrimary }?.caption) })
    private fun bssidRow(d: RegisteredWifiDeviceEntity, bssid: String, band: String, label: String, workspace: String, group: String?, unit: DistanceUnitPreference) = ExportRow(base(d, workspace, group).apply { put("bssid", bssid); put("band", band); put("label", label); put("estimatedDistance", distance(d.lastSeenRssi, unit)); put("channel", ""); put("frequency", ""); put("channelWidth", ""); put("security", "") })
    private fun photoRow(d: RegisteredWifiDeviceEntity, p: DevicePhotoEntity, workspace: String, group: String?) = ExportRow(base(d, workspace, group).apply { put("photoIndex", (p.sortOrder + 1).toString()); put("caption", p.caption); put("isPrimary", if (p.isPrimary) "はい" else "いいえ"); put("mimeType", p.mimeType); put("width", p.width.toString()); put("height", p.height.toString()); put("fileSize", p.fileSize.toString()) })
    private fun isDetected(d: RegisteredWifiDeviceEntity) = d.lastSeenAt != null && System.currentTimeMillis() - d.lastSeenAt <= 45_000
    private fun detected(d: RegisteredWifiDeviceEntity) = if (isDetected(d)) "検出中" else "未検出"
    private fun rssi(d: RegisteredWifiDeviceEntity) = d.lastSeenRssi?.let { "$it dBm" }
    private fun distance(rssi: Int?, unit: DistanceUnitPreference, locale: Locale = Locale.ROOT): String? = rssi?.let { value -> val meters = 10.0.pow((-59 - value) / 20.0); if (unit == DistanceUnitPreference.FEET) "%.2f ft".format(locale, meters * 3.28084) else "%.2f m".format(locale, meters) }
    private fun photoFile(photo: DevicePhotoEntity) = File(context.filesDir, "devices/${photo.workspaceId}/${photo.deviceId}/photos/${photo.fileName}")
    private fun photoDataUri(file: File, mimeType: String): String? = runCatching { "data:$mimeType;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }.getOrNull()
    private companion object { const val MAX_SINGLE_PHOTO_BYTES = 4L * 1024 * 1024; const val MAX_REPORT_PHOTO_BYTES = 12L * 1024 * 1024 }
}
