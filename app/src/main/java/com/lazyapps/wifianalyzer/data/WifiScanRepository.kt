package com.lazyapps.wifianalyzer.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.lazyapps.wifianalyzer.BuildConfig
import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiStandard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScanSnapshot(
    val state: ScanState = ScanState.PERMISSION_REQUIRED,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val updatedAtMillis: Long? = null,
    val message: String? = null,
    val newMeasurementBssids: Set<String> = emptySet(),
)

internal interface WifiScanResultsSource {
    fun read(readAtMillis: Long): List<WifiAccessPoint>
}

internal interface ElapsedRealtimeSource {
    fun now(): Long
}

class WifiScanRepository(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val bootWallClockMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    private val elapsedRealtime = object : ElapsedRealtimeSource {
        override fun now(): Long = SystemClock.elapsedRealtime()
    }
    private val resultsSource = object : WifiScanResultsSource {
        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun read(readAtMillis: Long): List<WifiAccessPoint> =
            WifiAnalysis.deduplicateByBssid(wifiManager.scanResults.mapNotNull { it.toAccessPoint(readAtMillis) })
    }
    private val observationPolicy = WifiScanObservationPolicy()
    private val monitoringSession = MonitoringSessionPolicy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private var pollingJob: Job? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    debugLog("OS scan-results broadcast received updated=$fresh")
                    readResults(if (fresh) ScanState.READY else ScanState.THROTTLED, "broadcast")
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION, LocationManager.MODE_CHANGED_ACTION ->
                    refreshEnvironment()
            }
        }
    }

    fun startMonitoring() {
        if (!monitoringSession.start()) return
        registerReceiver()
        refreshEnvironment()
        pollingJob = scope.launch {
            while (isActive) {
                delay(CACHE_POLL_INTERVAL_MS)
                if (monitoringSession.active) refreshEnvironment("poll")
            }
        }
    }

    fun stopMonitoring() {
        if (!monitoringSession.stop()) return
        pollingJob?.cancel()
        pollingJob = null
        unregisterReceiver()
        debugLog("cache monitoring stopped")
    }

    fun refreshEnvironment() = refreshEnvironment("environment")

    private fun refreshEnvironment(trigger: String) {
        when {
            !wifiManager.isWifiEnabled -> publishState(ScanState.WIFI_DISABLED)
            !locationManager.isLocationEnabled -> publishState(ScanState.LOCATION_DISABLED)
            else -> readResults(ScanState.READY, trigger)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun requestScan() {
        if (!wifiManager.isWifiEnabled) {
            publishState(ScanState.WIFI_DISABLED)
            return
        }
        if (!locationManager.isLocationEnabled) {
            publishState(ScanState.LOCATION_DISABLED)
            return
        }

        try {
            val requestedAt = System.currentTimeMillis()
            val accepted = wifiManager.startScan()
            debugLog("startScan at=$requestedAt accepted=$accepted")
            if (accepted) publishState(ScanState.SCANNING)
            else publishState(ScanState.THROTTLED)
        } catch (_: SecurityException) {
            debugLog("startScan failed: permission")
            publishState(ScanState.PERMISSION_REQUIRED)
        } catch (error: RuntimeException) {
            debugLog("startScan failed: ${error.javaClass.simpleName}")
            publishState(ScanState.ERROR, "SCN-002")
        }
    }

    private fun readResults(preferredState: ScanState, trigger: String) {
        try {
            val readings = resultsSource.read(System.currentTimeMillis())
            val decision = observationPolicy.accept(readings, elapsedRealtime.now())
            debugLog(
                "getScanResults trigger=$trigger count=${readings.size} " +
                    "adopted=${decision.newMeasurementBssids.size} " +
                    "sameTimestamp=${decision.ignoredSameTimestampCount} ui=${decision.uiChanged}",
            )
            if (BuildConfig.DEBUG) {
                readings.forEach {
                    Log.d(TAG, "result bssid=${it.bssid} rssi=${it.rssi} timestampUs=${it.timestampMicros}")
                }
            }
            val current = _snapshot.value
            val nextState = if (decision.accessPoints.isEmpty()) ScanState.EMPTY else preferredState
            val next = ScanSnapshot(
                state = nextState,
                accessPoints = decision.accessPoints,
                updatedAtMillis = decision.accessPoints.maxOfOrNull { it.observedAtMillis }
                    ?: current.updatedAtMillis,
                newMeasurementBssids = decision.newMeasurementBssids,
            )
            if (decision.uiChanged || current.state != next.state ||
                decision.newMeasurementBssids.isNotEmpty()
            ) {
                _snapshot.value = next
            }
        } catch (_: SecurityException) {
            publishState(ScanState.PERMISSION_REQUIRED)
        } catch (error: RuntimeException) {
            debugLog("getScanResults failed: ${error.javaClass.simpleName}")
            publishState(ScanState.ERROR, "SCN-002")
        }
    }

    private fun publishState(state: ScanState, message: String? = null) {
        val current = _snapshot.value
        val next = current.copy(state = state, message = message, newMeasurementBssids = emptySet())
        if (next != current) _snapshot.value = next
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
        debugLog("cache monitoring started intervalMs=$CACHE_POLL_INTERVAL_MS")
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        appContext.unregisterReceiver(receiver)
        receiverRegistered = false
    }

    override fun close() {
        stopMonitoring()
        scope.cancel()
    }

    private fun ScanResult.toAccessPoint(readAt: Long): WifiAccessPoint? {
        val band = WifiAnalysis.bandFromFrequency(frequency) ?: return null
        val width = when (channelWidth) {
            ScanResult.CHANNEL_WIDTH_40MHZ -> 40
            ScanResult.CHANNEL_WIDTH_80MHZ -> 80
            ScanResult.CHANNEL_WIDTH_160MHZ -> 160
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
            if (Build.VERSION.SDK_INT >= 33) ScanResult.CHANNEL_WIDTH_320MHZ else -1 -> 320
            else -> 20
        }
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (wifiStandard) {
                ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
                ScanResult.WIFI_STANDARD_11N -> WifiStandard.WIFI_4
                ScanResult.WIFI_STANDARD_11AC -> WifiStandard.WIFI_5
                ScanResult.WIFI_STANDARD_11AX ->
                    if (band == com.lazyapps.wifianalyzer.model.WifiBand.BAND_6) WifiStandard.WIFI_6E else WifiStandard.WIFI_6
                ScanResult.WIFI_STANDARD_11BE -> WifiStandard.WIFI_7
                else -> WifiStandard.UNKNOWN
            }
        } else WifiStandard.UNKNOWN
        return WifiAccessPoint(
            ssid = WifiAnalysis.displaySsid(SSID),
            bssid = BSSID?.uppercase() ?: return null,
            rssi = level,
            frequencyMhz = frequency,
            channel = WifiAnalysis.channelFromFrequency(frequency),
            channelWidthMhz = width,
            capabilities = capabilities.orEmpty(),
            timestampMicros = timestamp,
            band = band,
            signalQuality = WifiAnalysis.signalQuality(level),
            securityType = WifiAnalysis.securityType(capabilities.orEmpty()),
            wifiStandard = standard,
            distanceRange = DistanceRange.TWENTY_PLUS,
            observedAtMillis = if (timestamp > 0L) {
                bootWallClockMillis + timestamp / 1_000L
            } else {
                readAt
            },
        )
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    companion object {
        const val CACHE_POLL_INTERVAL_MS = 2_000L
        private const val TAG = "WifiCacheMonitor"
    }
}
