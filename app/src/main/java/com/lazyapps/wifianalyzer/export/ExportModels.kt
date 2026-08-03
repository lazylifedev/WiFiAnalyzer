package com.lazyapps.wifianalyzer.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class ExportType(val filePart: String) {
    DEVICES("Devices"), BSSIDS("BSSID"), PHOTOS("Photos"), REPORT("Report")
}
enum class ExportScope { CURRENT_WORKSPACE, ALL_WORKSPACES }
enum class ReportPhotoMode { NONE, PRIMARY, ALL }

data class ExportTarget(val scope: ExportScope = ExportScope.CURRENT_WORKSPACE, val workspaceId: Long, val groupId: Long? = null, val ungroupedOnly: Boolean = false, val deviceId: Long? = null)
data class ExportCounts(val devices: Int = 0, val bssids: Int = 0, val photos: Int = 0)
data class ExportHistory(val lastCsvAt: Long? = null, val lastCsvType: ExportType? = null, val lastCsvCount: Int = 0, val lastReportAt: Long? = null, val lastReportTarget: String? = null, val succeeded: Boolean? = null)

data class ExportColumn(val key: String, val header: String, val required: Boolean = false)

object ExportColumns {
    val devices = listOf(
        ExportColumn("workspaceName", "ワークスペース"), ExportColumn("groupName", "グループ"), ExportColumn("deviceName", "機器名"),
        ExportColumn("manufacturer", "メーカー"), ExportColumn("model", "型番"), ExportColumn("serialNumber", "シリアル番号"),
        ExportColumn("ssid", "SSID"), ExportColumn("primaryBssid", "主BSSID"), ExportColumn("allBssids", "全BSSID"),
        ExportColumn("location", "設置場所"), ExportColumn("notes", "メモ"), ExportColumn("detectedStatus", "検出状態"),
        ExportColumn("latestRssi", "最新RSSI"), ExportColumn("estimatedDistance", "推定距離"), ExportColumn("lastSeenAt", "最終検出日時"),
        ExportColumn("photoCount", "写真枚数"), ExportColumn("primaryPhotoCaption", "メイン写真説明"), ExportColumn("createdAt", "登録日時"), ExportColumn("updatedAt", "更新日時"),
    )
    val bssids = listOf(
        ExportColumn("workspaceName", "ワークスペース"), ExportColumn("groupName", "グループ"), ExportColumn("deviceName", "機器名"), ExportColumn("ssid", "SSID"),
        ExportColumn("bssid", "BSSID", true), ExportColumn("band", "周波数帯"), ExportColumn("label", "ラベル"), ExportColumn("manufacturer", "メーカー"),
        ExportColumn("model", "型番"), ExportColumn("latestRssi", "最新RSSI"), ExportColumn("channel", "チャンネル"), ExportColumn("frequency", "周波数"),
        ExportColumn("channelWidth", "チャンネル幅"), ExportColumn("security", "セキュリティ"), ExportColumn("detectedStatus", "検出状態"), ExportColumn("lastSeenAt", "最終検出日時"),
    )
    val photos = listOf(
        ExportColumn("workspaceName", "ワークスペース"), ExportColumn("groupName", "グループ"), ExportColumn("deviceName", "機器名"), ExportColumn("photoIndex", "写真番号"),
        ExportColumn("caption", "説明"), ExportColumn("isPrimary", "メイン写真"), ExportColumn("mimeType", "MIMEタイプ"), ExportColumn("width", "幅"),
        ExportColumn("height", "高さ"), ExportColumn("fileSize", "ファイルサイズ"), ExportColumn("createdAt", "登録日時"),
    )
    fun forType(type: ExportType) = when (type) { ExportType.DEVICES -> devices; ExportType.BSSIDS -> bssids; ExportType.PHOTOS -> photos; ExportType.REPORT -> emptyList() }
    fun minimum(type: ExportType) = when (type) { ExportType.DEVICES -> setOf("deviceName", "primaryBssid"); ExportType.BSSIDS -> setOf("bssid"); ExportType.PHOTOS -> setOf("deviceName", "photoIndex"); ExportType.REPORT -> emptySet() }
}

data class ExportRow(val values: Map<String, String?>)
data class ExportDataset(val type: ExportType, val rows: List<ExportRow>, val counts: ExportCounts, val targetLabel: String, val estimatedBytes: Long = 0)

object ExportFormat {
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun dateTime(epochMillis: Long?): String? = epochMillis?.let { dateTime.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
    fun dateTime(epochMillis: Long?, locale: Locale): String? = epochMillis?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
    fun fileStamp(epochMillis: Long) = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    fun safeFilePart(value: String) = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(50).ifBlank { "Workspace" }
}
