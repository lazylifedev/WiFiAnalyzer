package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.export.*
import org.junit.Assert.*
import org.junit.Test

class ReportGeneratorTest {
    @Test fun `report is standalone escaped Japanese printable html`() {
        val device = ReportDevice("東京", null, "機器<script>", "A&B", "型番", "SERIAL", "long<ssid>", listOf("00:11:22:33:44:55"), "棚", "a\nlong memo", "未検出", "-60 dBm", "1.00 m", "2026-07-23 09:00:00", emptyList())
        val html = ReportGenerator.generate("一覧", "2026-07-23 09:00:00", sequenceOf(device))
        assertTrue(html.startsWith("<!doctype html>")); assertTrue(html.contains("lang=\"ja\"")); assertTrue(html.contains("@page{size:A4 portrait"))
        assertTrue(html.contains("機器&lt;script&gt;")); assertTrue(html.contains("A&amp;B")); assertFalse(html.contains("<script>")); assertFalse(html.contains("http://")); assertFalse(html.contains("https://"))
        assertTrue(html.contains("機器件数: 1"))
    }

    @Test fun `photo mode model allows none primary and all`() { assertEquals(listOf(ReportPhotoMode.NONE, ReportPhotoMode.PRIMARY, ReportPhotoMode.ALL), ReportPhotoMode.entries) }
    @Test fun `report never invents credential fields`() { val html = ReportGenerator.generate("x", "now", emptySequence()); listOf("Wi-Fiパスワード", "APIキー", "管理パスワード", "暗号化キー").forEach { assertFalse(html.contains(it)) } }
}
