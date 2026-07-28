package com.lazyapps.wifianalyzer.data

import com.lazyapps.wifianalyzer.model.WifiAccessPoint

data class ScanObservationDecision(
    val accessPoints: List<WifiAccessPoint>,
    val newMeasurementBssids: Set<String>,
    val timestampChangedBssids: Set<String>,
    val rssiChangedBssids: Set<String>,
    val ignoredSameTimestampCount: Int,
    val ignoredRollbackCount: Int,
    val uiChanged: Boolean,
)

/**
 * Reconciles the OS cache without inventing samples. ScanResult timestamps are elapsed-realtime
 * based identifiers, not wall-clock values.
 */
class WifiScanObservationPolicy(
    private val missingGraceMillis: Long = DEFAULT_MISSING_GRACE_MILLIS,
) {
    private val retained = mutableMapOf<String, WifiAccessPoint>()
    private val missingSince = mutableMapOf<String, Long>()
    private val lastMeasurement = mutableMapOf<String, MeasurementIdentity>()
    private var lastPublished: List<WifiAccessPoint> = emptyList()

    @Synchronized
    fun accept(readings: List<WifiAccessPoint>, elapsedRealtimeMillis: Long): ScanObservationDecision {
        val current = readings
            .groupBy { it.bssid.uppercase() }
            .mapValues { (_, duplicates) -> duplicates.maxByOrNull { it.timestampMicros }!! }

        val adopted = mutableMapOf<String, WifiAccessPoint>()
        val newMeasurements = mutableSetOf<String>()
        val timestampChanged = mutableSetOf<String>()
        val rssiChanged = mutableSetOf<String>()
        var ignoredSameTimestamp = 0
        var ignoredRollback = 0

        current.forEach { (bssid, reading) ->
            val previous = retained[bssid]
            val previousIdentity = lastMeasurement[bssid]
            val identity = MeasurementIdentity.from(reading)
            val timestampIsUsable = reading.timestampMicros > 0L
            val isOlder = timestampIsUsable &&
                previousIdentity?.timestampMicros?.let { it > 0L && reading.timestampMicros < it } == true

            if (isOlder && previous != null) {
                adopted[bssid] = previous
                ignoredRollback++
                return@forEach
            }

            val sameFallbackWithoutTimestamp = !timestampIsUsable &&
                previousIdentity?.fallbackFields == identity.fallbackFields
            adopted[bssid] = if (sameFallbackWithoutTimestamp && previous != null) previous else reading
            missingSince.remove(bssid)
            when {
                previousIdentity == null -> newMeasurements += bssid
                timestampIsUsable && reading.timestampMicros > previousIdentity.timestampMicros -> {
                    newMeasurements += bssid
                    timestampChanged += bssid
                }
                timestampIsUsable && reading.timestampMicros == previousIdentity.timestampMicros ->
                    ignoredSameTimestamp++
                !timestampIsUsable && identity.fallbackFields != previousIdentity.fallbackFields ->
                    newMeasurements += bssid
            }
            if (previous != null && reading.rssi != previous.rssi) rssiChanged += bssid
            if (!isOlder) lastMeasurement[bssid] = identity
        }

        retained.forEach { (bssid, previous) ->
            if (bssid !in current) {
                val absentSince = missingSince.getOrPut(bssid) { elapsedRealtimeMillis }
                if (elapsedRealtimeMillis - absentSince < missingGraceMillis) adopted[bssid] = previous
            }
        }

        retained.clear()
        retained.putAll(adopted)
        missingSince.keys.retainAll(retained.keys)

        val published = adopted.values.sortedWith(
            compareByDescending<WifiAccessPoint> { it.rssi }.thenBy { it.bssid },
        )
        val changed = published != lastPublished
        if (changed) lastPublished = published
        return ScanObservationDecision(
            accessPoints = published,
            newMeasurementBssids = newMeasurements,
            timestampChangedBssids = timestampChanged,
            rssiChangedBssids = rssiChanged,
            ignoredSameTimestampCount = ignoredSameTimestamp,
            ignoredRollbackCount = ignoredRollback,
            uiChanged = changed,
        )
    }

    private data class MeasurementIdentity(
        val timestampMicros: Long,
        val fallbackFields: List<Any>,
    ) {
        companion object {
            fun from(ap: WifiAccessPoint) = MeasurementIdentity(
                timestampMicros = ap.timestampMicros,
                fallbackFields = listOf(
                    ap.rssi,
                    ap.frequencyMhz,
                    ap.channelWidthMhz,
                    ap.ssid,
                    ap.bssid,
                ),
            )
        }
    }

    companion object {
        const val DEFAULT_MISSING_GRACE_MILLIS = 10_000L
    }
}

internal class MonitoringSessionPolicy {
    var active: Boolean = false
        private set

    fun start(): Boolean {
        if (active) return false
        active = true
        return true
    }

    fun stop(): Boolean {
        if (!active) return false
        active = false
        return true
    }
}
