package com.lazyapps.wifianalyzer.kintone

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.kintoneManualSelectionDataStore by preferencesDataStore("kintone_manual_sync_selection")

class KintoneManualSyncSelectionStore(private val context: Context) {
    private val selectedKey = stringSetPreferencesKey("kintone_manual_sync_workspace_uuids")

    suspend fun reconcile(validWorkspaceIds: List<Long>, legacySelectedId: Long): LinkedHashSet<Long> {
        if (validWorkspaceIds.isEmpty()) return linkedSetOf()
        val valid = validWorkspaceIds.toSet()
        val storedUuids = context.kintoneManualSelectionDataStore.data.first()[selectedKey].orEmpty()
        val stored = validWorkspaceIds.filterTo(linkedSetOf()) { WorkspaceUuid.fromId(it) in storedUuids }
        val reconciled = if (stored.isNotEmpty()) stored else linkedSetOf(
            legacySelectedId.takeIf { it in valid } ?: validWorkspaceIds.first(),
        )
        return write(reconciled, validWorkspaceIds)
    }

    suspend fun write(selectedIds: Set<Long>, validWorkspaceIds: List<Long>): LinkedHashSet<Long> {
        val ordered = validWorkspaceIds.filterTo(linkedSetOf()) { it in selectedIds }
        require(ordered.isNotEmpty() || validWorkspaceIds.isEmpty()) { "At least one workspace must remain selected" }
        context.kintoneManualSelectionDataStore.edit { preferences ->
            preferences[selectedKey] = ordered.mapTo(linkedSetOf(), WorkspaceUuid::fromId)
        }
        return ordered
    }
}
