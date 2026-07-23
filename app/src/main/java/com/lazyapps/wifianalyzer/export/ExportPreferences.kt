package com.lazyapps.wifianalyzer.export

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.exportDataStore by preferencesDataStore("export_preferences")
data class ColumnPreset(val order: List<String>, val enabled: Set<String>)

class ExportPreferences(private val context: Context) {
    fun preset(type: ExportType): Flow<ColumnPreset> = context.exportDataStore.data.map { p ->
        val standard = ExportColumns.forType(type).map { it.key }; val order = p[stringPreferencesKey("${type.name}_order")]?.split('|')?.filter { it in standard }.orEmpty() + standard.filterNot { it in p[stringPreferencesKey("${type.name}_order")]?.split('|').orEmpty() }
        val enabled = p[stringPreferencesKey("${type.name}_enabled")]?.split('|')?.filter { it in standard }?.toSet() ?: standard.toSet()
        ColumnPreset(order, enabled.ifEmpty { ExportColumns.minimum(type) })
    }
    suspend fun save(type: ExportType, preset: ColumnPreset) = context.exportDataStore.edit { p -> p[stringPreferencesKey("${type.name}_order")] = preset.order.joinToString("|"); p[stringPreferencesKey("${type.name}_enabled")] = preset.enabled.joinToString("|") }
    suspend fun history(): ExportHistory { val p = context.exportDataStore.data.first(); return ExportHistory(p[LAST_CSV_AT], p[LAST_CSV_TYPE]?.let { runCatching { ExportType.valueOf(it) }.getOrNull() }, p[LAST_CSV_COUNT] ?: 0, p[LAST_REPORT_AT], p[LAST_REPORT_TARGET], p[LAST_SUCCESS]?.toBooleanStrictOrNull()) }
    suspend fun recordCsv(type: ExportType, count: Int, success: Boolean) = context.exportDataStore.edit { it[LAST_CSV_AT] = System.currentTimeMillis(); it[LAST_CSV_TYPE] = type.name; it[LAST_CSV_COUNT] = count; it[LAST_SUCCESS] = success.toString() }
    suspend fun recordReport(target: String, success: Boolean) = context.exportDataStore.edit { it[LAST_REPORT_AT] = System.currentTimeMillis(); it[LAST_REPORT_TARGET] = target; it[LAST_SUCCESS] = success.toString() }
    companion object { private val LAST_CSV_AT = longPreferencesKey("last_csv_at"); private val LAST_CSV_TYPE = stringPreferencesKey("last_csv_type"); private val LAST_CSV_COUNT = intPreferencesKey("last_csv_count"); private val LAST_REPORT_AT = longPreferencesKey("last_report_at"); private val LAST_REPORT_TARGET = stringPreferencesKey("last_report_target"); private val LAST_SUCCESS = stringPreferencesKey("last_success") }
}
