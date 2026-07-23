package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.kintone.KINTONE_QR_MAX_BYTES
import com.lazyapps.wifianalyzer.kintone.KintoneErrorCode
import com.lazyapps.wifianalyzer.kintone.KintoneException
import com.lazyapps.wifianalyzer.kintone.KintoneQrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KintoneQrParserTest {
    private fun qr(extra: String = "", domain: String = "example.cybozu.com", appId: Long = 123, token: String = "secret") = """{
        "type":"lazyapps-wifi-analyzer-kintone","qrVersion":1,
        "productId":"lazyapps-wifi-analyzer-kintone","pluginId":"real-plugin-id","pluginVersion":"1.0.0",
        "templateId":"lazyapps-wifi-analyzer","templateVersion":1,"fieldSchemaVersion":1,
        "domain":"$domain","guestSpaceId":null,"appId":$appId,"apiToken":"$token",
        "issuedAt":"2026-07-23T15:00:00+09:00","nonce":"84f4134f-88b0-4e27-935f-736d721d63ef"$extra} """.trim()

    @Test fun parsesValidQrAndAcceptsNonFixedPluginId() {
        val value = KintoneQrParser.parse(qr())
        assertEquals("real-plugin-id", value.pluginId)
        assertEquals("example.cybozu.com", value.domain)
    }

    @Test fun unknownFieldIsIgnored() { assertEquals(123, KintoneQrParser.parse(qr(",\"future\":true")).appId) }
    @Test fun uppercaseAndTrailingDotAreNormalized() { assertEquals("example.cybozu.com", KintoneQrParser.parse(qr(domain = "EXAMPLE.CYBOZU.COM.")).domain) }

    @Test fun invalidDomainsAreRejected() {
        listOf("https://example.cybozu.com", "example.cybozu.com/path", "example.cybozu.com:443", "127.0.0.1", "localhost", "user@example.cybozu.com").forEach {
            assertCode(KintoneErrorCode.KINTONE_DOMAIN_INVALID) { KintoneQrParser.parse(qr(domain = it)) }
        }
    }

    @Test fun invalidAppAndTokenAreRejected() {
        listOf(0L, -1L).forEach { assertCode(KintoneErrorCode.KINTONE_QR_INVALID) { KintoneQrParser.parse(qr(appId = it)) } }
        assertCode(KintoneErrorCode.KINTONE_QR_INVALID) { KintoneQrParser.parse(qr(token = " ")) }
        assertCode(KintoneErrorCode.KINTONE_QR_INVALID) { KintoneQrParser.parse(qr(token = "x\\u0001")) }
    }

    @Test fun malformedDuplicateNestedAndLargeQrAreRejected() {
        assertCode(KintoneErrorCode.KINTONE_QR_INVALID) { KintoneQrParser.parse("not json") }
        assertCode(KintoneErrorCode.KINTONE_QR_INVALID) { KintoneQrParser.parse(qr(",\"appId\":5")) }
        assertCode(KintoneErrorCode.KINTONE_QR_INVALID) { KintoneQrParser.parse(qr(",\"nested\":{}")) }
        assertCode(KintoneErrorCode.KINTONE_QR_TOO_LARGE) { KintoneQrParser.parse("x".repeat(KINTONE_QR_MAX_BYTES + 1)) }
    }

    @Test fun versionProductAndTemplateAreStrict() {
        assertCode(KintoneErrorCode.KINTONE_QR_UNSUPPORTED_VERSION) { KintoneQrParser.parse(qr().replace("\"qrVersion\":1", "\"qrVersion\":2")) }
        assertCode(KintoneErrorCode.KINTONE_QR_WRONG_PRODUCT) { KintoneQrParser.parse(qr().replace("\"productId\":\"lazyapps-wifi-analyzer-kintone\"", "\"productId\":\"other\"")) }
        assertCode(KintoneErrorCode.KINTONE_QR_WRONG_TEMPLATE) { KintoneQrParser.parse(qr().replace("\"templateId\":\"lazyapps-wifi-analyzer\"", "\"templateId\":\"other\"")) }
    }

    @Test fun invalidMetadataProducesWarnings() {
        val parsed = KintoneQrParser.parse(qr().replace("1.0.0", "bad").replace("2026-07-23T15:00:00+09:00", "bad").replace("84f4134f-88b0-4e27-935f-736d721d63ef", "bad"))
        assertTrue(parsed.warnings.size >= 3)
    }

    private fun assertCode(code: KintoneErrorCode, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull() as KintoneException
        assertEquals(code, error.code)
    }
}
