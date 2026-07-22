package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.domain.DeviceBssidInput
import com.lazyapps.wifianalyzer.domain.DeviceInput
import com.lazyapps.wifianalyzer.domain.SignalHistoryPolicy
import com.lazyapps.wifianalyzer.domain.ocr.OcrDeviceUpdateMerger
import com.lazyapps.wifianalyzer.domain.ocr.OcrUpdateMode
import com.lazyapps.wifianalyzer.model.SignalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5AUnitTest {
    @Test fun historyIsBoundedDeduplicatedAndSplitAtMissingPeriods() {
        val now = 1_000_000L
        val samples = listOf(
            SignalSample(now - 1_000_000L, -90),
            SignalSample(now - 80_000L, -60),
            SignalSample(now - 80_000L, -61),
            SignalSample(now - 10_000L, -50),
        )
        val kept = SignalHistoryPolicy.trim(samples, now)
        assertEquals(2, kept.size)
        assertEquals(2, SignalHistoryPolicy.segments(kept).size)
        assertEquals(-50, SignalHistoryPolicy.statistics(kept)?.latest)
    }

    @Test fun ocrFillBlanksPreservesValuesAndOnlyAddsUniqueBssids() {
        val current = DeviceInput(7, "Router", model = "M1", bssids = listOf(DeviceBssidInput("AA:BB:CC:DD:EE:FF", "5 GHz")))
        val recognized = DeviceInput(displayName = "OCR Router", manufacturer = "Maker", model = "M2", bssids = listOf(
            DeviceBssidInput("aa-bb-cc-dd-ee-ff", "5 GHz"), DeviceBssidInput("11:22:33:44:55:66", "2.4 GHz")
        ))
        val merged = OcrDeviceUpdateMerger.merge(current, recognized, OcrUpdateMode.FILL_BLANKS)
        assertEquals(7, merged.id)
        assertEquals("Router", merged.displayName)
        assertEquals("M1", merged.model)
        assertEquals("Maker", merged.manufacturer)
        assertEquals(2, merged.bssids.size)
        assertTrue(merged.bssids.any { it.bssid == "11:22:33:44:55:66" })
    }
}
