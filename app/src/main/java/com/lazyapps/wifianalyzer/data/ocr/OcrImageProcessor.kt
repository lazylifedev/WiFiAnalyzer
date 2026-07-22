package com.lazyapps.wifianalyzer.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

object OcrImageProcessor {
    private const val MAX_EDGE = 2048

    fun process(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "撮影画像を読み込めませんでした" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("撮影画像を読み込めませんでした")
        val rotation = when (ExifInterface(file).rotationDegrees) { 90 -> 90f; 180 -> 180f; 270 -> 270f; else -> 0f }
        val oriented = if (rotation == 0f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true).also { decoded.recycle() }
        val cropWidth = (oriented.width * .80f).toInt().coerceAtLeast(1)
        val cropHeight = (oriented.height * .56f).toInt().coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(oriented, (oriented.width - cropWidth) / 2, (oriented.height - cropHeight) / 2, cropWidth, cropHeight)
        if (cropped !== oriented) oriented.recycle()
        val scale = (MAX_EDGE.toFloat() / max(cropped.width, cropped.height)).coerceAtMost(1f)
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(cropped, (cropped.width * scale).toInt(), (cropped.height * scale).toInt(), true).also { cropped.recycle() } else cropped
        val enhanced = Bitmap.createBitmap(resized.width, resized.height, Bitmap.Config.ARGB_8888)
        val contrast = 1.18f
        val translate = (-.5f * contrast + .5f) * 255f
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        Canvas(enhanced).drawBitmap(resized, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        resized.recycle()
        return enhanced
    }
}
