package com.lazyapps.wifianalyzer.ui.scan

import android.app.Application
import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.BuildConfig
import com.lazyapps.wifianalyzer.data.WifiScanRepository
import com.lazyapps.wifianalyzer.data.WifiUiPreferencesRepository
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.ChannelOccupancy
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.SignalSample
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorCategory
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
    val homeBand: WifiBand = WifiBand.BAND_24,
    val channelBand: WifiBand = WifiBand.BAND_24,
    val refreshIntervalMillis: Long = WifiUiPreferencesRepository.DEFAULT_REFRESH_INTERVAL_MILLIS,
    val distanceUnit: DistanceUnitPreference = DistanceUnitPreference.METERS,
    val visibleBands: Set<WifiBand> = WifiBand.entries.toSet(),
    val signalHistoryRangeMillis: Long = 30_000L,
) {
    fun accessPointsFor(band: WifiBand): List<WifiAccessPoint> = accessPoints.filter { it.band == band }
    fun occupancyFor(band: WifiBand): List<ChannelOccupancy> = WifiAnalysis.channelOccupancy(accessPoints, band)
}

class WifiScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WifiScanRepository(application)
    private val preferences = WifiUiPreferencesRepository(application)
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
    private val samplesByBssid = mutableMapOf<String, ArrayDeque<SignalSample>>()
    private var permissionState = ScanState.PERMISSION_REQUIRED
    private var autoRefreshJob: Job? = null
    private var foreground = true
    private val scanSchedule = ScanRequestSchedule { SystemClock.elapsedRealtime() }

    init {
        viewModelScope.launch {
            preferences.preferences.collectLatest { value ->
                val intervalChanged = _uiState.value.refreshIntervalMillis != value.refreshIntervalMillis
                _uiState.value = _uiState.value.copy(
                    homeBand = value.homeBand,
                    channelBand = value.channelBand,
                    refreshIntervalMillis = value.refreshIntervalMillis,
                    distanceUnit = value.distanceUnit,
                    visibleBands = value.visibleBands,
                )
                if (intervalChanged) scheduleAutoRefresh()
            }
        }
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
                        errorMessage = scanErrorMessage(snapshot.state),
                    )
                    return@collectLatest
                }
                val now = System.currentTimeMillis()
                var addedHistoryCount = 0
                val withDistances = snapshot.accessPoints.map { ap ->
                    val queue = samplesByBssid.getOrPut(ap.bssid) { ArrayDeque() }
                    if (ap.bssid in snapshot.newMeasurementBssids) {
                        queue.addLast(SignalSample(ap.observedAtMillis, ap.rssi))
                        addedHistoryCount++
                    }
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
                    errorMessage = if (snapshot.state == ScanState.THROTTLED) scanErrorMessage(snapshot.state) else null,
                    isRefreshing = snapshot.state == ScanState.SCANNING,
                )
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "scan_history added=$addedHistoryCount uiUpdate=true " +
                            "newMeasurementBssids=${snapshot.newMeasurementBssids.size}",
                    )
                }
            }
        }
    }

    fun updatePermissionState(state: ScanState) {
        require(state in PERMISSION_STATES)
        permissionState = state
        if (state == ScanState.READY) {
            if (foreground) repository.startMonitoring()
            if (autoRefreshJob?.isActive != true) {
                scheduleAutoRefresh()
            }
        } else {
            repository.stopMonitoring()
            scanSchedule.stop()
            autoRefreshJob?.cancel()
            autoRefreshJob = null
            _uiState.value = _uiState.value.copy(scanState = state, selectedDetected = false)
        }
    }

    fun refresh() {
        scheduleAutoRefresh()
        requestRefresh()
    }

    fun selectHomeBand(band: WifiBand) {
        _uiState.value = _uiState.value.copy(homeBand = band)
        viewModelScope.launch { preferences.setHomeBand(band) }
    }

    fun selectChannelBand(band: WifiBand) {
        _uiState.value = _uiState.value.copy(channelBand = band)
        viewModelScope.launch { preferences.setChannelBand(band) }
    }

    fun setRefreshInterval(milliseconds: Long) {
        viewModelScope.launch { preferences.setRefreshInterval(milliseconds) }
    }
    fun setDistanceUnit(unit: DistanceUnitPreference) {
        _uiState.value = _uiState.value.copy(distanceUnit = unit)
        viewModelScope.launch { preferences.setDistanceUnit(unit) }
    }
    fun setVisibleBands(bands: Set<WifiBand>) {
        if (bands.isEmpty()) return
        val first = WifiBand.entries.first { it in bands }
        _uiState.value = _uiState.value.copy(
            visibleBands = bands,
            homeBand = _uiState.value.homeBand.takeIf { it in bands } ?: first,
            channelBand = _uiState.value.channelBand.takeIf { it in bands } ?: first,
        )
        viewModelScope.launch { preferences.setVisibleBands(bands) }
    }
    fun setSignalHistoryRange(milliseconds: Long) {
        require(milliseconds in setOf(30_000L, 60_000L, 300_000L, 900_000L))
        _uiState.value = _uiState.value.copy(signalHistoryRangeMillis = milliseconds)
    }

    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        if (isForeground) {
            if (permissionState == ScanState.READY) repository.startMonitoring()
            scheduleAutoRefresh()
        } else {
            repository.stopMonitoring()
            scanSchedule.stop()
            autoRefreshJob?.cancel()
            autoRefreshJob = null
        }
    }

    private fun requestRefresh() {
        if (permissionState == ScanState.READY) {
            repository.requestScan()
        }
        else _uiState.value = _uiState.value.copy(scanState = permissionState)
    }

    private fun scheduleAutoRefresh() {
        if (permissionState != ScanState.READY || !foreground) return
        val first = scanSchedule.restart(_uiState.value.refreshIntervalMillis)
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            var scheduled = first
            while (true) {
                while (scheduled.delayMillis > 0L) {
                    val waitMillis = minOf(250L, scheduled.delayMillis)
                    delay(waitMillis)
                    val current = scanSchedule.current(scheduled.generation) ?: return@launch
                    val cycleMillis = _uiState.value.refreshIntervalMillis
                    val remaining = current.delayMillis
                    _uiState.value = _uiState.value.copy(
                        refreshProgress = (1f - remaining.toFloat() / cycleMillis).coerceIn(0f, 1f),
                        refreshSecondsRemaining = ((remaining + 999L) / 1_000L).toInt(),
                    )
                    scheduled = current
                }
                if (scanSchedule.current(scheduled.generation) == null) return@launch
                if (permissionState == ScanState.READY && foreground) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "scan_due scheduledElapsedMs=${scheduled.scheduledAtMillis} " +
                                "actualElapsedMs=${SystemClock.elapsedRealtime()}",
                        )
                    }
                    repository.requestScan(scheduled.scheduledAtMillis)
                }
                scheduled = scanSchedule.advance(scheduled.generation) ?: return@launch
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
        scanSchedule.stop()
        repository.close()
        super.onCleared()
    }

    private fun scanErrorMessage(state: ScanState): String? {
        val category = when (state) {
            ScanState.PERMISSION_REQUIRED, ScanState.PERMISSION_DENIED -> OperationErrorCategory.PERMISSION_DENIED
            ScanState.PERMISSION_PERMANENTLY_DENIED -> OperationErrorCategory.PERMISSION_PERMANENTLY_DENIED
            ScanState.LOCATION_DISABLED -> OperationErrorCategory.LOCATION_SERVICE_DISABLED
            ScanState.WIFI_DISABLED -> OperationErrorCategory.WIFI_DISABLED
            ScanState.THROTTLED -> OperationErrorCategory.SCAN_THROTTLED
            ScanState.ERROR -> OperationErrorCategory.NETWORK_SCAN_FAILED
            else -> return null
        }
        return getApplication<Application>().getString(category.messageRes)
    }

    companion object {
        private const val TAG = "WifiCacheMonitor"
        const val HISTORY_WINDOW_MS = 15 * 60_000L
        const val DETECTION_TIMEOUT_MS = 45_000L
        const val MAX_HISTORY_SAMPLES = 900
        const val REFRESH_CYCLE_MS = WifiUiPreferencesRepository.DEFAULT_REFRESH_INTERVAL_MILLIS
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
