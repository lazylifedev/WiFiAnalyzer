package com.lazyapps.wifianalyzer.importcsv

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.data.registry.RegisteredWifiDeviceEntity
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WifiDeviceBssidEntity
import com.lazyapps.wifianalyzer.data.registry.WifiDeviceGroupEntity
import com.lazyapps.wifianalyzer.data.registry.WorkspaceEntity
import kotlinx.coroutines.flow.first

data class PlannedImportRow(
    val source: ImportedDeviceRow,
    val status: ImportRowStatus,
    val existingDeviceId: Long? = null,
    val messages: List<String> = emptyList(),
)

data class ImportPreview(
    val rows: List<PlannedImportRow>,
    val workspaceNames: Set<String>,
    val groupNames: Set<Pair<String, String>>,
) {
    val total get() = rows.size
    val additions get() = rows.count { it.status == ImportRowStatus.NEW }
    val updates get() = rows.count { it.status == ImportRowStatus.UPDATE }
    val skips get() = rows.count { it.status == ImportRowStatus.SKIP }
    val errors get() = rows.count { it.status == ImportRowStatus.ERROR }
    val conflicts get() = rows.count { it.status == ImportRowStatus.CONFLICT }
    val warnings get() = rows.sumOf { it.source.warnings.size }
}

data class ImportResult(
    val added: Int, val updated: Int, val skipped: Int, val errors: Int,
    val workspacesCreated: Int, val groupsCreated: Int, val bssidsRegistered: Int,
    val elapsedMillis: Long, val affectedDeviceIds: List<Long>,
)

class ImportRepository(private val database: WifiAnalyzerDatabase) {
    private val dao = database.registryDao()

    suspend fun plan(rows: List<ImportedDeviceRow>, settings: ImportSettings, currentWorkspaceId: Long): ImportPreview {
        val workspaces = dao.getWorkspacesOnce()
        val workspaceByName = workspaces.associateBy { normalizedName(it.name) }
        val currentName = workspaces.firstOrNull { it.id == currentWorkspaceId }?.name.orEmpty()
        val allDevices = dao.getAllDevices()
        val allBssids = dao.getAllBssids()
        val bssidsByDevice = allBssids.groupBy { it.deviceId }
        val planned = mutableListOf<PlannedImportRow>()
        val seenKeys = mutableSetOf<String>()
        rows.forEach { source ->
            val workspaceName = if (settings.workspaceMode == WorkspaceMode.CURRENT || source.workspace.isBlank()) currentName else source.workspace
            val workspace = workspaceByName[normalizedName(workspaceName)]
            val workspaceId = workspace?.id
            val row = source.copy(workspace = workspaceName)
            if (row.errors.isNotEmpty()) { planned += PlannedImportRow(row, ImportRowStatus.ERROR, messages = row.errors); return@forEach }
            val duplicateKey = normalizedName(workspaceName) + "|" + normalizedName(row.deviceName) + "|" + row.serial.lowercase() + "|" + row.bssids.joinToString()
            if (!seenKeys.add(duplicateKey)) { planned += PlannedImportRow(row, ImportRowStatus.ERROR, messages = listOf("DUPLICATE_CSV_DEVICE")); return@forEach }
            val candidates = if (workspaceId == null) emptySet() else findCandidates(row, workspaceId, allDevices, bssidsByDevice, settings.matchKey)
            if (candidates.size > 1) { planned += PlannedImportRow(row, ImportRowStatus.CONFLICT, messages = listOf("MULTIPLE_DEVICE_MATCHES")); return@forEach }
            val existing = candidates.singleOrNull()
            val conflictingBssids = if (workspaceId == null) emptyList() else allBssids.filter { it.workspaceId == workspaceId && it.bssid in row.bssids && it.deviceId != existing?.id }
            if (conflictingBssids.isNotEmpty()) { planned += PlannedImportRow(row, ImportRowStatus.CONFLICT, messages = listOf("BSSID_IN_OTHER_DEVICE")); return@forEach }
            planned += when {
                existing == null -> PlannedImportRow(row, ImportRowStatus.NEW)
                settings.mode == ImportMode.ADD_ONLY -> PlannedImportRow(row, ImportRowStatus.SKIP, existing.id, listOf("EXISTING_DEVICE_MATCH"))
                else -> PlannedImportRow(row, ImportRowStatus.UPDATE, existing.id)
            }
        }
        return ImportPreview(planned, planned.filter { it.status == ImportRowStatus.NEW }.map { it.source.workspace }.filter(String::isNotBlank).toSet(),
            planned.filter { it.status in setOf(ImportRowStatus.NEW, ImportRowStatus.UPDATE) }.mapNotNull { p -> p.source.group.takeIf(String::isNotBlank)?.let { p.source.workspace to it } }.toSet())
    }

