package com.lazyapps.wifianalyzer.kintone

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import java.time.Instant
import android.util.Log
import com.lazyapps.wifianalyzer.BuildConfig

interface KintoneApi {
    suspend fun verify(domain: String, appId: Long, token: CharArray): KintoneVerification
    suspend fun upsert(domain: String, appId: Long, token: CharArray, records: List<KintoneDeviceRecord>)
}

class HttpsKintoneApi : KintoneApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun verify(domain: String, appId: Long, token: CharArray): KintoneVerification = withContext(Dispatchers.IO) {
        try {
            val fieldsBody = get(domain, "/k/v1/app/form/fields.json?app=$appId", token)
            coroutineContext.ensureActive()
            val fields = parseFields(fieldsBody)
            val verification = KintoneFieldSchemaV1.validate(fields)
            get(domain, "/k/v1/records.json?app=$appId&query=limit%201", token)
            verification
        } finally { token.fill('\u0000') }
    }

    override suspend fun upsert(domain: String, appId: Long, token: CharArray, records: List<KintoneDeviceRecord>) = withContext(Dispatchers.IO) {
        require(records.size <= KINTONE_RECORD_BATCH_SIZE)
        val body = buildUpsertBody(appId, records)
        try { putRecords(domain, UPSERT_PATH, token, body.toString()) }
        catch (failure: KintoneException) {
            if (BuildConfig.DEBUG) Log.w("KintoneSync", "Kintone sync failed: category=${failure.category} httpStatus=${failure.httpStatus ?: "none"} code=${failure.kintoneErrorCode ?: "none"} batchSize=${records.size}")
            if (BuildConfig.DEBUG) failure.validationErrors.forEach { detail ->
                detail.messages.forEach { message -> Log.w("KintoneSync", "Kintone validation error: path=${detail.path} message=$message") }
            }
            throw failure
        }
        finally { token.fill('\u0000') }
    }

    internal fun buildUpsertBody(appId: Long, records: List<KintoneDeviceRecord>) = buildJsonObject {
            require(records.size <= KINTONE_RECORD_BATCH_SIZE)
            put("app", appId); put("upsert", true)
            put("records", buildJsonArray { records.forEach { item -> add(buildJsonObject {
                put("updateKey", buildJsonObject { put("field", "機器UUID"); put("value", item.deviceUuid) })
                put("record", buildJsonObject {
                    put("機器UUID", value(item.deviceUuid)); put("ワークスペースUUID", value(item.workspaceUuid)); put("ワークスペース名", value(item.workspaceName))
                    put("グループUUID", value(item.groupUuid)); put("グループ名", value(item.groupName)); put("機器名", value(item.deviceName))
                    put("メーカー", value(item.manufacturer)); put("機種", value(item.model)); put("シリアル番号", value(item.serialNumber)); put("SSID", value(item.ssid))
                    put("主BSSID", value(item.primaryBssid)); put("設置場所", value(item.location)); put("メモ", value(item.notes))
                    item.updatedAt.takeIf(::isUtcInstant)?.let { put("アプリ更新日時", value(it)) }
                    put("削除状態", buildJsonObject { put("value", buildJsonArray { if (item.deleted) add("削除済") }) })
                })
            }) } })
        }

    private fun value(value: String) = buildJsonObject { put("value", value) }
    private fun isUtcInstant(value: String) = value.isNotBlank() && runCatching { Instant.parse(value) }.isSuccess

    private fun get(domain: String, path: String, token: CharArray): String {
        val connection = (URL("https", domain, path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("X-Cybozu-API-Token", String(token))
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            if (status in 300..399) throw KintoneException(KintoneErrorCode.KINTONE_RESPONSE_INVALID)
            if (status !in 200..299) throw KintoneException(when (status) {
                401 -> KintoneErrorCode.KINTONE_AUTH_FAILED
                403 -> KintoneErrorCode.KINTONE_PERMISSION_DENIED
                404 -> KintoneErrorCode.KINTONE_APP_NOT_FOUND
                429 -> KintoneErrorCode.KINTONE_RATE_LIMITED
                in 500..599 -> KintoneErrorCode.KINTONE_SERVER_ERROR
                else -> KintoneErrorCode.KINTONE_RESPONSE_INVALID
            })
            return connection.inputStream.bufferedReader().use { it.readText().take(512 * 1024) }
        } catch (e: KintoneException) { throw e }
        catch (e: SocketTimeoutException) { throw KintoneException(KintoneErrorCode.KINTONE_TIMEOUT, e) }
        catch (e: SSLException) { throw KintoneException(KintoneErrorCode.KINTONE_TLS_ERROR, e) }
        catch (e: IOException) { throw KintoneException(KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE, e) }
        finally { connection.disconnect() }
    }

    private fun putRecords(domain: String, path: String, token: CharArray, body: String) {
        val connection = (URL("https", domain, path).openConnection() as HttpURLConnection).apply {
            requestMethod = UPSERT_METHOD; connectTimeout = 10_000; readTimeout = 30_000; instanceFollowRedirects = false; doOutput = true
            setRequestProperty("X-Cybozu-API-Token", String(token)); setRequestProperty("Content-Type", "application/json")
        }
        try { connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }; val status = connection.responseCode
            if (status !in 200..299) {
                val remote = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText().take(64 * 1024) }
                        ?.let(::parseErrorResponse)
                }.getOrNull()
                val code = when (status) { 401 -> KintoneErrorCode.KINTONE_AUTH_FAILED; 403 -> KintoneErrorCode.KINTONE_PERMISSION_DENIED; 404 -> KintoneErrorCode.KINTONE_APP_NOT_FOUND; 429 -> KintoneErrorCode.KINTONE_RATE_LIMITED; in 500..599 -> KintoneErrorCode.KINTONE_SERVER_ERROR; else -> KintoneErrorCode.KINTONE_BATCH_FAILED }
                throw KintoneException(code, httpStatus = status, kintoneErrorCode = remote?.first, validationErrors = remote?.second.orEmpty())
            }
        } catch (e: KintoneException) { throw e } catch (e: SocketTimeoutException) { throw KintoneException(KintoneErrorCode.KINTONE_TIMEOUT, e) } catch (e: SSLException) { throw KintoneException(KintoneErrorCode.KINTONE_TLS_ERROR, e) } catch (e: IOException) { throw KintoneException(KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE, e) } finally { connection.disconnect() }
    }

    internal fun parseErrorResponse(body: String): Pair<String?, List<KintoneValidationError>> {
        val root = json.parseToJsonElement(body).jsonObject
        val errors = (root["errors"] as? JsonObject).orEmpty().mapNotNull { (path, detail) ->
            val messages = ((detail as? JsonObject)?.get("messages") as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            KintoneValidationError(path, messages).takeIf { messages.isNotEmpty() }
        }
        return root["code"]?.jsonPrimitive?.contentOrNull to errors
    }

    internal fun parseFields(body: String): Map<String, KintoneFieldProperty> = try {
        val properties = json.parseToJsonElement(body).jsonObject["properties"]?.jsonObject
            ?: throw KintoneException(KintoneErrorCode.KINTONE_RESPONSE_INVALID)
        properties.mapValues { (mapCode, value) ->
            val field = value as? JsonObject ?: throw KintoneException(KintoneErrorCode.KINTONE_RESPONSE_INVALID)
            val code = field.string("code") ?: mapCode
            KintoneFieldProperty(
                code = code,
                label = field.string("label") ?: code,
                type = field.string("type") ?: "",
                required = field.boolean("required"),
                unique = field.boolean("unique"),
                maxLength = field["maxLength"]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() },
                defaultNowValue = field.boolean("defaultNowValue"),
                options = (field["options"] as? JsonObject)?.keys.orEmpty(),
            )
        }
    } catch (e: KintoneException) { throw e }
    catch (e: Exception) { throw KintoneException(KintoneErrorCode.KINTONE_RESPONSE_INVALID, e) }

    private fun JsonObject.string(name: String) = get(name)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.boolean(name: String) = get(name)?.jsonPrimitive?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

    internal companion object { const val UPSERT_METHOD = "PUT"; const val UPSERT_PATH = "/k/v1/records.json" }
}

