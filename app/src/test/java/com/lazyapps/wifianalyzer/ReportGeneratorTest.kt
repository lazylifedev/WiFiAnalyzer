package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.export.*
import org.junit.Assert.*
import org.junit.Test

class ReportGeneratorTest {
    private val ja = ReportLabels("ja", "作成日時", "ワークスペース", "グループ", "メーカー", "型番", "シリアル番号", "BSSID一覧", "設置場所", "メモ", "検出状態", "推定距離", "最終検出", "機器件数")
    private val en = ReportLabels("en", "Created", "Workspace", "Group", "Manufacturer", "Model", "Serial number", "BSSIDs", "Location", "Notes", "Detection status", "Estimated distance", "Last detected", "Devices")

    @Test fun `report is standalone escaped Japanese printable html`() {
        val device = ReportDevice("東京", null, "機器<script>", "A&B", "型番", "SERIAL", "long<ssid>", listOf("00:11:22:33:44:55"), "棚", "a\nlong memo", "未検出", "-60 dBm", "1.00 m", "2026-07-23 09:00:00", emptyList())
        val html = ReportGenerator.generate("一覧", "2026-07-23 09:00:00", ja, sequenceOf(device))
        assertTrue(html.startsWith("<!doctype html>")); assertTrue(html.contains("lang=\"ja\"")); assertTrue(html.contains("@page{size:A4 portrait"))
        assertTrue(html.contains("機器&lt;script&gt;")); assertTrue(html.contains("A&amp;B")); assertFalse(html.contains("<script>")); assertFalse(html.contains("http://")); assertFalse(html.contains("https://")); assertTrue(html.contains("機器件数: 1"))
    }

    @Test fun `English labels and long values remain wrap safe`() { val html = ReportGenerator.generate("Wi-Fi Device Report", "Jul 23, 2026", en, sequenceOf(ReportDevice("A very long workspace name", null, "Device", "", "", "", "SSID", emptyList(), "", "", "Not detected", null, null, null, emptyList()))); assertTrue(html.contains("lang=\"en\"")); assertTrue(html.contains("overflow-wrap:anywhere")); assertTrue(html.contains("Manufacturer")); assertTrue(html.contains("Devices: 1")) }
    @Test fun `photo mode model allows none primary and all`() { assertEquals(listOf(ReportPhotoMode.NONE, ReportPhotoMode.PRIMARY, ReportPhotoMode.ALL), ReportPhotoMode.entries) }
    @Test fun `report never invents credential fields`() { val html = ReportGenerator.generate("x", "now", en, emptySequence()); listOf("Wi-Fi password", "API key", "Admin password", "Encryption key").forEach { assertFalse(html.contains(it)) } }
}
