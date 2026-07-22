package com.lazyapps.wifianalyzer.domain

import java.text.Normalizer

data class Workspace(val id: Long, val name: String, val sortOrder: Int, val createdAt: Long, val updatedAt: Long)
data class WorkspaceCounts(val devices: Int, val groups: Int, val photos: Int)

object WorkspaceName {
    fun display(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
    fun normalized(value: String): String = display(value).lowercase()
}

object WorkspaceSelectionPolicy {
    fun selected(existingIds: List<Long>, requestedId: Long?): Long? = requestedId?.takeIf(existingIds::contains) ?: existingIds.firstOrNull()
    fun needsDefault(existingIds: List<Long>): Boolean = existingIds.isEmpty()
}
