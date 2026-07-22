package com.lazyapps.wifianalyzer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.lazyapps.wifianalyzer.data.ocr.LabelTextRecognizer
import com.lazyapps.wifianalyzer.domain.ocr.DeviceLabelParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelTextRecognizerDeviceTest {
    @Test fun bundledLatinAndJapaneseModelsRecognizeSyntheticDeviceLabelOffline() = runBlocking {
        val bitmap = Bitmap.createBitmap(1800, 1100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 92f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        listOf(
            "BUFFALO",
            "MODEL: TEST-100",
            "型番: TEST-200",
            "SSID 5G: LAB-5G",
            "BSSID: 02:11:22:33:44:55",
        ).forEachIndexed { index, text -> canvas.drawText(text, 80f, 170f + index * 190f, paint) }

        val recognizer = LabelTextRecognizer()
        try {
            val document = recognizer.recognize(bitmap)
            val parsed = DeviceLabelParser.parse(document)
            assertTrue(document.text.contains("BUFFALO", ignoreCase = true))
            assertTrue(document.text.contains("型番"))
            assertEquals("BUFFALO", parsed.manufacturerCandidates.first().value)
            assertTrue(parsed.modelCandidates.any { it.value.contains("TEST-100") || it.value.contains("TEST-200") })
            assertEquals("02:11:22:33:44:55", parsed.macCandidates.first().value)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}
