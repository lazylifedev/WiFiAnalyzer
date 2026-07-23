package com.lazyapps.wifianalyzer.kintone

import androidx.room.withTransaction
import com.lazyapps.wifianalyzer.data.registry.KintoneConnectionEntity
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import java.util.UUID
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

    private fun KintoneConnectionEntity.summary() = KintoneConnectionSummary(workspaceId, domain, appId, pluginVersion, templateVersion, fieldSchemaVersion, connectedAt, lastVerifiedAt, lastVerificationStatus)
}
