package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.kintone.KintoneErrorCategory
import com.lazyapps.wifianalyzer.kintone.KintoneErrorCode
import com.lazyapps.wifianalyzer.kintone.KintoneException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import com.lazyapps.wifianalyzer.kintone.HttpsKintoneApi
import com.lazyapps.wifianalyzer.kintone.KintoneErrorMessages

class KintoneErrorMessagesTest {
    @Test fun mapsHttpAndKintoneFailuresToStableMessageKeys() {
        val cases = listOf(
            Triple(401, null, KintoneErrorMessages.AUTH_INVALID),
            Triple(403, null, KintoneErrorMessages.AUTH_PERMISSION),
            Triple(404, null, KintoneErrorMessages.APP_NOT_FOUND),
            Triple(429, null, KintoneErrorMessages.RATE_LIMITED),
            Triple(400, "CB_VA01", KintoneErrorMessages.FIELD_MISMATCH),
            Triple(400, "GAIA_RE20", KintoneErrorMessages.UPSERT_MISMATCH),
        )
        cases.forEach { (status, remote, expected) -> assertEquals(expected, KintoneException(KintoneErrorCode.KINTONE_BATCH_FAILED, httpStatus = status, kintoneErrorCode = remote).userMessage) }
    }

    @Test fun classifiesNetworkTimeoutServerAndValidation() {
        assertEquals(KintoneErrorCategory.NETWORK, KintoneException(KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE).category)
        assertEquals(KintoneErrorCategory.TIMEOUT, KintoneException(KintoneErrorCode.KINTONE_TIMEOUT).category)
        assertEquals(KintoneErrorCategory.SERVER, KintoneException(KintoneErrorCode.KINTONE_SERVER_ERROR, httpStatus = 500).category)
        assertEquals(KintoneErrorCategory.VALIDATION, KintoneException(KintoneErrorCode.KINTONE_BATCH_FAILED, httpStatus = 400, kintoneErrorCode = "CB_VA01").category)
    }

    @Test fun exceptionNeverStoresRemoteIdOrResponseBody() {
        val failure = KintoneException(KintoneErrorCode.KINTONE_BATCH_FAILED, httpStatus = 400, kintoneErrorCode = "CB_VA01")
        assertFalse(failure.toString().contains("remote-id")); assertFalse(failure.userMessage.contains("remote-id"))
    }

    @Test fun parsesAllValidationDetailsWithoutResponseId() {
        val (code, errors) = HttpsKintoneApi().parseErrorResponse("""{
            "code":"CB_VA01","id":"secret-response-id","message":"入力内容が正しくありません。",
            "errors":{
              "records[0].record.アプリ更新日時.value":{"messages":["日時の形式が正しくありません。"]},
              "records[1].updateKey.value":{"messages":["必須です。","重複しています。"]}
            }
        }""")
        assertEquals("CB_VA01", code)
        assertEquals(2, errors.size)
        assertEquals("アプリ更新日時", errors[0].fieldCode)
        assertEquals(0, errors[0].recordIndex)
        assertEquals(listOf("必須です。", "重複しています。"), errors[1].messages)
        assertFalse(errors.toString().contains("secret-response-id"))
    }
}
