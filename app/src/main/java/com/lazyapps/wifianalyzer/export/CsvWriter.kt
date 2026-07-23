package com.lazyapps.wifianalyzer.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

object CsvWriter {
    private const val BOM = '\uFEFF'
    fun write(output: OutputStream, columns: List<ExportColumn>, rows: Sequence<ExportRow>) =
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { write(it, columns, rows, includeBom = true) }

    fun write(writer: Writer, columns: List<ExportColumn>, rows: Sequence<ExportRow>, includeBom: Boolean = true) {
        require(columns.isNotEmpty()) { "少なくとも1列を選択してください" }
        if (includeBom) writer.write(BOM.code)
        writeLine(writer, columns.map { it.header })
        rows.forEach { row -> writeLine(writer, columns.map { sanitize(row.values[it.key]) }) }
        writer.flush()
    }

    fun preview(columns: List<ExportColumn>, rows: Sequence<ExportRow>, limit: Int = 20): String = buildString {
        val writer = java.io.StringWriter()
        write(writer, columns, rows.take(limit), includeBom = false)
        append(writer.toString())
    }

    internal fun sanitize(value: String?): String {
        if (value == null) return ""
        val dangerous = value.firstOrNull()?.let { it == '=' || it == '+' || it == '-' || it == '@' || it == '\t' || it == '\r' || it == '\n' } == true
        return if (dangerous) "'$value" else value
    }

    private fun writeLine(writer: Writer, cells: List<String>) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) writer.write(','.code)
            writer.write('"'.code); writer.write(cell.replace("\"", "\"\"")); writer.write('"'.code)
        }
        writer.write("\r\n")
    }
}
