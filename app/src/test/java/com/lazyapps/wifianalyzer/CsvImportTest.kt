package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.importcsv.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvImportTest {
    private fun parse(text: String, encoding: CsvEncoding = CsvEncoding.UTF8) = CsvImportParser().readAll(ByteArrayInputStream(text.toByteArray(encoding.charset)), encoding)

    @Test fun `parses CRLF LF and CR with trailing empty cells`() {
        listOf("\r\n", "\n", "\r").forEach { nl ->
            val rows = parse("a,b,$nl" + "1,2,$nl")
            assertEquals(listOf("a", "b", ""), rows[0].cells)
            assertEquals(listOf("1", "2", ""), rows[1].cells)
        }
    }

    @Test fun `parses commas newlines and escaped quotes inside quoted cells`() {
        val rows = parse("name,notes\r\n\"Router, A\",\"line1\r\nline2 \"\"ok\"\"\"")
        assertEquals(2, rows.size)
        assertEquals("Router, A", rows[1].cells[0])
        assertEquals("line1\r\nline2 \"ok\"", rows[1].cells[1])
    }

    @Test fun `detects UTF8 BOM UTF8 and Windows31J`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "機器名".toByteArray()
        assertTrue(CsvEncodingDetector.detect(bom).hasBom)
        assertEquals(CsvEncoding.UTF8, CsvEncodingDetector.detect("日本語".toByteArray()).encoding)
        assertEquals(CsvEncoding.WINDOWS_31J, CsvEncodingDetector.detect("日本語".toByteArray(Charsets.UTF_8).let { "日本語".toByteArray(CsvEncoding.WINDOWS_31J.charset) }).encoding)
    }

    @Test fun `auto maps Japanese and English aliases without duplicates`() {
        val mapped = CsvColumnMapper.autoMap(listOf("機器名", "workspaceName", "serialNumber", "主BSSID", "name"))
        assertEquals(listOf(ImportField.DEVICE_NAME, ImportField.WORKSPACE, ImportField.SERIAL, ImportField.PRIMARY_BSSID, ImportField.UNUSED), mapped)
        assertTrue(CsvColumnMapper.validate(mapped).isEmpty())
        assertTrue(CsvColumnMapper.validate(listOf(ImportField.WORKSPACE)).isNotEmpty())
    }

    @Test fun `validator normalizes BSSID formats and NFKC matching names`() {
        val mapping = listOf(ImportField.DEVICE_NAME, ImportField.PRIMARY_BSSID, ImportField.ALL_BSSIDS)
        val row = ImportValidator.map(CsvRecord(2, listOf("Ｒｏｕｔｅｒ", "aa-bb-cc-dd-ee-ff", "AABBCCDDEEFF; 11:22:33:44:55:66")), mapping, 1L)
        assertEquals(listOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"), row.bssids)
        assertTrue(row.errors.isEmpty())
        assertEquals("router", normalizedForTest(row.deviceName))
    }

    @Test fun `rejects malformed CSV and invalid BSSID`() {
        assertThrows(CsvImportException::class.java) { parse("a\n\"unterminated") }
        val row = ImportValidator.map(CsvRecord(2, listOf("Device", "bad")), listOf(ImportField.DEVICE_NAME, ImportField.PRIMARY_BSSID))
        assertTrue(row.errors.any { it.contains("BSSID") })
    }

    private fun normalizedForTest(value: String) = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC).lowercase()
}
