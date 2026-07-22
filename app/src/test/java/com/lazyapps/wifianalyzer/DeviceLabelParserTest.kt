package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.domain.ocr.CandidateKind
import com.lazyapps.wifianalyzer.domain.ocr.ConfidenceLevel
import com.lazyapps.wifianalyzer.domain.ocr.DeviceLabelParser
import com.lazyapps.wifianalyzer.domain.ocr.OcrDocument
import com.lazyapps.wifianalyzer.domain.ocr.OcrTextLine
import com.lazyapps.wifianalyzer.domain.ocr.SensitiveValueMasker
import com.lazyapps.wifianalyzer.domain.ocr.OcrRegistrationDraftFactory
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLabelParserTest {
    @Test fun extractsSeparatedMacCandidate() {
        val result = parse("BSSID: AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", result.macCandidates.single().value)
    }

    @Test fun extractsCompactMacCandidate() {
        assertEquals("AA:BB:CC:DD:EE:FF", parse("MAC AABBCCDDEEFF").macCandidates.single().value)
    }

    @Test fun extractsMacContainingOcrWhitespace() {
        assertEquals("AA:BB:CC:DD:EE:FF", parse("WLAN MAC AA BB CC DD EE FF").macCandidates.single().value)
    }

    @Test fun extractsModelAndSerial() {
        val result = parse("MODEL NO: WSR-3200AX4S\nS/N: TEST123456")
        assertEquals("WSR-3200AX4S", result.modelCandidates.single().value)
        assertEquals("TEST123456", result.serialCandidates.single().value)
    }

    @Test fun extractsAndClassifiesMultipleSsids() {
        val result = parse("SSID 2.4G: Lab-24\nSSID 5G: Lab-5\nSSID 6G: Lab-6")
        assertEquals(listOf(WifiBand.BAND_24, WifiBand.BAND_5, WifiBand.BAND_6), result.ssidCandidates.map { it.band })
    }

    @Test fun wanMacIsNotSelectedAsBssid() {
        val candidate = parse("WAN MAC: AA:BB:CC:DD:EE:FF").macCandidates.single()
        assertEquals(CandidateKind.WAN_MAC, candidate.kind)
        assertFalse(candidate.selected)
    }

    @Test fun generatesButDoesNotSelectOcrCorrection() {
        val candidate = parse("BSSID: 00:11:22:33:44:5S").macCandidates.single()
        assertEquals("00:11:22:33:44:55", candidate.correctionCandidate)
        assertFalse(candidate.selected)
        assertNull(com.lazyapps.wifianalyzer.domain.BssidFormat.normalize(candidate.value))
    }

    @Test fun exactBssidMatchesNearbyScan() {
        val candidate = parse("BSSID: 00:11:22:33:44:55", listOf(ap("Nearby", "00:11:22:33:44:55"))).macCandidates.single()
        assertNotNull(candidate.nearbyMatch)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence)
    }

    @Test fun correctedBssidIsOnlyPresentedAsCandidate() {
        val candidate = parse("BSSID: 00:11:22:33:44:5S", listOf(ap("Nearby", "00:11:22:33:44:55"))).macCandidates.single()
        assertEquals("00:11:22:33:44:55", candidate.nearbyMatch?.correctedValue)
        assertFalse(candidate.selected)
    }

    @Test fun validHexBAndEightStillProduceNonAutomaticAmbiguityCandidate() {
        val candidate = parse("BSSID: 00:11:22:33:44:5B").macCandidates.single()
        assertEquals("00:11:22:33:44:58", candidate.correctionCandidate)
        assertEquals("00:11:22:33:44:5B", candidate.value)
    }

    @Test fun sameOuiIsReportedAsWeakerNearbyCandidate() {
        val candidate = parse("BSSID: 00:11:22:AA:BB:CC", listOf(ap("Nearby", "00:11:22:10:20:30"))).macCandidates.single()
        assertEquals("メーカーOUI候補", candidate.nearbyMatch?.reason)
        assertEquals(ConfidenceLevel.MEDIUM, candidate.confidence)
    }

    @Test fun normalizedSsidMatchesNearbyScan() {
        val candidate = parse("SSID: Lab Wi-Fi", listOf(ap("lab_wi-fi", "00:11:22:33:44:55"))).ssidCandidates.single()
        assertEquals("SSID正規化一致", candidate.nearbyMatch?.reason)
        assertEquals(ConfidenceLevel.MEDIUM, candidate.confidence)
    }

    @Test fun labelledAndUnlabelledValuesUseUnderstandableConfidenceLevels() {
        val high = parse("BSSID: AA:BB:CC:DD:EE:FF").macCandidates.single()
        val low = parse("AA-BB-CC-DD-EE-FF").macCandidates.single()
        assertEquals(ConfidenceLevel.HIGH, high.confidence)
        assertEquals(ConfidenceLevel.LOW, low.confidence)
    }

    @Test fun detectsKnownManufacturerWithoutForcingUnknown() {
        assertEquals("BUFFALO", parse("BUFFALO INC.\nMODEL: TEST-1").manufacturerCandidates.single().value)
        assertTrue(parse("UNKNOWN LAB\nMODEL: TEST-1").manufacturerCandidates.isEmpty())
    }

    @Test fun extractsManagementSecretSeparately() {
        val result = parse("IP Address: 192.0.2.1\nUsername: test-user\nPassword: TEST-SECRET")
        assertEquals(setOf(CandidateKind.MANAGEMENT_IP, CandidateKind.USERNAME, CandidateKind.PASSWORD), result.managementCandidates.map { it.kind }.toSet())
        assertTrue(result.managementCandidates.all { it.isSensitive })
    }

    @Test fun sensitiveValueIsMasked() {
        val masked = SensitiveValueMasker.mask("TEST-SECRET")
        assertEquals('T', masked.first())
        assertEquals('T', masked.last())
        assertFalse(masked.contains("SECRET"))
    }

    @Test fun registrationDraftIncludesSelectedNormalFieldsButNeverManagementSecrets() {
        val result = parse("BUFFALO\nMODEL: WSR-TEST\nS/N: TEST123\nSSID 5G: Lab-5\nBSSID: AA:BB:CC:DD:EE:FF\nWAN MAC: AA:BB:CC:DD:EE:00\nPassword: DO-NOT-SAVE")
        val draft = OcrRegistrationDraftFactory.create(result)
        assertEquals("BUFFALO WSR-TEST", draft.displayName)
        assertEquals("Lab-5", draft.ssid)
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), draft.bssids.map { it.bssid })
        assertFalse(draft.notes.contains("DO-NOT-SAVE"))
        assertFalse(draft.toString().contains("DO-NOT-SAVE"))
    }

    private fun parse(text: String, nearby: List<WifiAccessPoint> = emptyList()) =
        DeviceLabelParser.parse(OcrDocument(text, text.lines().map { OcrTextLine(it) }), nearby)

    private fun ap(ssid: String, bssid: String) = WifiAccessPoint(
        ssid = ssid,
        bssid = bssid,
        rssi = -48,
        frequencyMhz = 5180,
        channel = 36,
        channelWidthMhz = 80,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        timestampMicros = 1L,
        band = WifiBand.BAND_5,
        signalQuality = SignalQuality.GOOD,
        securityType = SecurityType.WPA2,
        wifiStandard = WifiStandard.WIFI_6,
        distanceRange = DistanceRange.THREE_TO_EIGHT,
        observedAtMillis = 1L,
    )
}