object KintoneFieldSchemaV1 {
    private const val DEVICE_UUID_LENGTH = 36
    private val requiredFields = linkedMapOf(
        "機器UUID" to "SINGLE_LINE_TEXT", "ワークスペースUUID" to "SINGLE_LINE_TEXT",
        "ワークスペース名" to "SINGLE_LINE_TEXT", "グループUUID" to "SINGLE_LINE_TEXT",
        "グループ名" to "SINGLE_LINE_TEXT", "機器名" to "SINGLE_LINE_TEXT",
        "メーカー" to "SINGLE_LINE_TEXT", "型番" to "SINGLE_LINE_TEXT",
        "シリアル番号" to "SINGLE_LINE_TEXT", "SSID" to "SINGLE_LINE_TEXT",
        "主BSSID" to "SINGLE_LINE_TEXT", "設置場所" to "SINGLE_LINE_TEXT",
        "メモ" to "MULTI_LINE_TEXT", "アプリ更新日時" to "DATETIME", "削除状態" to "CHECK_BOX",
    )
    private const val PHOTO_CODE = "写真"
    private val systemFieldCodes = setOf(
        "\$id", "\$revision", "レコード番号", "作成者", "更新者", "作成日時", "更新日時",
        "ステータス", "作業者", "カテゴリー",
    )

    fun validate(fields: Map<String, KintoneFieldProperty>): KintoneVerification {
        requiredFields.forEach { (code, type) ->
            val field = fields[code] ?: mismatch(KintoneErrorCode.KINTONE_REQUIRED_FIELD_MISSING)
            if (field.code != code || field.type != type) mismatch(KintoneErrorCode.KINTONE_FIELD_TYPE_MISMATCH)
        }
        val uuid = fields.getValue("機器UUID")
        if (uuid.required != true) mismatch(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH)
        if (uuid.unique == false) mismatch(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH)
        if (uuid.maxLength != null && uuid.maxLength < DEVICE_UUID_LENGTH) mismatch(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH)
        if ("削除済" !in fields.getValue("削除状態").options) mismatch(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH)

        val warnings = buildList {
            if (uuid.unique == null) add("APIから重複禁止設定を確認できませんでした。テンプレートどおりの設定を確認してください")
            if (fields.getValue("アプリ更新日時").defaultNowValue != true) add("アプリ更新日時の初期値が現在日時ではありません")
            val photo = fields[PHOTO_CODE]
            if (photo == null) add("写真同期は利用できません")
            else if (photo.code != PHOTO_CODE || photo.type != "FILE") mismatch(KintoneErrorCode.KINTONE_FIELD_TYPE_MISMATCH)
        }
        val hasUserFields = fields.keys.any { it !in requiredFields && it != PHOTO_CODE && it !in systemFieldCodes }
        val information = if (hasUserFields) listOf("連携対象外の追加フィールドがあります。同期には影響しません。") else emptyList()
        return KintoneVerification(fields.mapValues { it.value.type }, warnings, information)
    }

    private fun mismatch(code: KintoneErrorCode): Nothing = throw KintoneException(code)
}
