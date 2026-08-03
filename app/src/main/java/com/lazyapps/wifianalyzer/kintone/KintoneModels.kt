package com.lazyapps.wifianalyzer.kintone

import kotlinx.serialization.Serializable

const val KINTONE_QR_MAX_BYTES = 8 * 1024
const val KINTONE_PRODUCT_ID = "lazyapps-wifi-analyzer-kintone"
const val KINTONE_TEMPLATE_ID = "lazyapps-wifi-analyzer"

enum class KintoneErrorCode {
    KINTONE_QR_INVALID, KINTONE_QR_TOO_LARGE, KINTONE_QR_UNSUPPORTED_VERSION,
    KINTONE_QR_WRONG_PRODUCT, KINTONE_QR_WRONG_TEMPLATE, KINTONE_DOMAIN_INVALID,
    KINTONE_GUEST_SPACE_UNSUPPORTED, KINTONE_NETWORK_UNAVAILABLE, KINTONE_TIMEOUT,
    KINTONE_TLS_ERROR, KINTONE_AUTH_FAILED, KINTONE_APP_NOT_FOUND,
    KINTONE_PERMISSION_DENIED, KINTONE_SCHEMA_MISMATCH, KINTONE_REQUIRED_FIELD_MISSING,
    KINTONE_FIELD_TYPE_MISMATCH, KINTONE_RATE_LIMITED, KINTONE_SERVER_ERROR,
    KINTONE_RESPONSE_INVALID, KINTONE_SECURE_STORAGE_FAILED,
    KINTONE_CONNECTION_CANCELLED, KINTONE_PRO_REQUIRED, KINTONE_WORKSPACE_NOT_FOUND,
    KINTONE_NO_DEVICES, KINTONE_VALIDATION_FAILED, KINTONE_DUPLICATE_UUID,
    KINTONE_SCHEMA_CHANGED, KINTONE_BATCH_FAILED, KINTONE_PARTIALLY_COMPLETED,
    KINTONE_FILE_NOT_FOUND, KINTONE_FILE_UNREADABLE, KINTONE_FILE_INVALID,
    KINTONE_FILE_UPLOAD_FAILED, KINTONE_FILE_UPLOAD_TIMEOUT, KINTONE_FILE_TOO_LARGE,
    KINTONE_FILE_RESPONSE_INVALID, KINTONE_PHOTO_SYNC_FAILED,
    KINTONE_PHOTO_PARTIAL_UPLOAD, KINTONE_PHOTO_FINGERPRINT_FAILED,
}

enum class KintoneErrorCategory { AUTHENTICATION, PERMISSION, NOT_FOUND, VALIDATION, SCHEMA, RATE_LIMIT, SERVER, NETWORK, TIMEOUT, UNKNOWN }

class KintoneException(
    val code: KintoneErrorCode,
    cause: Throwable? = null,
    val httpStatus: Int? = null,
    val kintoneErrorCode: String? = null,
    val validationErrors: List<KintoneValidationError> = emptyList(),
    val userMessage: String = KintoneErrorMessages.forFailure(httpStatus, kintoneErrorCode, code),
) : Exception(code.name, cause) {
    val category = KintoneErrorMessages.category(httpStatus, kintoneErrorCode, code)
}

data class KintoneValidationError(
    val path: String,
    val messages: List<String>,
) {
    val recordIndex: Int? = Regex("records\\[(\\d+)]").find(path)?.groupValues?.get(1)?.toIntOrNull()
    val fieldCode: String? = path.substringAfter(".record.", "").substringBefore(".value").takeIf { it.isNotBlank() }
}

object KintoneErrorMessages {
    const val AUTH_INVALID = "AUTH_INVALID"
    const val AUTH_PERMISSION = "AUTH_PERMISSION"
    const val APP_NOT_FOUND = "APP_NOT_FOUND"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val FIELD_MISMATCH = "FIELD_MISMATCH"
    const val UPSERT_MISMATCH = "UPSERT_MISMATCH"
    const val SCHEMA_CHANGED = "SCHEMA_CHANGED"
    const val TIMEOUT = "TIMEOUT"
    const val PHOTO_UPLOAD_TIMEOUT = "PHOTO_UPLOAD_TIMEOUT"
    const val PHOTO_UNREADABLE = "PHOTO_UNREADABLE"
    const val PHOTO_SYNC_FAILED = "PHOTO_SYNC_FAILED"
    const val NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE"
    const val SYNC_FAILED = "SYNC_FAILED"
    fun category(status: Int?, remoteCode: String?, code: KintoneErrorCode): KintoneErrorCategory = when {
        status == 401 || code == KintoneErrorCode.KINTONE_AUTH_FAILED -> KintoneErrorCategory.AUTHENTICATION
        status == 403 || code == KintoneErrorCode.KINTONE_PERMISSION_DENIED -> KintoneErrorCategory.PERMISSION
        status == 404 || code == KintoneErrorCode.KINTONE_APP_NOT_FOUND -> KintoneErrorCategory.NOT_FOUND
        remoteCode == "CB_VA01" || remoteCode == "GAIA_RE20" -> KintoneErrorCategory.VALIDATION
        code in setOf(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH, KintoneErrorCode.KINTONE_SCHEMA_CHANGED, KintoneErrorCode.KINTONE_FIELD_TYPE_MISMATCH, KintoneErrorCode.KINTONE_REQUIRED_FIELD_MISSING) -> KintoneErrorCategory.SCHEMA
        status == 429 || code == KintoneErrorCode.KINTONE_RATE_LIMITED -> KintoneErrorCategory.RATE_LIMIT
        (status != null && status >= 500) || code == KintoneErrorCode.KINTONE_SERVER_ERROR -> KintoneErrorCategory.SERVER
        code == KintoneErrorCode.KINTONE_TIMEOUT -> KintoneErrorCategory.TIMEOUT
        code in setOf(KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE, KintoneErrorCode.KINTONE_TLS_ERROR) -> KintoneErrorCategory.NETWORK
        else -> KintoneErrorCategory.UNKNOWN
    }

