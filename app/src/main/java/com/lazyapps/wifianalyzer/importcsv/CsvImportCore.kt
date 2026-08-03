package com.lazyapps.wifianalyzer.importcsv

import com.lazyapps.wifianalyzer.domain.BssidFormat
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ImportLimits {
    const val MAX_FILE_BYTES = 50L * 1024 * 1024
    const val MAX_ROWS = 100_000
    const val MAX_ROW_CHARS = 1_048_576
    const val MAX_COLUMNS = 200
    const val MAX_CELL_CHARS = 100_000
    const val MAX_BSSIDS = 100
}

enum class CsvEncoding(val charset: Charset, val label: String) {
    UTF8(Charsets.UTF_8, "UTF-8"),
    WINDOWS_31J(Charset.forName("Windows-31J"), "Windows-31J"),
}

data class EncodingDetection(val encoding: CsvEncoding, val hasBom: Boolean)

object CsvEncodingDetector {
    fun detect(bytes: ByteArray): EncodingDetection {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return EncodingDetection(CsvEncoding.UTF8, true)
        }
        return try {
            CsvEncoding.UTF8.charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            EncodingDetection(CsvEncoding.UTF8, false)
        } catch (_: CharacterCodingException) {
            EncodingDetection(CsvEncoding.WINDOWS_31J, false)
        }
    }
}

enum class CsvImportError { FILE_TOO_LARGE, FILE_UNREADABLE, EMPTY_FILE, COLUMN_COUNT_MISMATCH, OUTPUT_UNAVAILABLE, ROW_TOO_LONG, CELL_TOO_LONG, TOO_MANY_COLUMNS, TOO_MANY_ROWS, INVALID_AFTER_QUOTE, INVALID_QUOTE, UNCLOSED_QUOTE }
class CsvImportException(val error: CsvImportError, val row: Int? = null) : IllegalArgumentException(error.name)
data class CsvRecord(val rowNumber: Int, val cells: List<String>)

/** RFC 4180 compatible streaming parser. CRLF, LF, and CR are accepted. */
class CsvImportParser {
    fun parse(input: InputStream, encoding: CsvEncoding, hasBom: Boolean = false, onRecord: (CsvRecord) -> Unit) {
        val reader = PushbackReader(BufferedReader(InputStreamReader(input, encoding.charset), 32 * 1024), 1)
        if (hasBom) {
            val first = reader.read()
            if (first != 0xFEFF && first >= 0) reader.unread(first)
        }
        var row = 1
        var rowChars = 0
        var inQuotes = false
        var afterQuote = false
        var atCellStart = true
        val cells = ArrayList<String>()
        val cell = StringBuilder()

        fun append(ch: Char) {
            rowChars++
            if (rowChars > ImportLimits.MAX_ROW_CHARS) throw CsvImportException(CsvImportError.ROW_TOO_LONG, row)
            if (cell.length >= ImportLimits.MAX_CELL_CHARS) throw CsvImportException(CsvImportError.CELL_TOO_LONG, row)
            cell.append(ch)
        }
        fun finishCell() {
            if (cells.size >= ImportLimits.MAX_COLUMNS) throw CsvImportException(CsvImportError.TOO_MANY_COLUMNS, row)
            cells += cell.toString(); cell.setLength(0); atCellStart = true; afterQuote = false
        }
        fun finishRow() {
            finishCell()
            if (row > ImportLimits.MAX_ROWS + 1) throw CsvImportException(CsvImportError.TOO_MANY_ROWS, row)
            onRecord(CsvRecord(row, cells.toList()))
            cells.clear(); row++; rowChars = 0
        }

        while (true) {
            val value = reader.read()
            if (value < 0) break
            val ch = value.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    val next = reader.read()
                    if (next == '"'.code) append('"') else {
                        inQuotes = false; afterQuote = true
                        if (next >= 0) reader.unread(next)
                    }
                } else append(ch)
                continue
            }
            if (afterQuote && ch != ',' && ch != '\r' && ch != '\n') {
                throw CsvImportException(CsvImportError.INVALID_AFTER_QUOTE, row)
            }
            when (ch) {
                '"' -> if (atCellStart) { inQuotes = true; atCellStart = false } else throw CsvImportException(CsvImportError.INVALID_QUOTE, row)
                ',' -> finishCell()
                '\r', '\n' -> {
                    if (ch == '\r') { val next = reader.read(); if (next >= 0 && next != '\n'.code) reader.unread(next) }
                    finishRow()
                }
                else -> { append(ch); atCellStart = false }
            }
        }
        if (inQuotes) throw CsvImportException(CsvImportError.UNCLOSED_QUOTE, row)
        if (cell.isNotEmpty() || cells.isNotEmpty() || !atCellStart) finishRow()
    }

    fun readAll(input: InputStream, encoding: CsvEncoding, hasBom: Boolean = false): List<CsvRecord> =
        buildList { parse(input, encoding, hasBom, ::add) }
}

