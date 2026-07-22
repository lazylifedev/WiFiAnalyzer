package com.lazyapps.wifianalyzer.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.data.WifiScanRepository
import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.ChannelOccupancy
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.SignalSample
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ScanUiState(
    val scanState: ScanState = ScanState.PERMISSION_REQUIRED,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val lastUpdatedMillis: Long? = null,
    val selectedBssid: String? = null,
    val selectedAccessPoint: WifiAccessPoint? = null,
    val selectedDetected: Boolean = false,
    val signalHistory: List<SignalSample> = emptyList(),
    val errorMessage: String? = null,
    val refreshProgress: Float = 0f,
    val refreshSecondsRemaining: Int? = null,
    val isRefreshing: Boolean = false,
) {
    fun accessPointsFor(band: WifiBand): List<WifiAccessPoint> = accessPoints.filter { it.band == band }
    fun occupancyFor(band: WifiBand): List<ChannelOccupancy> = WifiAnalysis.channelOccupancy(accessPoints, band)
}

class WifiScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WifiScanRepository(application)
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
    private val samplesByBssid = mutableMapOf<String, ArrayDeque<SignalSample>>()
    private var permissionState = ScanState.PERMISSION_REQUIRED
    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.snapshot.collectLatest { snapshot ->
                if (permissionState != ScanState.READY) {
                    _uiState.value = _uiState.value.copy(scanState = permissionState, selectedDetected = false)
                    return@collectLatest
                }
                if (snapshot.state == ScanState.PERMISSION_REQUIRED) {
                    _uiState.value = _uiState.value.copy(scanState = permissionState, selectedDetected = false)
                    return@collectLatest
                }
                if (snapshot.state in NON_DETECTING_STATES) {
                    _uiState.value = _uiState.value.copy(
                        scanState = snapshot.state,
                        selectedDetected = false,
                        errorMessage = snapshot.message,
                    )
                    return@collectLatest
                }
                val now = System.currentTimeMillis()
                val withDistances = snapshot.accessPoints.map { ap ->
                    val queue = samplesByBssid.getOrPut(ap.bssid) { ArrayDeque() }
                    if (queue.lastOrNull()?.timestampMillis != ap.observedAtMillis) queue.addLast(SignalSample(ap.observedAtMillis, ap.rssi))
                    while (queue.size > MAX_HISTORY_SAMPLES || queue.firstOrNull()?.timestampMillis?.let { now - it > HISTORY_WINDOW_MS } == true) {
                        queue.removeFirst()
                    }
                    ap.copy(distanceRange = WifiAnalysis.distanceRange(queue.map { it.rssi }, ap.band))
                }
                samplesByBssid.values.forEach { queue ->
                    while (queue.firstOrNull()?.timestampMillis?.let { now - it > HISTORY_WINDOW_MS } == true) queue.removeFirst()
                }
                val selectedBssid = _uiState.value.selectedBssid
                val selected = withDistances.firstOrNull { it.bssid == selectedBssid && now - it.observedAtMillis <= DETECTION_TIMEOUT_MS }
                val previousSelected = _uiState.value.selectedAccessPoint
                    ?.takeIf { now - it.observedAtMillis <= DETECTION_TIMEOUT_MS }
                _uiState.value = _uiState.value.copy(
                    scanState = snapshot.state,
                    accessPoints = withDistances,
                    lastUpdatedMillis = snapshot.updatedAtMillis ?: _uiState.value.lastUpdatedMillis,
                    selectedAccessPoint = selected ?: previousSelected,
                    selectedDetected = selected != null,
                    signalHistory = selectedBssid?.let { samplesByBssid[it]?.toList() }.orEmpty(),
                    errorMessage = snapshot.message,
                    isRefreshing = snapshot.state == ScanState.SCANNING,
                )
            }
        }
    }

    fun updatePermissionState(state: ScanState) {
        require(state in PERMISSION_STATES)
        permissionState = state
        if (state == ScanState.READY) {
            repository.refreshEnvironment()
            if (autoRefreshJob?.isActive != true) {
                scheduleAutoRefresh()
            }
        } else {
            autoRefreshJob?.cancel()
            autoRefreshJob = null
            _uiState.value = _uiState.value.copy(scanState = state, selectedDetected = false)
        }
    }

    fun refresh() {
        scheduleAutoRefresh()
        requestRefresh()
    }

    private fun requestRefresh() {
        if (permissionState == ScanState.READY) {
            if (_uiState.value.scanState == ScanState.SCANNING) return
            repository.requestScan()
        }
        else _uiState.value = _uiState.value.copy(scanState = permissionState)
    }

    private fun scheduleAutoRefresh() {
        if (permissionState != ScanState.READY) return
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val startedAt = System.currentTimeMillis()
                while (System.currentTimeMillis() - startedAt < REFRESH_CYCLE_MS) {
                    val elapsed = System.currentTimeMillis() - startedAt
                    val remaining = (REFRESH_CYCLE_MS - elapsed).coerceAtLeast(0L)
                    _uiState.value = _uiState.value.copy(
                        refreshProgress = (elapsed.toFloat() / REFRESH_CYCLE_MS).coerceIn(0f, 1f),
                        refreshSecondsRemaining = ((remaining + 999L) / 1_000L).toInt(),
                    )
                    delay(250L)
                }
                requestRefresh()
            }
        }
    }

    fun selectAccessPoint(bssid: String) {
        val normalized = bssid.uppercase()
        val selected = _uiState.value.accessPoints.firstOrNull { it.bssid == normalized }
        val detected = selected?.let { System.currentTimeMillis() - it.observedAtMillis <= DETECTION_TIMEOUT_MS } == true
        _uiState.value = _uiState.value.copy(
            selectedBssid = normalized,
            selectedAccessPoint = selected,
            selectedDetected = detected,
            signalHistory = samplesByBssid[normalized]?.toList().orEmpty(),
        )
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    companion object {
        const val HISTORY_WINDOW_MS = 15 * 60_000L
        const val DETECTION_TIMEOUT_MS = 45_000L
        const val MAX_HISTORY_SAMPLES = 900
        const val REFRESH_CYCLE_MS = 18_000L
        val PERMISSION_STATES = setOf(
            ScanState.PERMISSION_REQUIRED,
            ScanState.PERMISSION_DENIED,
            ScanState.PERMISSION_PERMANENTLY_DENIED,
            ScanState.READY,
        )
        val NON_DETECTING_STATES = setOf(
            ScanState.LOCATION_DISABLED,
            ScanState.WIFI_DISABLED,
            ScanState.ERROR,
        )
    }
}
