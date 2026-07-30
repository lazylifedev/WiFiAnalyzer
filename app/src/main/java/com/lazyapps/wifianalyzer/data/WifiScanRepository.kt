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
import com.lazyapps.wifianalyzer.debug.DebugLogCategory
import com.lazyapps.wifianalyzer.debug.DebugLogs
import com.lazyapps.wifianalyzer.debug.DebugUpdateSource
import com.lazyapps.wifianalyzer.debug.debugUpdateSource
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
    private var lastNewMeasurementElapsedMillis: Long? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    val receivedAt = elapsedRealtime.now()
                    if (BuildConfig.DEBUG) DebugLogs.store.add(
                        DebugLogCategory.BROADCAST,
                        "resultsUpdated=$fresh resultsRead=true requestDeltaMs=" +
                            lastScanRequestElapsedMillis?.let { receivedAt - it },
                    )
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
        if (BuildConfig.DEBUG) DebugLogs.store.add(
            DebugLogCategory.LIFECYCLE,
            "foreground=true receiverRegistered=$receiverRegistered cacheJob=true monitoringStopped=false",
        )
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
        if (BuildConfig.DEBUG) DebugLogs.store.add(
            DebugLogCategory.LIFECYCLE,
            "foreground=false receiverRegistered=$receiverRegistered cacheJob=false monitoringStopped=true",
        )
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
    fun requestScan(
        scheduledAtElapsedMillis: Long? = null,
        intervalMillis: Long? = null,
        trigger: String = "manual",
    ) {
        val actualElapsedMillis = elapsedRealtime.now()
        val sincePreviousMillis = lastScanRequestElapsedMillis?.let { actualElapsedMillis - it }
        if (!scanRequestInFlight.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) DebugLogs.store.add(
                DebugLogCategory.SCAN_REQUEST,
                "trigger=$trigger accepted=none interval=${intervalMillis}ms sincePreviousMs=$sincePreviousMillis " +
                    "skippedInFlight=true exception=none",
            )
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
            if (BuildConfig.DEBUG) DebugLogs.store.add(
                DebugLogCategory.SCAN_REQUEST,
                "trigger=$trigger accepted=$accepted interval=${intervalMillis}ms " +
                    "sincePreviousMs=$sincePreviousMillis skippedInFlight=false exception=none",
            )
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
            if (BuildConfig.DEBUG) DebugLogs.store.add(
                DebugLogCategory.ERROR,
                "trigger=$trigger scanRequest exception=SecurityException",
            )
            debugLog(
                "scan_request scheduledElapsedMs=$scheduledAtElapsedMillis " +
                    "actualElapsedMs=$actualElapsedMillis wallMs=${System.currentTimeMillis()} " +
                    "sincePreviousMs=$sincePreviousMillis accepted=none " +
                    "exception=SecurityException skippedInFlight=false",
            )
            publishState(ScanState.PERMISSION_REQUIRED)
        } catch (error: RuntimeException) {
            if (BuildConfig.DEBUG) DebugLogs.store.add(
                DebugLogCategory.ERROR,
                "trigger=$trigger scanRequest exception=${error.javaClass.simpleName}",
            )
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
            if (BuildConfig.DEBUG) {
                val source = debugUpdateSource(
                    hasNewMeasurements = decision.newMeasurementBssids.isNotEmpty(),
                    hasTimestampChanges = decision.timestampChangedBssids.isNotEmpty(),
                    uiNotified = willNotify,
                )
                val nowElapsed = elapsedRealtime.now()
                val sinceNewMeasurement = lastNewMeasurementElapsedMillis?.let { nowElapsed - it }
                if (source == DebugUpdateSource.NEW_SCAN_RESULT) lastNewMeasurementElapsedMillis = nowElapsed
                val commonDetail = "trigger=$trigger readAp=${readings.size} adoptedAp=${decision.accessPoints.size} " +
                    "timestampChanges=${decision.timestampChangedBssids.size} rssiChanges=${decision.rssiChangedBssids.size} " +
                    "newMeasurements=${decision.newMeasurementBssids.size} sameTimestampSuppressed=" +
                    "${decision.ignoredSameTimestampCount} rollbackSuppressed=${decision.ignoredRollbackCount} " +
                    "uiNotified=$willNotify state=${next.state} sinceNewMeasurementMs=$sinceNewMeasurement " +
                    "foreground=${monitoringSession.active} receiverRegistered=$receiverRegistered " +
                    "cacheJob=${pollingJob?.isActive == true}"
                if (trigger == "poll" && willNotify) {
                    DebugLogs.store.add(
                        DebugLogCategory.CACHE_POLL,
                        "cache read count=${readings.size} uiUpdated=true stateChanged=${current.state != next.state}",
                    )
                }
                if (current.state != next.state) {
                    DebugLogs.store.add(DebugLogCategory.STATE, "${current.state} -> ${next.state}")
                }
                when (source) {
                    DebugUpdateSource.NEW_SCAN_RESULT -> DebugLogs.store.add(
                        DebugLogCategory.UI_UPDATE_NEW_SCAN, commonDetail, source,
                    )
                    DebugUpdateSource.OS_CACHE_UI_UPDATED -> DebugLogs.store.add(
                        DebugLogCategory.UI_UPDATE_CACHE, commonDetail, source,
                    )
                    DebugUpdateSource.OS_CACHE_NO_CHANGE -> DebugLogs.store.add(
                        DebugLogCategory.CACHE_NO_CHANGE,
                        "trigger=$trigger readAp=${readings.size} adoptedAp=${decision.accessPoints.size} " +
                            "sameTimestampSuppressed=${decision.ignoredSameTimestampCount} uiNotified=false " +
                            "state=${next.state}",
                        source,
                        aggregate = true,
                    )
                }
            }
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
        if (next != current) {
            if (BuildConfig.DEBUG) {
                DebugLogs.store.add(DebugLogCategory.STATE, "${current.state} -> $state")
            }
            _snapshot.value = next
        }
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
