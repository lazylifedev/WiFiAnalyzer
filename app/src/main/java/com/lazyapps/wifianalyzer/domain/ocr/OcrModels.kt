package com.lazyapps.wifianalyzer.domain.ocr

import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.domain.BssidFormat
import com.lazyapps.wifianalyzer.domain.DeviceBssidInput
import com.lazyapps.wifianalyzer.domain.DeviceInput

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

enum class CandidateKind {
    MANUFACTURER, MODEL, SERIAL, SSID, BSSID, LAN_MAC, WAN_MAC,
    MANAGEMENT_IP, USERNAME, PASSWORD
}

data class OcrTextElement(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class OcrTextLine(
    val text: String,
    val elements: List<OcrTextElement> = emptyList(),
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

data class OcrDocument(val text: String, val lines: List<OcrTextLine>)

data class NearbyWifiMatch(
    val accessPoint: WifiAccessPoint,
    val correctedValue: String? = null,
    val reason: String,
)

data class ParsedFieldCandidate(
    val value: String,
    val kind: CandidateKind,
    val sourceLabel: String,
    val confidence: ConfidenceLevel,
    val band: WifiBand? = null,
    val selected: Boolean = true,
    val originalValue: String = value,
    val correctionCandidate: String? = null,
    val nearbyMatch: NearbyWifiMatch? = null,
) {
    val isSensitive: Boolean get() = kind == CandidateKind.PASSWORD || kind == CandidateKind.USERNAME || kind == CandidateKind.MANAGEMENT_IP
}

data class ParsedDeviceLabel(
    val manufacturerCandidates: List<ParsedFieldCandidate> = emptyList(),
    val modelCandidates: List<ParsedFieldCandidate> = emptyList(),
    val serialCandidates: List<ParsedFieldCandidate> = emptyList(),
    val ssidCandidates: List<ParsedFieldCandidate> = emptyList(),
    val macCandidates: List<ParsedFieldCandidate> = emptyList(),
    val managementCandidates: List<ParsedFieldCandidate> = emptyList(),
    val rawText: String = "",
) {
    val allCandidates: List<ParsedFieldCandidate> get() = manufacturerCandidates + modelCandidates + serialCandidates + ssidCandidates + macCandidates + managementCandidates
    val hasUsefulFields: Boolean get() = allCandidates.isNotEmpty()
}

object SensitiveValueMasker {
    fun mask(value: String): String = when {
        value.isEmpty() -> ""
        value.length <= 2 -> "•".repeat(value.length)
        else -> value.take(1) + "•".repeat((value.length - 2).coerceAtMost(12)) + value.takeLast(1)
    }
}

object OcrRegistrationDraftFactory {
    fun create(result: ParsedDeviceLabel): DeviceInput {
        fun first(kind: CandidateKind) = result.allCandidates.firstOrNull { it.kind == kind && it.selected }?.value.orEmpty()
        val manufacturer = first(CandidateKind.MANUFACTURER)
        val model = first(CandidateKind.MODEL)
        val ssid = first(CandidateKind.SSID)
        val bssids = result.macCandidates.filter { it.kind == CandidateKind.BSSID && it.selected }.mapNotNull { candidate ->
            BssidFormat.normalize(candidate.value)?.let { normalized ->
                DeviceBssidInput(normalized, candidate.band?.label ?: candidate.nearbyMatch?.accessPoint?.band?.label ?: "2.4 GHz", candidate.sourceLabel)
            }
        }.distinctBy { it.bssid }
        val displayName = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { ssid.ifBlank { model } }
        return DeviceInput(
            displayName = displayName,
            manufacturer = manufacturer,
            model = model,
            serialNumber = first(CandidateKind.SERIAL),
            ssid = ssid,
            bssids = bssids.ifEmpty { listOf(DeviceBssidInput("", "2.4 GHz")) },
        )
    }
}