    private fun findCandidates(row: ImportedDeviceRow, workspaceId: Long, devices: List<RegisteredWifiDeviceEntity>, bssids: Map<Long, List<WifiDeviceBssidEntity>>, key: MatchKey): Set<RegisteredWifiDeviceEntity> {
        val inWorkspace = devices.filter { it.workspaceId == workspaceId }
        fun serial() = row.serial.takeIf(String::isNotBlank)?.let { value -> inWorkspace.filter { it.serialNumber.trim().equals(value.trim(), true) } }.orEmpty()
        fun primary() = row.primaryBssid.takeIf(String::isNotBlank)?.let { value -> inWorkspace.filter { device -> device.primaryBssid == value } }.orEmpty()
        fun anyBssid() = if (row.bssids.isEmpty()) emptyList() else inWorkspace.filter { device -> bssids[device.id].orEmpty().any { it.bssid in row.bssids } }
        fun name() = inWorkspace.filter { normalizedName(it.displayName) == normalizedName(row.deviceName) }
        val result = when (key) {
            MatchKey.SERIAL -> serial(); MatchKey.PRIMARY_BSSID -> primary(); MatchKey.ANY_BSSID -> anyBssid(); MatchKey.NAME_AND_WORKSPACE -> name()
            MatchKey.AUTO -> serial().takeIf(List<*>::isNotEmpty) ?: anyBssid().takeIf(List<*>::isNotEmpty) ?: name()
        }
        return result.toSet()
    }

    suspend fun execute(preview: ImportPreview, settings: ImportSettings): ImportResult {
        if (settings.errorMode == ErrorMode.ABORT_ALL && preview.rows.any { it.status in setOf(ImportRowStatus.ERROR, ImportRowStatus.CONFLICT) }) {
            throw IllegalStateException("IMPORT_ABORTED_FOR_ERRORS")
        }
        val started = System.currentTimeMillis()
        var createdWorkspaces = 0; var createdGroups = 0; var registeredBssids = 0
        val affected = mutableListOf<Long>()
        database.withTransaction {
            val workspaceMap = dao.getWorkspacesOnce().associateByTo(mutableMapOf()) { normalizedName(it.name) }
            val executable = preview.rows.filter { it.status in setOf(ImportRowStatus.NEW, ImportRowStatus.UPDATE) }
            executable.forEach { planned ->
                val row = planned.source; val now = System.currentTimeMillis()
                val workspace = workspaceMap.getOrPut(normalizedName(row.workspace)) {
                    createdWorkspaces++
                    val id = dao.insertWorkspace(WorkspaceEntity(name = row.workspace, normalizedName = normalizedName(row.workspace), sortOrder = workspaceMap.size, createdAt = now, updatedAt = now))
                    WorkspaceEntity(id, row.workspace, normalizedName(row.workspace), workspaceMap.size, now, now)
                }
                val groups = dao.getGroupsOnce(workspace.id)
                val groupId = row.group.takeIf(String::isNotBlank)?.let { name ->
                    groups.firstOrNull { normalizedName(it.name) == normalizedName(name) }?.id ?: run {
                        createdGroups++; dao.insertGroup(WifiDeviceGroupEntity(name = name, normalizedName = normalizedName(name), sortOrder = groups.size, createdAt = now, updatedAt = now, workspaceId = workspace.id))
                    }
                }
                val existing = planned.existingDeviceId?.let { dao.getDevice(it) }
                fun imported(value: String, old: String) = if (value.isBlank() && settings.blankMode == BlankMode.KEEP) old else value
                val entity = RegisteredWifiDeviceEntity(
                    id = existing?.id ?: 0, displayName = imported(row.deviceName, existing?.displayName.orEmpty()),
                    manufacturer = imported(row.manufacturer, existing?.manufacturer.orEmpty()), model = imported(row.model, existing?.model.orEmpty()),
                    serialNumber = imported(row.serial, existing?.serialNumber.orEmpty()), primaryBssid = row.primaryBssid.ifBlank { existing?.primaryBssid.orEmpty() },
                    ssid = imported(row.ssid, existing?.ssid.orEmpty()), groupId = groupId ?: if (settings.blankMode == BlankMode.KEEP) existing?.groupId else null,
                    location = imported(row.location, existing?.location.orEmpty()), notes = imported(row.notes, existing?.notes.orEmpty()),
                    createdAt = existing?.createdAt ?: row.createdAt ?: now, updatedAt = now, lastSeenAt = existing?.lastSeenAt,
                    lastSeenRssi = existing?.lastSeenRssi, isEnabled = existing?.isEnabled ?: true, workspaceId = workspace.id,
                )
                val deviceId = if (existing == null) dao.insertDevice(entity) else { dao.updateDevice(entity); existing.id }
                val previous = if (existing == null || settings.bssidMode == BssidUpdateMode.REPLACE) emptyList() else dao.getBssids(deviceId).map { it.bssid }
                val merged = (previous + row.bssids).distinct()
                dao.deleteBssidsForDevice(deviceId)
                if (merged.isNotEmpty()) dao.insertBssids(merged.map { WifiDeviceBssidEntity(deviceId = deviceId, bssid = it, band = "UNKNOWN", createdAt = now, workspaceId = workspace.id) })
                registeredBssids += merged.size; affected += deviceId
            }
        }
        return ImportResult(preview.additions, preview.updates, preview.skips, preview.errors + preview.conflicts, createdWorkspaces, createdGroups, registeredBssids, System.currentTimeMillis() - started, affected)
    }
}

