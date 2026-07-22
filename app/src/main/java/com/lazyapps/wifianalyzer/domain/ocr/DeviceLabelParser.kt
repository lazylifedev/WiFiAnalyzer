package com.lazyapps.wifianalyzer.domain.ocr

import com.lazyapps.wifianalyzer.domain.BssidFormat
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import java.text.Normalizer

object DeviceLabelParser {
    private val manufacturers = linkedMapOf(
        "BUFFALO" to "BUFFALO", "ELECOM" to "ELECOM", "I-O DATA" to "I-O DATA",
        "TP-LINK" to "TP-Link", "CISCO" to "Cisco", "ARUBA" to "Aruba",
        "RUCKUS" to "Ruckus", "UBIQUITI" to "Ubiquiti", "YAMAHA" to "YAMAHA",
        "PANASONIC" to "Panasonic", "NETGEAR" to "NETGEAR", "ASUS" to "ASUS",
        "ATERM" to "Aterm", "NEC" to "NEC",
    )
    private val fieldRules = listOf(
        Rule(CandidateKind.MODEL, Regex("^(?:MODEL(?:\\s*(?:NO|NAME))?|型番|型式|品名|PRODUCT\\s*NAME|製品型番)\\s*[:：#-]?\\s*(.+)$", RegexOption.IGNORE_CASE)),
        Rule(CandidateKind.SERIAL, Regex("^(?:S/?N|SN|SERIAL(?:\\s*NO|\\s*NUMBER)?|製造番号)\\s*[:：#-]?\\s*(.+)$", RegexOption.IGNORE_CASE)),
        Rule(CandidateKind.SSID, Regex("^(?:(SSID(?:\\s*(?:2\\.?4|5|6)G(?:HZ)?)?)|NETWORK\\s*NAME|WI-?FI\\s*NAME)\\s*[:：]?\\s*(.+)$", RegexOption.IGNORE_CASE), valueGroup = 2),
        Rule(CandidateKind.MANAGEMENT_IP, Regex("^(?:IP\\s*ADDRESS|WEB\\s*ADDRESS)\\s*[:：]?\\s*(.+)$", RegexOption.IGNORE_CASE)),
        Rule(CandidateKind.USERNAME, Regex("^(?:LOGIN|USERNAME|USER)\\s*[:：]?\\s*(.+)$", RegexOption.IGNORE_CASE)),
        Rule(CandidateKind.PASSWORD, Regex("^(?:PASSWORD|PASS|KEY|暗号化キー|管理パスワード)\\s*[:：]?\\s*(.+)$", RegexOption.IGNORE_CASE)),
    )
    private val macLabel = Regex("^(.*?(?:BSSID|MAC(?:\\s*ADDRESS)?|WLAN\\s*MAC|LAN\\s*MAC|WAN\\s*MAC|(?:2\\.?4|5|6)G\\s*MAC|RADIO\\s*MAC))\\s*[:：]?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val macLoose = Regex("(?<![0-9A-Fa-f])(?:[0-9A-Za-z][ \\t:-]*){12}(?![0-9A-Fa-f])")

    fun parse(document: OcrDocument, nearby: List<WifiAccessPoint> = emptyList()): ParsedDeviceLabel {
        val found = mutableListOf<ParsedFieldCandidate>()
        val lines = document.lines.ifEmpty { document.text.lines().map { OcrTextLine(it) } }
        val normalizedWhole = normalizeText(document.text)
        manufacturers.entries.filter { normalizedWhole.contains(it.key) }.forEach { (key, display) ->
            found += ParsedFieldCandidate(display, CandidateKind.MANUFACTURER, key, ConfidenceLevel.HIGH)
        }
        lines.forEach { line ->
            val text = Normalizer.normalize(line.text.trim(), Normalizer.Form.NFKC)
            if (text.isBlank()) return@forEach
            fieldRules.firstNotNullOfOrNull { rule -> rule.match(text) }?.let(found::add)
            parseMacLine(text).forEach(found::add)
        }
        val matched = WifiCandidateMatcher.attach(found, nearby)
        return ParsedDeviceLabel(
            manufacturerCandidates = matched.filterKind(CandidateKind.MANUFACTURER).singleSelection(),
            modelCandidates = matched.filterKind(CandidateKind.MODEL).singleSelection(),
            serialCandidates = matched.filterKind(CandidateKind.SERIAL).singleSelection(),
            ssidCandidates = matched.filterKind(CandidateKind.SSID).singleSelection(),
            macCandidates = matched.filter { it.kind in setOf(CandidateKind.BSSID, CandidateKind.LAN_MAC, CandidateKind.WAN_MAC) }.distinctBy { it.kind to it.value },
            managementCandidates = matched.filter { it.kind in setOf(CandidateKind.MANAGEMENT_IP, CandidateKind.USERNAME, CandidateKind.PASSWORD) },
            rawText = document.text,
        )
    }

    private fun parseMacLine(text: String): List<ParsedFieldCandidate> {
        val labeled = macLabel.matchEntire(text)
        val source = labeled?.groupValues?.get(1)?.trim().orEmpty()
        val valueArea = labeled?.groupValues?.get(2)?.ifBlank { text } ?: text
        val kind = when {
            source.contains("WAN", true) -> CandidateKind.WAN_MAC
            source.contains("LAN", true) && !source.contains("WLAN", true) -> CandidateKind.LAN_MAC
            else -> CandidateKind.BSSID
        }
        return macLoose.findAll(valueArea).mapNotNull { match ->
            macCandidate(match.value, source.ifBlank { "MAC" }, kind, if (labeled != null) ConfidenceLevel.HIGH else ConfidenceLevel.LOW)
        }.toList()
    }

    private fun macCandidate(raw: String, label: String, kind: CandidateKind, confidence: ConfidenceLevel): ParsedFieldCandidate? {
        val compact = raw.filterNot { it.isWhitespace() || it == ':' || it == '-' }
        BssidFormat.normalize(compact)?.let { normalized ->
            val alternate = when {
                normalized.contains('B') -> BssidFormat.normalize(normalized.replace('B', '8'))
                normalized.contains('8') -> BssidFormat.normalize(normalized.replace('8', 'B'))
                else -> null
            }
            return ParsedFieldCandidate(normalized, kind, label, confidence, bandFrom(label), selected = kind == CandidateKind.BSSID, originalValue = raw.trim(), correctionCandidate = alternate)
        }
        if (compact.length != 12 || compact.any { !it.isLetterOrDigit() }) return null
        val correction = compact.map { ch ->
            when (ch.uppercaseChar()) { 'O' -> '0'; 'I', 'L' -> '1'; 'S' -> '5'; 'G' -> '6'; else -> ch.uppercaseChar() }
        }.joinToString("").let(BssidFormat::normalize)
        if (correction == null) return null
        return ParsedFieldCandidate(raw.trim(), kind, label, ConfidenceLevel.LOW, bandFrom(label), selected = false, originalValue = raw.trim(), correctionCandidate = correction)
    }

    private fun bandFrom(label: String): WifiBand? = when {
        Regex("2\\.?4", RegexOption.IGNORE_CASE).containsMatchIn(label) -> WifiBand.BAND_24
        Regex("(?:^|\\D)5G", RegexOption.IGNORE_CASE).containsMatchIn(label) -> WifiBand.BAND_5
        Regex("(?:^|\\D)6G", RegexOption.IGNORE_CASE).containsMatchIn(label) -> WifiBand.BAND_6
        else -> null
    }

    private fun normalizeText(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).uppercase()
    private fun List<ParsedFieldCandidate>.filterKind(kind: CandidateKind) = filter { it.kind == kind }.distinctBy { it.value }
    private fun List<ParsedFieldCandidate>.singleSelection() = mapIndexed { index, candidate -> candidate.copy(selected = index == 0) }

    private data class Rule(val kind: CandidateKind, val regex: Regex, val valueGroup: Int = 1) {
        fun match(text: String): ParsedFieldCandidate? = regex.matchEntire(text)?.let { result ->
            val value = result.groupValues.getOrNull(valueGroup)?.trim().orEmpty()
            if (value.isBlank()) null else ParsedFieldCandidate(value, kind, result.groupValues.first().substringBefore(value).trim(' ', ':', '：'), ConfidenceLevel.HIGH, bandFromLabel(result.value))
        }
        private fun bandFromLabel(label: String): WifiBand? = when {
            Regex("2\\.?4", RegexOption.IGNORE_CASE).containsMatchIn(label) -> WifiBand.BAND_24
            Regex("(?:^|\\D)5G", RegexOption.IGNORE_CASE).containsMatchIn(label) -> WifiBand.BAND_5
            Regex("(?:^|\\D)6G", RegexOption.IGNORE_CASE).containsMatchIn(label) -> WifiBand.BAND_6
            else -> null
        }
    }
}

object WifiCandidateMatcher {
    fun attach(candidates: List<ParsedFieldCandidate>, nearby: List<WifiAccessPoint>): List<ParsedFieldCandidate> = candidates.map { candidate ->
        when (candidate.kind) {
            CandidateKind.BSSID -> matchBssid(candidate, nearby)
            CandidateKind.SSID -> matchSsid(candidate, nearby)
            else -> candidate
        }
    }.sortedByDescending { it.nearbyMatch != null }

