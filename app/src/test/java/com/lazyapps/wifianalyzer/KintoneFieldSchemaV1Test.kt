package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.kintone.HttpsKintoneApi
import com.lazyapps.wifianalyzer.kintone.KintoneErrorCode
import com.lazyapps.wifianalyzer.kintone.KintoneException
import com.lazyapps.wifianalyzer.kintone.KintoneFieldProperty
import com.lazyapps.wifianalyzer.kintone.KintoneFieldSchemaV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KintoneFieldSchemaV1Test {
    @Test fun officialSixteenFieldsMatch() { assertTrue(validate().warnings.isEmpty()) }
    @Test fun missingDeviceUuidFails() { assertCode(KintoneErrorCode.KINTONE_REQUIRED_FIELD_MISSING) { validate(fields() - "機器UUID") } }
    @Test fun deviceUuidWrongTypeFails() { assertCode(KintoneErrorCode.KINTONE_FIELD_TYPE_MISMATCH) { validate(change("機器UUID", type = "NUMBER")) } }
    @Test fun deviceUuidRequiredFalseFails() { assertCode(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH) { validate(change("機器UUID", required = false)) } }
    @Test fun deviceUuidUniqueFalseFails() { assertCode(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH) { validate(change("機器UUID", unique = false)) } }
    @Test fun deviceUuidUnknownUniqueWarns() { assertEquals("UNIQUE_SETTING_UNVERIFIED", validate(change("機器UUID", unique = null)).warnings.single()) }
    @Test fun deviceUuidTooShortFails() { assertCode(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH) { validate(change("機器UUID", maxLength = 35)) } }
    @Test fun memoUsesRestMultiLineType() { assertEquals("MULTI_LINE_TEXT", validate().fields["メモ"]) }
    @Test fun templateInternalMultiLineTypeFails() { assertCode(KintoneErrorCode.KINTONE_FIELD_TYPE_MISMATCH) { validate(change("メモ", type = "MULTIPLE_LINE_TEXT")) } }
    @Test fun deletionUsesRestCheckboxType() { assertEquals("CHECK_BOX", validate().fields["削除状態"]) }
    @Test fun deletionOptionExists() { assertTrue(fields().getValue("削除状態").options.contains("削除済")) }
    @Test fun missingDeletionOptionFails() { assertCode(KintoneErrorCode.KINTONE_SCHEMA_MISMATCH) { validate(change("削除状態", options = setOf("その他"))) } }
    @Test fun extraDeletionOptionIsAllowed() { assertTrue(validate(change("削除状態", options = setOf("削除済", "その他"))).warnings.isEmpty()) }
    @Test fun photoFileExists() { assertEquals("FILE", validate().fields["写真"]) }
    @Test fun missingPhotoWarnsButConnects() { assertEquals(listOf("PHOTO_SYNC_UNAVAILABLE"), validate(fields() - "写真").warnings) }
    @Test fun userAddedFieldIsInformationOnly() {
        val result = validate(fields() + ("将来項目" to field("将来項目", "NUMBER")))
        assertTrue(result.warnings.isEmpty())
        assertEquals(listOf("EXTRA_FIELDS_IGNORED"), result.information)
    }
    @Test fun systemFieldsAreNotReportedAsAdditionalFields() {
        val systemFields = listOf("\$id", "\$revision", "レコード番号", "作成者", "更新者", "作成日時", "更新日時", "ステータス", "作業者", "カテゴリー")
            .associateWith { field(it, "RECORD_NUMBER") }
        val result = validate(fields() + systemFields)
        assertTrue(result.warnings.isEmpty())
        assertTrue(result.information.isEmpty())
    }
    @Test fun changedLabelDoesNotMatter() { assertTrue(validate(change("機器名", label = "表示名だけ変更")).warnings.isEmpty()) }
    @Test fun defaultNowMissingWarns() { assertTrue(validate(change("アプリ更新日時", defaultNowValue = false)).warnings.contains("UPDATED_AT_DEFAULT_INVALID")) }

    @Test fun parsesOfficialRestApiPropertyNames() {
        val parsed = HttpsKintoneApi().parseFields("""{"properties":{"機器UUID":{"code":"機器UUID","label":"機器 UUID","type":"SINGLE_LINE_TEXT","required":true,"unique":true,"maxLength":"64"},"削除状態":{"code":"削除状態","label":"削除状態","type":"CHECK_BOX","options":{"削除済":{"label":"削除済","index":"0"}}}}}""")
        assertEquals(true, parsed.getValue("機器UUID").unique)
        assertEquals(64, parsed.getValue("機器UUID").maxLength)
        assertEquals(setOf("削除済"), parsed.getValue("削除状態").options)
    }

    private fun validate(value: Map<String, KintoneFieldProperty> = fields()) = KintoneFieldSchemaV1.validate(value)
    private fun fields() = linkedMapOf(
        "機器UUID" to field("機器UUID", "SINGLE_LINE_TEXT", required = true, unique = true, maxLength = 64),
        "ワークスペースUUID" to field("ワークスペースUUID"), "ワークスペース名" to field("ワークスペース名"),
        "グループUUID" to field("グループUUID"), "グループ名" to field("グループ名"), "機器名" to field("機器名"),
        "メーカー" to field("メーカー"), "型番" to field("型番"), "シリアル番号" to field("シリアル番号"),
        "SSID" to field("SSID"), "主BSSID" to field("主BSSID"), "設置場所" to field("設置場所"),
        "メモ" to field("メモ", "MULTI_LINE_TEXT"),
        "アプリ更新日時" to field("アプリ更新日時", "DATETIME", defaultNowValue = true),
        "削除状態" to field("削除状態", "CHECK_BOX", options = setOf("削除済")),
        "写真" to field("写真", "FILE"),
    )

    private fun change(code: String, type: String? = null, label: String? = null, required: Boolean? = fields().getValue(code).required,
        unique: Boolean? = fields().getValue(code).unique, maxLength: Int? = fields().getValue(code).maxLength,
        defaultNowValue: Boolean? = fields().getValue(code).defaultNowValue, options: Set<String> = fields().getValue(code).options): Map<String, KintoneFieldProperty> {
        val result = fields(); val old = result.getValue(code)
        result[code] = old.copy(type = type ?: old.type, label = label ?: old.label, required = required, unique = unique, maxLength = maxLength, defaultNowValue = defaultNowValue, options = options)
        return result
    }

    private fun field(code: String, type: String = "SINGLE_LINE_TEXT", label: String = code, required: Boolean? = false,
        unique: Boolean? = false, maxLength: Int? = null, defaultNowValue: Boolean? = null, options: Set<String> = emptySet()) =
        KintoneFieldProperty(code, label, type, required, unique, maxLength, defaultNowValue, options)

    private fun assertCode(code: KintoneErrorCode, block: () -> Unit) {
        assertEquals(code, (runCatching(block).exceptionOrNull() as KintoneException).code)
    }
}