private val Context.importDataStore by preferencesDataStore("csv_import_preferences")
class ImportPreferences(private val context: Context) {
    suspend fun save(fileName: String, settings: ImportSettings, result: ImportResult?, succeeded: Boolean) = context.importDataStore.edit {
        it[LAST_AT] = System.currentTimeMillis(); it[LAST_FILE] = fileName.take(255); it[LAST_COUNT] = result?.let { r -> r.added + r.updated } ?: 0
        it[LAST_SUCCESS] = succeeded.toString(); it[MATCH] = settings.matchKey.name; it[BLANK] = settings.blankMode.name
        it[BSSID] = settings.bssidMode.name; it[ERROR] = settings.errorMode.name
    }
    suspend fun loadSettings(): ImportSettings { val p = context.importDataStore.data.first(); return ImportSettings(
        matchKey = p[MATCH]?.let { runCatching { MatchKey.valueOf(it) }.getOrNull() } ?: MatchKey.AUTO,
        blankMode = p[BLANK]?.let { runCatching { BlankMode.valueOf(it) }.getOrNull() } ?: BlankMode.KEEP,
        bssidMode = p[BSSID]?.let { runCatching { BssidUpdateMode.valueOf(it) }.getOrNull() } ?: BssidUpdateMode.APPEND,
        errorMode = p[ERROR]?.let { runCatching { ErrorMode.valueOf(it) }.getOrNull() } ?: ErrorMode.ABORT_ALL,
    ) }
    suspend fun saveMapping(headers: List<String>, mapping: List<ImportField>) = context.importDataStore.edit { it[mappingKey(headers)] = mapping.joinToString("|") { field -> field.name } }
    suspend fun loadMapping(headers: List<String>): List<ImportField>? = context.importDataStore.data.first()[mappingKey(headers)]?.split('|')?.mapNotNull { runCatching { ImportField.valueOf(it) }.getOrNull() }?.takeIf { it.size == headers.size }
    private fun mappingKey(headers: List<String>) = stringPreferencesKey("mapping_${headers.joinToString("\u001f").hashCode().toUInt().toString(16)}")
    companion object { private val LAST_AT = longPreferencesKey("last_at"); private val LAST_FILE = stringPreferencesKey("last_file"); private val LAST_COUNT = intPreferencesKey("last_count"); private val LAST_SUCCESS = stringPreferencesKey("last_success"); private val MATCH = stringPreferencesKey("match"); private val BLANK = stringPreferencesKey("blank"); private val BSSID = stringPreferencesKey("bssid"); private val ERROR = stringPreferencesKey("error") }
}
