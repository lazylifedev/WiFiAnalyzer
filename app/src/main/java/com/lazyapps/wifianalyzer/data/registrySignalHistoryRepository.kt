package com.lazyapps.wifianalyzer.data.registry

import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import kotlinx.coroutines.flow.Flow

class SignalHistoryRepository(private val database: WifiAnalyzerDatabase) {
    companion object { const val MAX_POINTS = 900 }
    private val dao = database.registryDao()
    suspend fun insert(workspaceId: Long, points: List<WifiAccessPoint>, access: FeatureAccessPolicy, now: Long) = database.withTransaction {
        if (points.isNotEmpty()) dao.insertHistory(points.distinctBy { it.bssid to it.observedAtMillis }.map { SignalHistoryEntity(workspaceId = workspaceId, bssid = it.bssid, ssid = it.ssid, timestampMillis = it.observedAtMillis, rssi = it.rssi, frequencyMhz = it.frequencyMhz, channel = it.channel, band = it.band.name) })
        if (!access.isPro) dao.deleteHistoryBefore(now - 24L * 60L * 60L * 1000L)
    }
    fun observe(workspaceId: Long, bssid: String): Flow<List<SignalHistoryEntity>> = dao.observeHistory(workspaceId, bssid)
    fun observeLatest(workspaceId: Long, bssid: String): Flow<List<SignalHistoryEntity>> = dao.observeLatestHistory(workspaceId, bssid, MAX_POINTS)
    suspend fun cleanupFree(now: Long) = dao.deleteHistoryBefore(now - 24L * 60L * 60L * 1000L)
}
