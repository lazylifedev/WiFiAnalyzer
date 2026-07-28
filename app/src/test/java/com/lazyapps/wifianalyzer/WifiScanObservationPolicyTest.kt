package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.data.MonitoringSessionPolicy
import com.lazyapps.wifianalyzer.data.ScanSnapshot
import com.lazyapps.wifianalyzer.data.WifiScanObservationPolicy
import com.lazyapps.wifianalyzer.data.resolveScanResultState
import com.lazyapps.wifianalyzer.data.observedWallClockMillis
import com.lazyapps.wifianalyzer.domain.WifiAnalysis
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScanObservationPolicyTest {
    @Test
    fun sameBssidAndTimestampDoesNotCreateDuplicateMeasurement() {
        val policy = WifiScanObservationPolicy()
        assertEquals(setOf(BSSID_A), policy.accept(listOf(ap(BSSID_A, -50, 100)), 0).newMeasurementBssids)

        val same = policy.accept(listOf(ap(BSSID_A, -50, 100)), 2_000)

        assertTrue(same.newMeasurementBssids.isEmpty())
        assertEquals(1, same.ignoredSameTimestampCount)
        assertFalse(same.uiChanged)
    }

    @Test
    fun newerTimestampCreatesMeasurement() {
        val policy = WifiScanObservationPolicy()
        policy.accept(listOf(ap(BSSID_A, -50, 100)), 0)

        val newer = policy.accept(listOf(ap(BSSID_A, -51, 200)), 2_000)

        assertEquals(setOf(BSSID_A), newer.newMeasurementBssids)
        assertEquals(setOf(BSSID_A), newer.timestampChangedBssids)
        assertEquals(setOf(BSSID_A), newer.rssiChangedBssids)
        assertTrue(newer.uiChanged)
    }

    @Test
    fun rssiOnlyChangeUpdatesUiButNotHistory() {
        val policy = WifiScanObservationPolicy()
        policy.accept(listOf(ap(BSSID_A, -50, 100)), 0)

        val changed = policy.accept(listOf(ap(BSSID_A, -60, 100)), 2_000)

        assertTrue(changed.uiChanged)
        assertTrue(changed.newMeasurementBssids.isEmpty())
        assertTrue(changed.timestampChangedBssids.isEmpty())
        assertEquals(setOf(BSSID_A), changed.rssiChangedBssids)
        assertEquals(-60, changed.accessPoints.single().rssi)
    }

    @Test
    fun newBssidUpdatesUiAndCreatesMeasurement() {
        val policy = WifiScanObservationPolicy()
        policy.accept(listOf(ap(BSSID_A, -50, 100)), 0)

        val changed = policy.accept(
            listOf(ap(BSSID_A, -50, 100), ap(BSSID_B, -60, 110)),
            2_000,
        )

        assertTrue(changed.uiChanged)
        assertEquals(setOf(BSSID_B), changed.newMeasurementBssids)
    }

    @Test
    fun resultOrderAloneDoesNotUpdateUi() {
        val policy = WifiScanObservationPolicy()
        val first = listOf(ap(BSSID_A, -50, 100), ap(BSSID_B, -60, 110))
        policy.accept(first, 0)

        val reordered = policy.accept(first.reversed(), 2_000)

        assertFalse(reordered.uiChanged)
    }

    @Test
    fun temporaryMissKeepsPreviousDisplayThenExpiresIt() {
        val policy = WifiScanObservationPolicy(missingGraceMillis = 10_000)
        policy.accept(listOf(ap(BSSID_A, -50, 100)), 0)

        val firstMiss = policy.accept(emptyList(), 2_000)
        val withinGrace = policy.accept(emptyList(), 11_999)
        val expired = policy.accept(emptyList(), 12_000)

        assertEquals(1, firstMiss.accessPoints.size)
        assertEquals(1, withinGrace.accessPoints.size)
        assertTrue(expired.accessPoints.isEmpty())
        assertTrue(expired.uiChanged)
    }

    @Test
    fun olderOsCacheDoesNotReplaceNewerMeasurement() {
        val policy = WifiScanObservationPolicy()
        policy.accept(listOf(ap(BSSID_A, -50, 200)), 0)

        val stale = policy.accept(listOf(ap(BSSID_A, -80, 100)), 2_000)

        assertEquals(200, stale.accessPoints.single().timestampMicros)
        assertEquals(-50, stale.accessPoints.single().rssi)
        assertTrue(stale.newMeasurementBssids.isEmpty())
        assertFalse(stale.uiChanged)
    }

    @Test
    fun unavailableTimestampFallsBackToMeasuredFieldsWithoutUiChurn() {
        val policy = WifiScanObservationPolicy()
        policy.accept(listOf(ap(BSSID_A, -50, 0).copy(observedAtMillis = 1_000)), 0)

        val same = policy.accept(
            listOf(ap(BSSID_A, -50, 0).copy(observedAtMillis = 3_000)),
            2_000,
        )
        val rssiChanged = policy.accept(
            listOf(ap(BSSID_A, -51, 0).copy(observedAtMillis = 5_000)),
            4_000,
        )

        assertFalse(same.uiChanged)
        assertTrue(same.newMeasurementBssids.isEmpty())
        assertTrue(rssiChanged.uiChanged)
        assertEquals(setOf(BSSID_A), rssiChanged.newMeasurementBssids)
    }

    @Test
    fun scanFailureStateCanPreservePreviousDisplay() {
        val previous = ScanSnapshot(
            state = ScanState.READY,
            accessPoints = listOf(ap(BSSID_A, -50, 100)),
        )

        val failure = previous.copy(state = ScanState.THROTTLED)

        assertEquals(previous.accessPoints, failure.accessPoints)
    }

    @Test
    fun validCacheNormalizesTransientThrottledState() {
        assertEquals(
            ScanState.READY,
            resolveScanResultState(
                currentState = ScanState.THROTTLED,
                preferredState = ScanState.READY,
                isEmpty = false,
                dataChanged = false,
            ),
        )
        assertEquals(
            ScanState.READY,
            resolveScanResultState(
                currentState = ScanState.THROTTLED,
                preferredState = ScanState.READY,
                isEmpty = false,
                dataChanged = true,
            ),
        )
    }

    @Test
    fun displayTimestampUsesCurrentWallClockOffset() {
        assertEquals(
            1_699_999_995_000L,
            observedWallClockMillis(
                readAtWallClockMillis = 1_700_000_000_000L,
                readAtElapsedMillis = 25_000L,
                scanTimestampMicros = 20_000_000L,
            ),
        )
        assertEquals(
            1_799_999_995_000L,
            observedWallClockMillis(
                readAtWallClockMillis = 1_800_000_000_000L,
                readAtElapsedMillis = 25_000L,
                scanTimestampMicros = 20_000_000L,
            ),
        )
    }

    @Test
    fun invalidOrFutureScanTimestampFallsBackSafely() {
        assertEquals(1_000L, observedWallClockMillis(1_000L, 500L, 0L))
        assertEquals(1_000L, observedWallClockMillis(1_000L, 500L, 600_000L))
        assertEquals(Long.MIN_VALUE, observedWallClockMillis(Long.MIN_VALUE, 500L, 100_000L))
    }

    @Test
    fun monitoringStartAndStopAreIdempotent() {
        val session = MonitoringSessionPolicy()

        assertTrue(session.start())
        assertFalse(session.start())
        assertTrue(session.stop())
        assertFalse(session.stop())
        assertFalse(session.active)
    }

    private fun ap(bssid: String, rssi: Int, timestamp: Long) = WifiAccessPoint(
        ssid = "Test",
        bssid = bssid,
        rssi = rssi,
        frequencyMhz = 2_412,
        channel = 1,
        channelWidthMhz = 20,
        capabilities = "[WPA2]",
        timestampMicros = timestamp,
        band = WifiAnalysis.bandFromFrequency(2_412)!!,
        signalQuality = WifiAnalysis.signalQuality(rssi),
        securityType = SecurityType.WPA2,
        wifiStandard = WifiStandard.WIFI_4,
        distanceRange = DistanceRange.THREE_TO_EIGHT,
        observedAtMillis = timestamp,
    )

    companion object {
        private const val BSSID_A = "AA:BB:CC:DD:EE:01"
        private const val BSSID_B = "AA:BB:CC:DD:EE:02"
    }
}
