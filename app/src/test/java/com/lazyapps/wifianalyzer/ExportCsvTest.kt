package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.export.*
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class ExportCsvTest {
    private val columns = listOf(ExportColumn("name", "機器名"), ExportColumn("memo", "メモ"))
    private fun csv(vararg rows: ExportRow, bom: Boolean = true) = StringWriter().also { CsvWriter.write(it, columns, rows.asSequence(), bom) }.toString()

    @Test fun `csv uses bom crlf quotes and Japanese headers`() {
        val value = csv(ExportRow(mapOf("name" to "テスト", "memo" to null)))
        assertTrue(value.startsWith("\uFEFF\"機器名\",\"メモ\"\r\n"))
        assertTrue(value.endsWith("\"テスト\",\"\"\r\n"))
        assertFalse(value.replace("\r\n", "").contains('\n'))
    }

    @Test fun `csv escapes commas quotes and newlines`() {
        val value = csv(ExportRow(mapOf("name" to "a,b", "memo" to "line1\n\"line2\"")), bom = false)
        assertEquals("\"機器名\",\"メモ\"\r\n\"a,b\",\"line1\n\"\"line2\"\"\"\r\n", value)
    }

    @Test fun `csv protects every dangerous spreadsheet prefix`() {
        listOf("=SUM(1)", "+1", "-1", "@x", "\tcmd", "\ncmd", "\rcmd").forEach { assertEquals("'$it", CsvWriter.sanitize(it)) }
        assertEquals("safe", CsvWriter.sanitize("safe"))
    }

    @Test fun `preview is limited and excludes bom`() {
        val rows = generateSequence(0) { it + 1 }.map { ExportRow(mapOf("name" to "$it", "memo" to "x")) }
        val preview = CsvWriter.preview(columns, rows, 20)
        assertFalse(preview.startsWith("\uFEFF")); assertEquals(21, preview.split("\r\n").filter { it.isNotEmpty() }.size)
    }

    @Test fun `standard columns include requested BSSID and photo contracts`() {
        assertTrue(ExportColumns.devices.map { it.key }.containsAll(listOf("allBssids", "photoCount", "primaryPhotoCaption")))
        assertTrue(ExportColumns.bssids.first { it.key == "bssid" }.required)
        assertFalse(ExportColumns.photos.any { it.key.contains("path", ignoreCase = true) })
        assertEquals(setOf("bssid"), ExportColumns.minimum(ExportType.BSSIDS))
    }
}