enum class ImportField {
    UNUSED, WORKSPACE, GROUP, DEVICE_NAME, MANUFACTURER, MODEL, SERIAL, SSID,
    PRIMARY_BSSID, ALL_BSSIDS, LOCATION, NOTES, CREATED_AT, UPDATED_AT,
}

object CsvColumnMapper {
    private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase()
        .replace("_", "").replace(" ", "")
    private val aliases = mapOf(
        "ワークスペース" to ImportField.WORKSPACE, "workspace" to ImportField.WORKSPACE, "workspacename" to ImportField.WORKSPACE,
        "グループ" to ImportField.GROUP, "group" to ImportField.GROUP, "groupname" to ImportField.GROUP,
        "機器名" to ImportField.DEVICE_NAME, "devicename" to ImportField.DEVICE_NAME, "name" to ImportField.DEVICE_NAME,
        "メーカー" to ImportField.MANUFACTURER, "manufacturer" to ImportField.MANUFACTURER,
        "型番" to ImportField.MODEL, "model" to ImportField.MODEL,
        "シリアル番号" to ImportField.SERIAL, "serial" to ImportField.SERIAL, "serialnumber" to ImportField.SERIAL,
        "ssid" to ImportField.SSID, "主bssid" to ImportField.PRIMARY_BSSID, "bssid" to ImportField.PRIMARY_BSSID, "primarybssid" to ImportField.PRIMARY_BSSID,
        "全bssid" to ImportField.ALL_BSSIDS, "allbssids" to ImportField.ALL_BSSIDS,
        "設置場所" to ImportField.LOCATION, "location" to ImportField.LOCATION,
        "メモ" to ImportField.NOTES, "notes" to ImportField.NOTES, "memo" to ImportField.NOTES,
        "登録日時" to ImportField.CREATED_AT, "createdat" to ImportField.CREATED_AT,
        "更新日時" to ImportField.UPDATED_AT, "updatedat" to ImportField.UPDATED_AT,
    ).mapKeys { key(it.key) }
    fun autoMap(headers: List<String>): List<ImportField> {
        val used = mutableSetOf<ImportField>()
        return headers.map { aliases[key(it)]?.takeIf(used::add) ?: ImportField.UNUSED }
    }
    fun validate(mapping: List<ImportField>): List<String> = buildList {
        if (ImportField.DEVICE_NAME !in mapping) add("MISSING_DEVICE_NAME_COLUMN")
        mapping.filter { it != ImportField.UNUSED }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add("DUPLICATE_FIELD:${it.name}") }
    }
}

