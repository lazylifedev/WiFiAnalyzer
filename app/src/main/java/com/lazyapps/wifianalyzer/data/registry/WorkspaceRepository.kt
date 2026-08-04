package com.lazyapps.wifianalyzer.data.registry

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.domain.Workspace
import com.lazyapps.wifianalyzer.domain.WorkspaceCounts
import com.lazyapps.wifianalyzer.domain.WorkspaceName
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private val Context.workspaceDataStore by preferencesDataStore("workspace_selection")

data class WorkspaceSnapshot(val workspaces: List<Workspace>, val selectedId: Long, val selected: Workspace?)

class WorkspaceRepository(private val context: Context, private val database: WifiAnalyzerDatabase) {
    private val dao = database.registryDao()
    private val selectedKey = longPreferencesKey("selected_workspace_id")

    val snapshot: Flow<WorkspaceSnapshot> = combine(dao.observeWorkspaces(), context.workspaceDataStore.data) { entities, prefs ->
        val workspaces = entities.map { Workspace(it.id, it.name, it.sortOrder, it.createdAt, it.updatedAt) }
        val requested = prefs[selectedKey]
        val selected = workspaces.firstOrNull { it.id == requested } ?: workspaces.firstOrNull()
        WorkspaceSnapshot(workspaces, selected?.id ?: 0, selected)
    }

    suspend fun ensureUsable(): Long {
        val id = database.withTransaction {
            val existing = dao.getWorkspacesOnce()
            existing.firstOrNull()?.id ?: createDefaultLocked()
        }
        val current = context.workspaceDataStore.data.first()[selectedKey]
        if (dao.getWorkspace(current ?: -1) == null) select(id)
        return id
    }

    suspend fun select(id: Long) {
        if (dao.getWorkspace(id) == null) throw RegistryValidationException(RegistryError.WORKSPACE_NOT_FOUND)
        context.workspaceDataStore.edit { it[selectedKey] = id }
    }

    suspend fun create(name: String, access: FeatureAccessPolicy = FeatureAccessPolicy.from(com.lazyapps.wifianalyzer.billing.ProEntitlementState.Pro)): Long = database.withTransaction {
        val display = WorkspaceName.display(name)
        val normalized = WorkspaceName.normalized(name)
        if (normalized.isBlank()) throw RegistryValidationException(RegistryError.WORKSPACE_NAME_REQUIRED)
        val all = dao.getWorkspacesOnce()
        if (!access.workspaceDecision(all.size).allowed) throw RegistryValidationException(RegistryError.WORKSPACE_LIMIT)
        if (all.any { it.normalizedName == normalized }) throw RegistryValidationException(RegistryError.DUPLICATE_WORKSPACE)
        val now = System.currentTimeMillis()
        dao.insertWorkspace(WorkspaceEntity(name = display, normalizedName = normalized, sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = now, updatedAt = now))
    }

    suspend fun rename(id: Long, name: String) = database.withTransaction {
        val current = dao.getWorkspace(id) ?: throw RegistryValidationException(RegistryError.WORKSPACE_NOT_FOUND)
        val display = WorkspaceName.display(name)
        val normalized = WorkspaceName.normalized(name)
        if (normalized.isBlank()) throw RegistryValidationException(RegistryError.WORKSPACE_NAME_REQUIRED)
        if (dao.getWorkspacesOnce().any { it.id != id && it.normalizedName == normalized }) throw RegistryValidationException(RegistryError.DUPLICATE_WORKSPACE)
        dao.updateWorkspace(current.copy(name = display, normalizedName = normalized, updatedAt = System.currentTimeMillis()))
    }

    suspend fun counts(id: Long) = WorkspaceCounts(dao.countDevices(id), dao.countGroups(id), dao.countPhotos(id))

    suspend fun move(id: Long, direction: Int) = database.withTransaction {
        val all = dao.getWorkspacesOnce()
        val index = all.indexOfFirst { it.id == id }
        val other = index + direction
        if (index !in all.indices || other !in all.indices) return@withTransaction
        val now = System.currentTimeMillis()
        dao.updateWorkspaceOrder(all[index].id, all[other].sortOrder, now)
        dao.updateWorkspaceOrder(all[other].id, all[index].sortOrder, now)
    }

    suspend fun delete(id: Long): Pair<Long, List<String>> {
        val pending = mutableListOf<String>()
        val next = database.withTransaction {
            if (dao.getWorkspace(id) == null) throw RegistryValidationException(RegistryError.WORKSPACE_NOT_FOUND)
            dao.getPhotosForWorkspace(id).forEach { photo ->
                val path = "devices/${photo.workspaceId}/${photo.deviceId}/photos/${photo.fileName}"
                pending += path; dao.insertPendingDeletion(PendingFileDeletionEntity(path, System.currentTimeMillis()))
            }
            dao.deleteWorkspace(id)
            dao.getWorkspacesOnce().firstOrNull()?.id ?: createDefaultLocked()
        }
        select(next)
        pending.forEach { path -> val file = java.io.File(context.filesDir, path); if (!file.exists() || file.delete()) dao.deletePendingDeletion(path) }
        return next to pending
    }

    private suspend fun createDefaultLocked(): Long {
        val now = System.currentTimeMillis()
        return dao.insertWorkspace(WorkspaceEntity(name = "default", normalizedName = "default", sortOrder = 0, createdAt = now, updatedAt = now))
    }
}
