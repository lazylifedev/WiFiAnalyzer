package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.kintone.HttpsKintoneApi
import com.lazyapps.wifianalyzer.kintone.KintoneDeviceRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KintoneUpsertRequestTest {
    private val api = HttpsKintoneApi()
    private fun record(index: Int = 1, updatedAt: String = "2026-07-25T00:00:00Z", deleted: Boolean = false) = KintoneDeviceRecord(
        deviceUuid = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}", workspaceUuid = "workspace", workspaceName = "管理",
        groupUuid = "group", groupName = "グループ", deviceName = "アクセスポイント", manufacturer = "", model = "", serialNumber = "",
        ssid = "ssid", primaryBssid = "00:11:22:33:44:55", location = "", notes = "テスト\n確認", updatedAt = updatedAt, deleted = deleted,
    )

    @Test fun usesOfficialPutEndpoint() { assertEquals("PUT", HttpsKintoneApi.UPSERT_METHOD); assertEquals("/k/v1/records.json", HttpsKintoneApi.UPSERT_PATH) }

    @Test fun upsertAndKeysAreAtOfficialLevels() {
        val root = api.buildUpsertBody(287, listOf(record()))
        assertEquals(287, root["app"]!!.jsonPrimitive.content.toInt())
        assertTrue(root["upsert"]!!.jsonPrimitive.boolean)
        val item = root["records"]!!.jsonArray.single().jsonObject
        assertEquals("機器UUID", item["updateKey"]!!.jsonObject["field"]!!.jsonPrimitive.content)
        assertTrue(item["updateKey"]!!.jsonObject["value"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals(item["updateKey"]!!.jsonObject["value"], item["record"]!!.jsonObject["機器UUID"]!!.jsonObject["value"])
    }

    @Test fun supportsOneAndOneHundredButRejectsOneHundredOne() {
        assertEquals(1, api.buildUpsertBody(1, listOf(record()))["records"]!!.jsonArray.size)
        assertEquals(100, api.buildUpsertBody(1, (1..100).map(::record))["records"]!!.jsonArray.size)
        assertThrows(IllegalArgumentException::class.java) { api.buildUpsertBody(1, (1..101).map(::record)) }
    }

    @Test fun sendsSupportedValuesOnly() {
        val values = api.buildUpsertBody(1, listOf(record(deleted = false)))["records"]!!.jsonArray.single().jsonObject["record"]!!.jsonObject
        assertEquals("テスト\n確認", values["メモ"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("2026-07-25T00:00:00Z", values["アプリ更新日時"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals(JsonArray(emptyList()), values["削除状態"]!!.jsonObject["value"])
        listOf("写真", "APIトークン", "QR内容", "複数BSSID").forEach { assertFalse(values.containsKey(it)) }
        assertTrue(values.values.all { it is JsonObject })
    }

    @Test fun omitsMissingOrInvalidDatetime() {
        listOf("", "2026-07-25", "not-a-date").forEach { value ->
            val record = api.buildUpsertBody(1, listOf(record(updatedAt = value)))["records"]!!.jsonArray.single().jsonObject["record"]!!.jsonObject
            assertFalse(record.containsKey("アプリ更新日時"))
        }
    }
}
