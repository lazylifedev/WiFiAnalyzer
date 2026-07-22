package com.lazyapps.wifianalyzer.domain

import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import java.text.Normalizer
import kotlin.math.abs

object BssidFormat {
    private val hex = Regex("^[0-9A-F]{12}$")

    fun normalize(value: String): String? {
        val compact = value.trim().replace(Regex("[:-]"), "").uppercase()
        if (!hex.matches(compact)) return null
        return compact.chunked(2).joinToString(":")
    }

    fun isValid(value: String): Boolean = normalize(value) != null

    fun hasDuplicates(values: List<String>): Boolean {
        val normalized = values.mapNotNull(::normalize)
        return normalized.size != normalized.distinct().size
    }
}

object GroupNameFormat {
    fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase()

    fun isDuplicate(candidate: String, existing: Collection<String>): Boolean =
        existing.map(::normalize).contains(normalize(candidate))
}

data class DeviceBssidInput(val bssid: String, val band: String, val label: String = "")

data class DeviceInput(
    val id: Long = 0,
    val displayName: String,
    val manufacturer: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val ssid: String = "",
    val groupId: Long? = null,
    val location: String = "",
    val notes: String = "",
    val bssids: List<DeviceBssidInput>,
    val initialLastSeenAt: Long? = null,
    val initialLastSeenRssi: Int? = null,
)

data class RegisteredDevice(
    val id: Long,
    val displayName: String,
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val ssid: String,
    val groupId: Long?,
    val groupName: String?,
    val location: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSeenAt: Long?,
    val lastSeenRssi: Int?,
    val isEnabled: Boolean,
    val bssids: List<RegisteredBssid>,
) {
    val primaryBssid: String get() = bssids.firstOrNull()?.bssid.orEmpty()
}

data class RegisteredBssid(val id: Long, val bssid: String, val band: String, val label: String)
data class DeviceGroup(val id: Long, val name: String, val sortOrder: Int)

data class RegisteredMatch(
    val deviceId: Long,
    val deviceName: String,
    val groupName: String?,
    val bssid: String,
)

object DeviceMatching {
    fun index(devices: List<RegisteredDevice>): Map<String, RegisteredMatch> = buildMap {
        devices.filter { it.isEnabled }.forEach { device ->
            device.bssids.forEach { address ->
                BssidFormat.normalize(address.bssid)?.let { normalized ->
                    put(normalized, RegisteredMatch(device.id, device.displayName, device.groupName, normalized))
                }
            }
        }
    }

    fun match(accessPoint: WifiAccessPoint, index: Map<String, RegisteredMatch>): RegisteredMatch? =
        BssidFormat.normalize(accessPoint.bssid)?.let(index::get)
}

object DetectionPolicy {
    const val DETECTION_WINDOW_MS = 45_000L
    const val MIN_UPDATE_INTERVAL_MS = 60_000L
    const val RSSI_UPDATE_THRESHOLD = 5

    fun isDetected(lastSeenAt: Long?, now: Long): Boolean = lastSeenAt != null && now - lastSeenAt <= DETECTION_WINDOW_MS

    fun shouldUpdate(lastSeenAt: Long?, lastSeenRssi: Int?, observedAt: Long, rssi: Int): Boolean =
        lastSeenAt == null ||
            observedAt - lastSeenAt >= MIN_UPDATE_INTERVAL_MS ||
            lastSeenRssi == null ||
            abs(rssi - lastSeenRssi) >= RSSI_UPDATE_THRESHOLD
}

interface ManufacturerCandidateProvider {
    fun candidateFor(normalizedBssid: String): String?
}

object OfflineManufacturerCandidateProvider : ManufacturerCandidateProvider {
    private val knownPrefixes = mapOf(
        "38:45:3B" to "",
    )

    override fun candidateFor(normalizedBssid: String): String? = knownPrefixes[normalizedBssid.take(8)]?.ifBlank { null }
}
