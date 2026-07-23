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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface KintoneApi {
    suspend fun verify(domain: String, appId: Long, token: CharArray): KintoneVerification
}

class HttpsKintoneApi : KintoneApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun verify(domain: String, appId: Long, token: CharArray): KintoneVerification = withContext(Dispatchers.IO) {
        try {
            val fieldsBody = get(domain, "/k/v1/app/form/fields.json?app=$appId", token)
            coroutineContext.ensureActive()
            val fields = parseFields(fieldsBody)
            get(domain, "/k/v1/records.json?app=$appId&query=limit%201", token)
            KintoneVerification(fields, listOf("実テンプレートの必須フィールド定義が未提供のため、型の完全照合は未確認です"))
        } finally { token.fill('\u0000') }
    }

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

    private fun parseFields(body: String): Map<String, String> = try {
        val properties = json.parseToJsonElement(body).jsonObject["properties"]?.jsonObject
            ?: throw KintoneException(KintoneErrorCode.KINTONE_RESPONSE_INVALID)
        properties.mapValues { (_, value) -> (value as JsonObject)["type"]?.jsonPrimitive?.content ?: "" }
    } catch (e: KintoneException) { throw e }
    catch (e: Exception) { throw KintoneException(KintoneErrorCode.KINTONE_RESPONSE_INVALID, e) }
}
