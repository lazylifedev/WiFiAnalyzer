package com.lazyapps.wifianalyzer.data.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lazyapps.wifianalyzer.domain.ocr.OcrDocument
import com.lazyapps.wifianalyzer.domain.ocr.OcrTextElement
import com.lazyapps.wifianalyzer.domain.ocr.OcrTextLine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LabelTextRecognizer : AutoCloseable {
    private val japanese = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrDocument {
        val image = InputImage.fromBitmap(bitmap, 0)
        val primary = japanese.process(image).await()
        val secondary = latin.process(image).await()
        val combined = (toLines(primary) + toLines(secondary)).distinctBy { normalize(it.text) }
            .sortedWith(compareBy<OcrTextLine> { it.top }.thenBy { it.left })
        return OcrDocument(combined.joinToString("\n") { it.text }, combined)
    }

    private fun toLines(text: Text): List<OcrTextLine> = text.textBlocks.flatMap { block -> block.lines }.map { line ->
        val box = line.boundingBox
        OcrTextLine(
            text = line.text,
            elements = line.elements.map { element ->
                val elementBox = element.boundingBox
                OcrTextElement(element.text, elementBox?.left ?: 0, elementBox?.top ?: 0, elementBox?.right ?: 0, elementBox?.bottom ?: 0)
            },
            left = box?.left ?: 0,
            top = box?.top ?: 0,
            right = box?.right ?: 0,
            bottom = box?.bottom ?: 0,
        )
    }

    private fun normalize(value: String) = value.trim().replace(Regex("\\s+"), " ").uppercase()

    override fun close() {
        japanese.close()
        latin.close()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
}
