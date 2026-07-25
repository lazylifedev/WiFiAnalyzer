package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.kintone.KintoneErrorCategory
import com.lazyapps.wifianalyzer.kintone.KintoneErrorCode
import com.lazyapps.wifianalyzer.kintone.KintoneException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import com.lazyapps.wifianalyzer.kintone.HttpsKintoneApi

class KintoneErrorMessagesTest {
    @Test fun mapsHttpAndKintoneFailuresToSafeJapaneseMessages() {
        val cases = listOf(
            Triple(401, null, "APIトークンが無効です。QRコードを再生成してください。"),
            Triple(403, null, "APIトークンにレコード追加・編集権限がありません。"),
            Triple(404, null, "接続先のkintoneアプリが見つかりません。"),
            Triple(429, null, "kintoneへのアクセスが集中しています。時間をおいて再試行します。"),
            Triple(400, "CB_VA01", "送信内容がkintoneのフィールド仕様と一致しません。"),
            Triple(400, "GAIA_RE20", "更新キーに一致するレコードが見つかりません。UPSERT設定を確認してください。"),
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
