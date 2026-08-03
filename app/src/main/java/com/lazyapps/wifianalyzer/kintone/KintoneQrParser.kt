package com.lazyapps.wifianalyzer.kintone

import java.net.IDN
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object KintoneQrParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false; explicitNulls = false }
    private val semVer = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$")
    private val key = Regex("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"\\s*:")

    fun parse(raw: String): KintoneQrPayload {
        if (raw.toByteArray(Charsets.UTF_8).size > KINTONE_QR_MAX_BYTES) fail(KintoneErrorCode.KINTONE_QR_TOO_LARGE)
        rejectNestedAndDuplicateKeys(raw)
        val wire = try { json.decodeFromString<KintoneQrWire>(raw) } catch (_: SerializationException) { fail(KintoneErrorCode.KINTONE_QR_INVALID) }
        if (wire.type != KINTONE_PRODUCT_ID) fail(KintoneErrorCode.KINTONE_QR_INVALID)
        if (wire.productId != KINTONE_PRODUCT_ID) fail(KintoneErrorCode.KINTONE_QR_WRONG_PRODUCT)
        if (wire.qrVersion != 1) fail(KintoneErrorCode.KINTONE_QR_UNSUPPORTED_VERSION)
        if (wire.templateId != KINTONE_TEMPLATE_ID) fail(KintoneErrorCode.KINTONE_QR_WRONG_TEMPLATE)
        if (wire.templateVersion != 1 || wire.fieldSchemaVersion != 1) fail(KintoneErrorCode.KINTONE_QR_UNSUPPORTED_VERSION)
        val domain = normalizeDomain(wire.domain ?: "")
        val appId = wire.appId?.takeIf { it >= 1 } ?: fail(KintoneErrorCode.KINTONE_QR_INVALID)
        val token = wire.apiToken?.trim()?.takeIf { it.isNotEmpty() && it.none(Char::isISOControl) } ?: fail(KintoneErrorCode.KINTONE_QR_INVALID)
        val pluginId = wire.pluginId?.trim()?.takeIf(String::isNotEmpty) ?: fail(KintoneErrorCode.KINTONE_QR_INVALID)
        val pluginVersion = wire.pluginVersion?.trim()?.takeIf(String::isNotEmpty) ?: fail(KintoneErrorCode.KINTONE_QR_INVALID)
        val warnings = mutableListOf<String>()
        if (!semVer.matches(pluginVersion)) warnings += "PLUGIN_VERSION_INVALID"
        val issuedAt = wire.issuedAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrElse { warnings += "ISSUED_AT_INVALID"; null } }
        val nonce = wire.nonce?.also { if (runCatching { UUID.fromString(it) }.isFailure) warnings += "NONCE_INVALID" }
        if (wire.guestSpaceId != null) fail(KintoneErrorCode.KINTONE_GUEST_SPACE_UNSUPPORTED)
        return KintoneQrPayload(domain, appId, token, pluginId, pluginVersion, 1, 1, issuedAt, nonce, warnings)
    }

    fun normalizeDomain(value: String): String {
        val trimmed = value.trim().removeSuffix(".").lowercase()
        if (trimmed.isEmpty() || trimmed != value.trim().removeSuffix(".").lowercase() || trimmed.any { it.isWhitespace() || it.isISOControl() }) fail(KintoneErrorCode.KINTONE_DOMAIN_INVALID)
        if (trimmed.contains(Regex("[:/@?#]")) || trimmed == "localhost") fail(KintoneErrorCode.KINTONE_DOMAIN_INVALID)
        val ascii = runCatching { IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES) }.getOrElse { fail(KintoneErrorCode.KINTONE_DOMAIN_INVALID) }
        if (ascii.length > 253 || ascii.split('.').any { it.isEmpty() || it.length > 63 }) fail(KintoneErrorCode.KINTONE_DOMAIN_INVALID)
        if (!(ascii.endsWith(".cybozu.com") || ascii.endsWith(".kintone.com"))) fail(KintoneErrorCode.KINTONE_DOMAIN_INVALID)
        return ascii
    }

    private fun rejectNestedAndDuplicateKeys(raw: String) {
        val trimmed = raw.trim()
        if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) fail(KintoneErrorCode.KINTONE_QR_INVALID)
        var quoted = false; var escaped = false; var depth = 0
        for (c in trimmed) {
            if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
            else if (c == '"') quoted = true else if (c == '{') { depth++; if (depth > 1) fail(KintoneErrorCode.KINTONE_QR_INVALID) }
            else if (c == '}') depth-- else if (c == '[' || c == ']') fail(KintoneErrorCode.KINTONE_QR_INVALID)
        }
        if (quoted || depth != 0) fail(KintoneErrorCode.KINTONE_QR_INVALID)
        val keys = key.findAll(trimmed).map { it.groupValues[1] }.toList()
        if (keys.size != keys.distinct().size) fail(KintoneErrorCode.KINTONE_QR_INVALID)
    }

    private fun fail(code: KintoneErrorCode): Nothing = throw KintoneException(code)
}
