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
import java.util.concurrent.atomic.AtomicBoolean

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
    private val elapsedRealtime = object : ElapsedRealtimeSource {
        override fun now(): Long = SystemClock.elapsedRealtime()
    }
    private val resultsSource = object : WifiScanResultsSource {
        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun read(readAtMillis: Long): List<WifiAccessPoint> {
            val readAtElapsedMillis = elapsedRealtime.now()
            return WifiAnalysis.deduplicateByBssid(
                wifiManager.scanResults.mapNotNull {
                    it.toAccessPoint(readAtMillis, readAtElapsedMillis)
                },
            )
        }
    }
    private val observationPolicy = WifiScanObservationPolicy()
    private val monitoringSession = MonitoringSessionPolicy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private var pollingJob: Job? = null
    private var receiverRegistered = false
    private val scanRequestInFlight = AtomicBoolean(false)
    private var lastScanRequestElapsedMillis: Long? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    debugLog(
                        "scan_broadcast wallMs=${System.currentTimeMillis()} " +
                            "elapsedMs=${elapsedRealtime.now()} resultsUpdated=$fresh",
                    )
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
    fun requestScan(scheduledAtElapsedMillis: Long? = null) {
        val actualElapsedMillis = elapsedRealtime.now()
        val sincePreviousMillis = lastScanRequestElapsedMillis?.let { actualElapsedMillis - it }
        if (!scanRequestInFlight.compareAndSet(false, true)) {
            debugLog(
                "scan_request scheduledElapsedMs=$scheduledAtElapsedMillis " +
                    "actualElapsedMs=$actualElapsedMillis sincePreviousMs=$sincePreviousMillis " +
                    "accepted=none exception=none skippedInFlight=true",
            )
            return
        }
        lastScanRequestElapsedMillis = actualElapsedMillis
        try {
            if (!wifiManager.isWifiEnabled) {
                publishState(ScanState.WIFI_DISABLED)
                return
            }
            if (!locationManager.isLocationEnabled) {
                publishState(ScanState.LOCATION_DISABLED)
                return
            }
            val accepted = wifiManager.startScan()
            debugLog(
                "scan_request scheduledElapsedMs=$scheduledAtElapsedMillis " +
                    "actualElapsedMs=$actualElapsedMillis wallMs=${System.currentTimeMillis()} " +
                    "sincePreviousMs=$sincePreviousMillis accepted=$accepted " +
                    "exception=none skippedInFlight=false",
            )
            if (accepted) {
                publishState(ScanState.SCANNING)
            } else if (_snapshot.value.accessPoints.isEmpty()) {
                publishState(ScanState.THROTTLED)
            }
        } catch (_: SecurityException) {
            debugLog(
                "scan_request scheduledElapsedMs=$scheduledAtElapsedMillis " +
                    "actualElapsedMs=$actualElapsedMillis wallMs=${System.currentTimeMillis()} " +
                    "sincePreviousMs=$sincePreviousMillis accepted=none " +
                    "exception=SecurityException skippedInFlight=false",
            )
            publishState(ScanState.PERMISSION_REQUIRED)
        } catch (error: RuntimeException) {
            debugLog(
                "scan_request scheduledElapsedMs=$scheduledAtElapsedMillis " +
                    "actualElapsedMs=$actualElapsedMillis wallMs=${System.currentTimeMillis()} " +
                    "sincePreviousMs=$sincePreviousMillis accepted=none " +
                    "exception=${error.javaClass.simpleName} skippedInFlight=false",
            )
            publishState(ScanState.ERROR, "SCN-002")
        } finally {
            scanRequestInFlight.set(false)
        }
    }

    private fun readResults(preferredState: ScanState, trigger: String) {
        try {
            val readings = resultsSource.read(System.currentTimeMillis())
            val decision = observationPolicy.accept(readings, elapsedRealtime.now())
            val current = _snapshot.value
            val dataChanged = decision.uiChanged || decision.newMeasurementBssids.isNotEmpty()
            val nextState = resolveScanResultState(
                currentState = current.state,
                preferredState = preferredState,
                isEmpty = decision.accessPoints.isEmpty(),
                dataChanged = dataChanged,
            )
            val next = ScanSnapshot(
                state = nextState,
                accessPoints = decision.accessPoints,
                updatedAtMillis = decision.accessPoints.maxOfOrNull { it.observedAtMillis }
                    ?: current.updatedAtMillis,
                newMeasurementBssids = decision.newMeasurementBssids,
            )
            val willNotify = decision.uiChanged || current.state != next.state ||
                decision.newMeasurementBssids.isNotEmpty()
            debugLog(
                "scan_results trigger=$trigger wallMs=${System.currentTimeMillis()} " +
                    "elapsedMs=${elapsedRealtime.now()} count=${readings.size} " +
                    "timestampChanges=${decision.timestampChangedBssids.size} " +
                    "rssiChanges=${decision.rssiChangedBssids.size} " +
                    "historyCandidates=${decision.newMeasurementBssids.size} " +
                    "sameCacheSuppressed=${decision.ignoredSameTimestampCount} " +
                    "rollbacksSuppressed=${decision.ignoredRollbackCount} uiNotified=$willNotify",
            )
            if (BuildConfig.DEBUG) {
                readings.forEach {
                    Log.d(
                        TAG,
                        "scan_result bssid=${it.bssid} rssi=${it.rssi} " +
                            "timestampUs=${it.timestampMicros} " +
                            "timestampChanged=${it.bssid in decision.timestampChangedBssids} " +
                            "rssiChanged=${it.bssid in decision.rssiChangedBssids}",
                    )
                }
            }
            if (willNotify) _snapshot.value = next
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

    private fun ScanResult.toAccessPoint(
        readAtWallClockMillis: Long,
        readAtElapsedMillis: Long,
    ): WifiAccessPoint? {
        val band = WifiAnalysis.bandFromFrequency(frequency) ?: return null
        val width = WifiAnalysis.channelWidthMhzFromScanResult(channelWidth)
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
            observedAtMillis = observedWallClockMillis(
                readAtWallClockMillis = readAtWallClockMillis,
                readAtElapsedMillis = readAtElapsedMillis,
                scanTimestampMicros = timestamp,
            ),
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

internal fun resolveScanResultState(
    currentState: ScanState,
    preferredState: ScanState,
    isEmpty: Boolean,
    dataChanged: Boolean,
): ScanState = when {
    isEmpty -> ScanState.EMPTY
    preferredState == ScanState.THROTTLED -> ScanState.READY
    dataChanged -> preferredState
    currentState == ScanState.READY ||
        currentState == ScanState.SCANNING -> currentState
    currentState == ScanState.THROTTLED -> ScanState.READY
    else -> preferredState
}

internal fun observedWallClockMillis(
    readAtWallClockMillis: Long,
    readAtElapsedMillis: Long,
    scanTimestampMicros: Long,
): Long {
    if (scanTimestampMicros <= 0L || readAtElapsedMillis < 0L) return readAtWallClockMillis
    val scanElapsedMillis = scanTimestampMicros / 1_000L
    if (scanElapsedMillis !in 0L..readAtElapsedMillis) return readAtWallClockMillis
    val ageMillis = readAtElapsedMillis - scanElapsedMillis
    return try {
        Math.subtractExact(readAtWallClockMillis, ageMillis)
    } catch (_: ArithmeticException) {
        readAtWallClockMillis
    }
}
