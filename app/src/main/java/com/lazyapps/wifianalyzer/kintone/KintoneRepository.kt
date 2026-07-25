package com.lazyapps.wifianalyzer.kintone

import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.data.registry.KintoneConnectionEntity
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class KintoneRepository(
    private val database: WifiAnalyzerDatabase,
    private val api: KintoneApi = HttpsKintoneApi(),
    private val cipher: TokenCipher = AndroidKeystoreTokenCipher(),
) {
    private val dao = database.registryDao()

    fun observe(workspaceId: Long): Flow<KintoneConnectionSummary?> = dao.observeKintoneConnection(workspaceId).map { it?.summary() }

    suspend fun verify(payload: KintoneQrPayload): KintoneVerification = api.verify(payload.domain, payload.appId, payload.apiToken.toCharArray())

    suspend fun save(workspaceId: Long, payload: KintoneQrPayload) = database.withTransaction {
        if (dao.getWorkspace(workspaceId) == null) throw KintoneException(KintoneErrorCode.KINTONE_WORKSPACE_NOT_FOUND)
        val workspaceUuid = UUID.nameUUIDFromBytes("wifi-analyzer-workspace:$workspaceId".toByteArray()).toString()
        val encrypted = cipher.encrypt(workspaceUuid, payload.apiToken.toCharArray())
        val now = System.currentTimeMillis()
        dao.upsertKintoneConnection(KintoneConnectionEntity(
            workspaceId, workspaceUuid, payload.domain, null, payload.appId, payload.pluginId,
            payload.pluginVersion, KINTONE_TEMPLATE_ID, payload.templateVersion, payload.fieldSchemaVersion,
            encrypted.ciphertext, encrypted.iv, now, now, "CONNECTED",
        ))
    }

    suspend fun reverify(workspaceId: Long): KintoneVerification {
        val saved = dao.getKintoneConnection(workspaceId) ?: throw KintoneException(KintoneErrorCode.KINTONE_WORKSPACE_NOT_FOUND)
        return try {
            api.verify(saved.domain, saved.appId, cipher.decrypt(saved.workspaceUuid, EncryptedToken(saved.encryptedApiToken, saved.tokenIv))).also {
                dao.updateKintoneVerification(workspaceId, System.currentTimeMillis(), "CONNECTED")
            }
        } catch (error: KintoneException) {
            dao.updateKintoneVerification(workspaceId, System.currentTimeMillis(), error.code.name)
            throw error
        }
    }

    suspend fun disconnect(workspaceId: Long) = dao.deleteKintoneConnection(workspaceId)
    suspend fun hasDuplicateTarget(workspaceId: Long, domain: String, appId: Long) = dao.countOtherKintoneConnections(domain, appId, workspaceId) > 0

    suspend fun buildSyncRecords(workspaceId: Long): List<KintoneDeviceRecord> {
        val workspace = dao.getWorkspace(workspaceId) ?: throw KintoneException(KintoneErrorCode.KINTONE_WORKSPACE_NOT_FOUND)
        val groups = dao.getGroupsOnce(workspaceId).associateBy { it.id }
        return dao.getDevicesOnce(workspaceId).map { device ->
            val uuid = UUID.nameUUIDFromBytes("wifi-analyzer-device:$workspaceId:${device.id}".toByteArray()).toString()
            val group = device.groupId?.let(groups::get)
            KintoneDeviceRecord(uuid, UUID.nameUUIDFromBytes("wifi-analyzer-workspace:$workspaceId".toByteArray()).toString(), workspace.name,
                group?.let { UUID.nameUUIDFromBytes("wifi-analyzer-group:${workspaceId}:${it.id}".toByteArray()).toString() }.orEmpty(), group?.name.orEmpty(), device.displayName,
                device.manufacturer, device.model, device.serialNumber, device.ssid, device.primaryBssid, device.location, device.notes,
                Instant.ofEpochMilli(device.updatedAt).toString(), !device.isEnabled)
        }
    }

    suspend fun buildSyncRecordsForConnection(workspaceId: Long): List<KintoneDeviceRecord> {
        val connection = dao.getKintoneConnection(workspaceId)
            ?: throw KintoneException(KintoneErrorCode.KINTONE_WORKSPACE_NOT_FOUND)
        val connectedWorkspaceId = dao.getWorkspacesOnce()
            .firstOrNull { WorkspaceUuid.fromId(it.id) == connection.workspaceUuid }
            ?.id ?: throw KintoneException(KintoneErrorCode.KINTONE_WORKSPACE_NOT_FOUND)
        return buildSyncRecords(connectedWorkspaceId)
    }

    suspend fun previewSync(workspaceId: Long): KintoneSyncPreview {
        val records = buildSyncRecords(workspaceId)
        val errors = records.flatMap { r -> buildList { if (r.deviceUuid.isBlank()) add("device UUID is blank"); if (r.primaryBssid.isBlank()) add("primary BSSID is blank") } }
        val duplicateCount = records.groupingBy { it.deviceUuid }.eachCount().count { it.value > 1 }
        return KintoneSyncPreview(records.size, records.size - errors.size - duplicateCount, errors + if (duplicateCount > 0) listOf("duplicate device UUID") else emptyList(), records.filter { it.ssid.isBlank() || it.groupName.isBlank() }.map { "optional value is not set" })
    }

    suspend fun sync(workspaceId: Long, records: List<KintoneDeviceRecord>, onProgress: (Int, Int) -> Unit = { _, _ -> }): KintoneSyncResult {
        if (records.isEmpty()) return KintoneSyncResult(0, 0, 0, 0, emptyList())
        val connection = dao.getKintoneConnection(workspaceId) ?: throw KintoneException(KintoneErrorCode.KINTONE_WORKSPACE_NOT_FOUND)
        val token = cipher.decrypt(connection.workspaceUuid, EncryptedToken(connection.encryptedApiToken, connection.tokenIv))
        val batches = records.chunked(KINTONE_RECORD_BATCH_SIZE); val results = mutableListOf<KintoneBatchResult>(); var success = 0; var failed = 0
        try { batches.forEachIndexed { index, batch ->
            try { api.upsert(connection.domain, connection.appId, token.copyOf(), batch); success += batch.size; results += KintoneBatchResult(index + 1, batch.size, 0) }
            catch (e: KintoneException) {
                if (batch.size > 1 && e.category == KintoneErrorCategory.VALIDATION) {
                    var singleSuccess = 0
                    batch.forEachIndexed { recordIndex, record ->
                        try {
                            api.upsert(connection.domain, connection.appId, token.copyOf(), listOf(record)); success++; singleSuccess++
                        } catch (single: KintoneException) {
                            failed++
                            results += KintoneBatchResult(index + 1, 0, 1, single.code, single.category, single.httpStatus, single.kintoneErrorCode, single.userMessage, single.validationErrors, recordIndex)
                        }
                    }
                    if (singleSuccess > 0) results += KintoneBatchResult(index + 1, singleSuccess, 0)
                } else {
                    failed += batch.size; results += KintoneBatchResult(index + 1, 0, batch.size, e.code, e.category, e.httpStatus, e.kintoneErrorCode, e.userMessage, e.validationErrors)
                }
            }
            onProgress(index + 1, batches.size)
        } } finally { token.fill('\u0000') }
        return KintoneSyncResult(records.size, success, failed, 0, results)
    }

    private fun KintoneConnectionEntity.summary() = KintoneConnectionSummary(workspaceId, domain, appId, pluginVersion, templateVersion, fieldSchemaVersion, connectedAt, lastVerifiedAt, lastVerificationStatus)
}