    private fun matchBssid(candidate: ParsedFieldCandidate, nearby: List<WifiAccessPoint>): ParsedFieldCandidate {
        val normalized = BssidFormat.normalize(candidate.value)
        val exact = normalized?.let { value -> nearby.firstOrNull { BssidFormat.normalize(it.bssid) == value } }
        if (exact != null) return candidate.copy(nearbyMatch = NearbyWifiMatch(exact, reason = "BSSID完全一致"), confidence = ConfidenceLevel.HIGH)
        val correction = candidate.correctionCandidate
        val corrected = correction?.let { value -> nearby.firstOrNull { BssidFormat.normalize(it.bssid) == value } }
        if (corrected != null) return candidate.copy(nearbyMatch = NearbyWifiMatch(corrected, corrected.bssid, "OCR補正候補"))
        val oui = normalized?.take(8)?.let { prefix -> nearby.firstOrNull { BssidFormat.normalize(it.bssid)?.take(8) == prefix } }
        return if (oui != null) candidate.copy(nearbyMatch = NearbyWifiMatch(oui, reason = "メーカーOUI候補"), confidence = ConfidenceLevel.MEDIUM) else candidate
    }

    private fun matchSsid(candidate: ParsedFieldCandidate, nearby: List<WifiAccessPoint>): ParsedFieldCandidate {
        val exact = nearby.firstOrNull { it.ssid == candidate.value }
        if (exact != null) return candidate.copy(nearbyMatch = NearbyWifiMatch(exact, reason = "SSID完全一致"), confidence = ConfidenceLevel.HIGH)
        val key = normalizeSsid(candidate.value)
        val normalized = nearby.firstOrNull { normalizeSsid(it.ssid) == key }
        return if (normalized != null) candidate.copy(nearbyMatch = NearbyWifiMatch(normalized, reason = "SSID正規化一致"), confidence = ConfidenceLevel.MEDIUM) else candidate
    }

    private fun normalizeSsid(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase().replace(Regex("[ _-]+"), "")
}
