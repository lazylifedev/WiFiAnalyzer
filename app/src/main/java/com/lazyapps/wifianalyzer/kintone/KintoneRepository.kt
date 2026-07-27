package com.lazyapps.wifianalyzer.kintone

import android.content.Context
import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.data.registry.KintoneConnectionEntity
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ensureActive

class KintoneRepository(
    private val database: WifiAnalyzerDatabase,
    private val api: KintoneApi = HttpsKintoneApi(),
    private val cipher: TokenCipher = AndroidKeystoreTokenCipher(),
    context: Context? = null,
    private val photoStore: KintonePhotoSyncStore? = context?.let(::KintonePhotoSyncStore),
) {
    private val dao = database.registryDao()
    private val filesDir = context?.applicationContext?.filesDir

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
        photoStore?.removeWorkspace(workspaceUuid)
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

    suspend fun disconnect(workspaceId: Long) {
        dao.getKintoneConnection(workspaceId)?.workspaceUuid?.let { photoStore?.removeWorkspace(it) }
        dao.deleteKintoneConnection(workspaceId)
    }
    suspend fun hasDuplicateTarget(workspaceId: Long, domain: String, appId: Long) = dao.countOtherKintoneConnections(domain, appId, workspaceId) > 0

    suspend fun workspaceOptions(autoSyncStore: KintoneAutoSyncStore): List<KintoneWorkspaceOption> = dao.getWorkspacesOnce().map { workspace ->
        KintoneWorkspaceOption(workspace.id, workspace.name, dao.countDevices(workspace.id), dao.getKintoneConnection(workspace.id) != null, autoSyncStore.read(WorkspaceUuid.fromId(workspace.id)).enabled)
    }

    suspend fun buildSyncRecords(workspaceId: Long): List<KintoneDeviceRecord> {
        val workspace = dao.getWorkspace(workspaceId) ?: throw KintoneException(KintoneErrorCode.KINTONE_WORKSPACE_NOT_FOUND)
        val groups = dao.getGroupsOnce(workspaceId).associateBy { it.id }
        return dao.getDevicesOnce(workspaceId).map { device ->
            val uuid = UUID.nameUUIDFromBytes("wifi-analyzer-device:$workspaceId:${device.id}".toByteArray()).toString()
            val group = device.groupId?.let(groups::get)
            KintoneDeviceRecord(uuid, UUID.nameUUIDFromBytes("wifi-analyzer-workspace:$workspaceId".toByteArray()).toString(), workspace.name,
                group?.let { UUID.nameUUIDFromBytes("wifi-analyzer-group:${workspaceId}:${it.id}".toByteArray()).toString() }.orEmpty(), group?.name.orEmpty(), device.displayName,
                device.manufacturer, device.model, device.serialNumber, device.ssid, device.primaryBssid, device.location, device.notes,
                Instant.ofEpochMilli(device.updatedAt).toString(), !device.isEnabled, device.id)
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
        val changed = records.mapNotNull { record -> runCatching { photoPlan(workspaceId, record) }.getOrNull()?.takeIf { it.changed } }
        return KintoneSyncPreview(records.size, records.size - errors.size - duplicateCount, errors + if (duplicateCount > 0) listOf("duplicate device UUID") else emptyList(), records.filter { it.ssid.isBlank() || it.groupName.isBlank() }.map { "optional value is not set" }, changed.size, changed.sumOf { it.photos.size })
    }

    suspend fun sync(
        workspaceId: Long,
        records: List<KintoneDeviceRecord>,
        includePhotos: Boolean = true,
        onProgress: (stage: String, current: Int, total: Int) -> Unit = { _, _, _ -> },
    ): KintoneSyncResult {
        if (records.isEmpty()) return KintoneSyncResult(0, 0, 0, 0, emptyList())
        val connection = dao.getKintoneConnection(workspaceId) ?: throw KintoneException(KintoneErrorCode.KINTONE_WORKSPACE_NOT_FOUND)
        val token = cipher.decrypt(connection.workspaceUuid, EncryptedToken(connection.encryptedApiToken, connection.tokenIv))
        val prepared = mutableListOf<Pair<KintoneDeviceRecord, PhotoPlan?>>()
        val results = mutableListOf<KintoneBatchResult>(); var success = 0; var failed = 0; var uploaded = 0
        val plans = records.mapNotNull { record ->
            if (!includePhotos) record to null
            else try { record to photoPlan(workspaceId, record) }
            catch (e: KintoneException) {
                failed++
                results += KintoneBatchResult(0, 0, 1, e.code, e.category, e.httpStatus, e.kintoneErrorCode, e.userMessage)
                null
            }
        }
        val photoPlans = plans.mapNotNull { it.second?.takeIf(PhotoPlan::changed) }
        try {
            plans.forEach { (record, plan) ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (plan == null || !plan.changed) prepared += record to plan
                else try {
                    val keys = plan.photos.mapIndexed { index, photo ->
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        onProgress("写真アップロード中", uploaded + 1, photoPlans.sumOf { it.photos.size })
                        api.uploadFile(connection.domain, token.copyOf(), photo.file, safeFileName(record.deviceUuid, index))
                            .also { uploaded++ }
                    }
                    prepared += record.copy(photoFileKeys = keys) to plan
                } catch (e: KintoneException) {
                    failed++
                    results += KintoneBatchResult(0, 0, 1, e.code, e.category, e.httpStatus, e.kintoneErrorCode, e.userMessage)
                }
            }
            val batches = prepared.chunked(KINTONE_RECORD_BATCH_SIZE)
            batches.forEachIndexed { index, batchItems ->
            val batch = batchItems.map { it.first }
            var batchSucceeded = false
            try { api.upsert(connection.domain, connection.appId, token.copyOf(), batch); success += batch.size; results += KintoneBatchResult(index + 1, batch.size, 0); batchSucceeded = true }
            catch (e: KintoneException) {
                if (batch.size > 1 && e.category == KintoneErrorCategory.VALIDATION) {
                    var singleSuccess = 0
                    batchItems.forEachIndexed { recordIndex, item ->
                        try {
                            api.upsert(connection.domain, connection.appId, token.copyOf(), listOf(item.first)); success++; singleSuccess++
                            item.second?.takeIf { it.changed }?.let(::saveFingerprint)
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
            if (batchSucceeded) batchItems.forEach { it.second?.takeIf { plan -> plan.changed }?.let(::saveFingerprint) }
            onProgress("機器送信中", index + 1, batches.size)
        } } finally { token.fill('\u0000') }
        return KintoneSyncResult(records.size, success, failed, 0, results, photoPlans.size, photoPlans.sumOf { it.photos.size }, uploaded)
    }

    private data class PhotoPlan(val workspaceUuid: String, val deviceUuid: String, val fingerprint: String, val photos: List<KintonePhotoCandidate>, val changed: Boolean)

    private suspend fun photoPlan(workspaceId: Long, record: KintoneDeviceRecord): PhotoPlan {
        val store = photoStore ?: return PhotoPlan(record.workspaceUuid, record.deviceUuid, "", emptyList(), false)
        val root = filesDir ?: return PhotoPlan(record.workspaceUuid, record.deviceUuid, "", emptyList(), false)
        val entities = dao.getPhotos(record.localDeviceId).take(9)
        val photos = entities.map { photo ->
            if (photo.mimeType != "image/jpeg") throw KintoneException(KintoneErrorCode.KINTONE_FILE_INVALID)
            val file = java.io.File(root, "devices/$workspaceId/${record.localDeviceId}/photos/${photo.fileName}")
            if (!file.exists()) throw KintoneException(KintoneErrorCode.KINTONE_FILE_NOT_FOUND)
            if (!file.isFile || !file.canRead()) throw KintoneException(KintoneErrorCode.KINTONE_FILE_UNREADABLE)
            if (file.length() <= 0) throw KintoneException(KintoneErrorCode.KINTONE_FILE_INVALID)
            val jpeg = runCatching { file.inputStream().use { it.read() == 0xff && it.read() == 0xd8 } }.getOrDefault(false)
            if (!jpeg) throw KintoneException(KintoneErrorCode.KINTONE_FILE_INVALID)
            val hash = try { KintonePhotoFingerprint.sha256(file) } catch (e: Exception) { throw KintoneException(KintoneErrorCode.KINTONE_PHOTO_FINGERPRINT_FAILED, e) }
            KintonePhotoCandidate(photo, file, hash)
        }
        val fingerprint = KintonePhotoFingerprint.create(photos)
        val previous = store.read(record.workspaceUuid, record.deviceUuid)
        return PhotoPlan(record.workspaceUuid, record.deviceUuid, fingerprint, photos, previous != fingerprint && (photos.isNotEmpty() || previous != null))
    }

    private fun saveFingerprint(plan: PhotoPlan) { photoStore?.write(plan.workspaceUuid, plan.deviceUuid, plan.fingerprint) }
    private fun safeFileName(deviceUuid: String, index: Int) = "wifi-device-${deviceUuid.filter(Char::isLetterOrDigit).take(8).ifBlank { "unknown" }}-${(index + 1).toString().padStart(2, '0')}.jpg"

    private fun KintoneConnectionEntity.summary() = KintoneConnectionSummary(workspaceId, domain, appId, pluginVersion, templateVersion, fieldSchemaVersion, connectedAt, lastVerifiedAt, lastVerificationStatus)
}
