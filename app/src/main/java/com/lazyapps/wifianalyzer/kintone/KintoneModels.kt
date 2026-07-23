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
}

class KintoneException(val code: KintoneErrorCode, cause: Throwable? = null) : Exception(code.name, cause)

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