    fun forFailure(status: Int?, remoteCode: String?, code: KintoneErrorCode): String = when {
        status == 401 || code == KintoneErrorCode.KINTONE_AUTH_FAILED -> AUTH_INVALID
        status == 403 || code == KintoneErrorCode.KINTONE_PERMISSION_DENIED -> AUTH_PERMISSION
        status == 404 || code == KintoneErrorCode.KINTONE_APP_NOT_FOUND -> APP_NOT_FOUND
        status == 429 || code == KintoneErrorCode.KINTONE_RATE_LIMITED -> RATE_LIMITED
        remoteCode == "CB_VA01" -> FIELD_MISMATCH
        remoteCode == "GAIA_RE20" -> UPSERT_MISMATCH
        category(status, remoteCode, code) == KintoneErrorCategory.SCHEMA -> SCHEMA_CHANGED
        code == KintoneErrorCode.KINTONE_TIMEOUT -> TIMEOUT
        code == KintoneErrorCode.KINTONE_FILE_UPLOAD_TIMEOUT -> PHOTO_UPLOAD_TIMEOUT
        code in setOf(KintoneErrorCode.KINTONE_FILE_NOT_FOUND, KintoneErrorCode.KINTONE_FILE_UNREADABLE, KintoneErrorCode.KINTONE_FILE_INVALID) -> PHOTO_UNREADABLE
        code in setOf(KintoneErrorCode.KINTONE_FILE_UPLOAD_FAILED, KintoneErrorCode.KINTONE_FILE_TOO_LARGE, KintoneErrorCode.KINTONE_FILE_RESPONSE_INVALID, KintoneErrorCode.KINTONE_PHOTO_SYNC_FAILED, KintoneErrorCode.KINTONE_PHOTO_PARTIAL_UPLOAD, KintoneErrorCode.KINTONE_PHOTO_FINGERPRINT_FAILED) -> PHOTO_SYNC_FAILED
        code in setOf(KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE, KintoneErrorCode.KINTONE_TLS_ERROR) -> NETWORK_UNAVAILABLE
        else -> SYNC_FAILED
    }
}

@Serializable
internal data class KintoneQrWire(
    val type: String? = null,
    val qrVersion: Int? = null,
    val productId: String? = null,
    val pluginId: String? = null,
    val pluginVersion: String? = null,
    val templateId: String? = null,
    val templateVersion: Int? = null,
    val fieldSchemaVersion: Int? = null,
    val domain: String? = null,
    val guestSpaceId: Long? = null,
    val appId: Long? = null,
    val apiToken: String? = null,
    val issuedAt: String? = null,
    val nonce: String? = null,
)

data class KintoneQrPayload(
    val domain: String,
    val appId: Long,
    val apiToken: String,
    val pluginId: String,
    val pluginVersion: String,
    val templateVersion: Int,
    val fieldSchemaVersion: Int,
    val issuedAtMillis: Long?,
    val nonce: String?,
    val warnings: List<String>,
)

data class KintoneConnectionSummary(
    val workspaceId: Long,
    val domain: String,
    val appId: Long,
    val pluginVersion: String,
    val templateVersion: Int,
    val fieldSchemaVersion: Int,
    val connectedAt: Long,
    val lastVerifiedAt: Long,
    val lastVerificationStatus: String,
)

data class KintoneVerification(
    val fields: Map<String, String>,
    val warnings: List<String> = emptyList(),
    val information: List<String> = emptyList(),
)
data class KintoneWorkspaceOption(
    val id: Long, val name: String, val deviceCount: Int,
    val connected: Boolean, val autoSyncEnabled: Boolean,
)

data class KintoneFieldProperty(
    val code: String,
    val label: String,
    val type: String,
    val required: Boolean? = null,
    val unique: Boolean? = null,
    val maxLength: Int? = null,
    val defaultNowValue: Boolean? = null,
    val options: Set<String> = emptySet(),
)

const val KINTONE_RECORD_BATCH_SIZE = 100
data class KintoneDeviceRecord(
    val deviceUuid: String, val workspaceUuid: String, val workspaceName: String,
    val groupUuid: String, val groupName: String, val deviceName: String,
    val manufacturer: String, val model: String, val serialNumber: String,
    val ssid: String, val primaryBssid: String, val location: String, val notes: String,
    val updatedAt: String, val deleted: Boolean = false,
    val localDeviceId: Long = 0,
    val photoFileKeys: List<String>? = null,
)
data class KintoneSyncPreview(
    val total: Int, val valid: Int, val errors: List<String>, val warnings: List<String>,
    val photoDeviceCount: Int = 0, val photoCount: Int = 0,
)
data class KintoneBatchResult(
    val batch: Int, val succeeded: Int, val failed: Int, val error: KintoneErrorCode? = null,
    val errorCategory: KintoneErrorCategory? = null, val httpStatus: Int? = null,
    val kintoneErrorCode: String? = null, val userMessage: String? = null,
    val validationErrors: List<KintoneValidationError> = emptyList(),
    val recordIndex: Int? = null,
)
data class KintoneSyncResult(
    val total: Int, val succeeded: Int, val failed: Int, val skipped: Int,
    val batches: List<KintoneBatchResult>, val photoDeviceCount: Int = 0,
    val photoCount: Int = 0, val uploadedPhotoCount: Int = 0,
)
