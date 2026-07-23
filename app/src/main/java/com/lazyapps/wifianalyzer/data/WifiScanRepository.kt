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
import androidx.core.content.ContextCompat
import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiStandard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScanSnapshot(
    val state: ScanState = ScanState.PERMISSION_REQUIRED,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val updatedAtMillis: Long? = null,
    val message: String? = null,
)

class WifiScanRepository(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private var lastScanRequestElapsed = Long.MIN_VALUE
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    readResults(if (fresh) ScanState.READY else ScanState.THROTTLED)
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION, LocationManager.MODE_CHANGED_ACTION -> refreshEnvironment()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        // These broadcasts originate from system Wi-Fi/location services, including privileged
        // processes outside this app's UID. The receiver only re-reads protected system state.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    fun refreshEnvironment() {
        when {
            !wifiManager.isWifiEnabled -> publishState(ScanState.WIFI_DISABLED)
            !locationManager.isLocationEnabled -> publishState(ScanState.LOCATION_DISABLED)
            else -> readResults(ScanState.READY)
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

        val elapsed = SystemClock.elapsedRealtime()
        if (lastScanRequestElapsed != Long.MIN_VALUE && elapsed - lastScanRequestElapsed < MIN_SCAN_INTERVAL_MS) {
            readResults(ScanState.THROTTLED)
            return
        }

        try {
            lastScanRequestElapsed = elapsed
            val accepted = wifiManager.startScan()
            if (accepted) publishState(ScanState.SCANNING) else readResults(ScanState.THROTTLED)
        } catch (_: SecurityException) {
            publishState(ScanState.PERMISSION_REQUIRED)
        } catch (_: RuntimeException) {
            publishState(ScanState.ERROR, "SCN-002")
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readResults(preferredState: ScanState) {
        try {
            val now = System.currentTimeMillis()
            val mapped = WifiAnalysis.deduplicateByBssid(wifiManager.scanResults.mapNotNull { it.toAccessPoint(now) })
            _snapshot.value = ScanSnapshot(
                state = if (mapped.isEmpty()) ScanState.EMPTY else preferredState,
                accessPoints = mapped,
                updatedAtMillis = mapped.maxOfOrNull { it.observedAtMillis } ?: now,
            )
        } catch (_: SecurityException) {
            publishState(ScanState.PERMISSION_REQUIRED)
        } catch (_: RuntimeException) {
            publishState(ScanState.ERROR, "SCN-002")
        }
    }

    private fun publishState(state: ScanState, message: String? = null) {
        _snapshot.value = _snapshot.value.copy(state = state, message = message)
    }

    override fun close() {
        if (receiverRegistered) {
            appContext.unregisterReceiver(receiver)
            receiverRegistered = false
        }
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
                ScanResult.WIFI_STANDARD_11AX -> if (band == com.lazyapps.wifianalyzer.model.WifiBand.BAND_6) WifiStandard.WIFI_6E else WifiStandard.WIFI_6
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
            observedAtMillis = readAt - (SystemClock.elapsedRealtime() - timestamp / 1_000L).coerceAtLeast(0L),
        )
    }

    companion object {
        const val MIN_SCAN_INTERVAL_MS = 30_000L
    }
}