enum class ImportMode { ADD_ONLY, ADD_AND_UPDATE }
enum class MatchKey { AUTO, SERIAL, PRIMARY_BSSID, ANY_BSSID, NAME_AND_WORKSPACE }
enum class WorkspaceMode { CSV, CURRENT }
enum class BlankMode { KEEP, CLEAR }
enum class BssidUpdateMode { APPEND, REPLACE }
enum class ErrorMode { ABORT_ALL, VALID_ROWS_ONLY }
enum class ImportRowStatus { NEW, UPDATE, SKIP, ERROR, CONFLICT }

data class ImportSettings(
    val mode: ImportMode = ImportMode.ADD_ONLY,
    val matchKey: MatchKey = MatchKey.AUTO,
    val workspaceMode: WorkspaceMode = WorkspaceMode.CSV,
    val blankMode: BlankMode = BlankMode.KEEP,
    val bssidMode: BssidUpdateMode = BssidUpdateMode.APPEND,
    val errorMode: ErrorMode = ErrorMode.ABORT_ALL,
)

data class ImportedDeviceRow(
    val sourceRow: Int, val workspace: String, val group: String, val deviceName: String,
    val manufacturer: String, val model: String, val serial: String, val ssid: String,
    val primaryBssid: String, val bssids: List<String>, val location: String, val notes: String,
    val createdAt: Long?, val warnings: List<String> = emptyList(), val errors: List<String> = emptyList(),
)

object ImportValidator {
    fun map(record: CsvRecord, mapping: List<ImportField>, now: Long = System.currentTimeMillis()): ImportedDeviceRow {
        val values = ImportField.entries.associateWith { field -> mapping.indexOf(field).takeIf { it >= 0 }?.let { record.cells.getOrElse(it) { "" } }.orEmpty() }
        val errors = mutableListOf<String>(); val warnings = mutableListOf<String>()
        val name = values.getValue(ImportField.DEVICE_NAME).trim()
        if (name.isBlank()) errors += "DEVICE_NAME_REQUIRED"
        if (name.length > 100) errors += "DEVICE_NAME_TOO_LONG"
        val rawBssids = buildList {
            values.getValue(ImportField.PRIMARY_BSSID).trim().takeIf(String::isNotEmpty)?.let(::add)
            values.getValue(ImportField.ALL_BSSIDS).split(';').map(String::trim).filter(String::isNotEmpty).forEach(::add)
        }
        if (rawBssids.size > ImportLimits.MAX_BSSIDS) errors += "TOO_MANY_BSSIDS"
        val normalized = rawBssids.map { raw -> BssidFormat.normalize(raw) ?: run { errors += "INVALID_BSSID"; raw } }.distinct()
        val primary = values.getValue(ImportField.PRIMARY_BSSID).trim().let { if (it.isBlank()) normalized.firstOrNull().orEmpty() else BssidFormat.normalize(it).orEmpty() }
        val created = parseDate(values.getValue(ImportField.CREATED_AT)).also { if (values.getValue(ImportField.CREATED_AT).isNotBlank() && it == null) warnings += "CREATED_AT_CORRECTED" } ?: now
        return ImportedDeviceRow(record.rowNumber, values.getValue(ImportField.WORKSPACE).trim(), values.getValue(ImportField.GROUP).trim(), name,
            values.getValue(ImportField.MANUFACTURER).trim(), values.getValue(ImportField.MODEL).trim(), values.getValue(ImportField.SERIAL).trim(), values.getValue(ImportField.SSID).trim(),
            primary, normalized.filter { BssidFormat.isValid(it) }.let { if (primary.isNotBlank() && primary !in it) listOf(primary) + it else it },
            values.getValue(ImportField.LOCATION).trim(), values.getValue(ImportField.NOTES).trim(), created, warnings, errors)
    }
    private fun parseDate(value: String): Long? {
        if (value.isBlank()) return null
        value.toLongOrNull()?.let { return it }
        runCatching { return Instant.parse(value).toEpochMilli() }
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd HH:mm")
        return formats.firstNotNullOfOrNull { pattern -> runCatching { LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() }
    }
}

internal fun normalizedName(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase()
