package com.bunny.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import java.io.ByteArrayOutputStream

object ImageUtils {
    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 85

    fun prepareImage(context: Context, uri: Uri): Pair<ByteArray, String> {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Could not decode image")

        val scaled = scaleDown(original)
        if (scaled != original) original.recycle()

        val format = if (mimeType.contains("png")) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val output = ByteArrayOutputStream()
        scaled.compress(format, JPEG_QUALITY, output)
        scaled.recycle()
        return output.toByteArray() to mimeType
    }

    fun decodeDataUri(dataUri: String): Bitmap? {
        return try {
            val base64 = dataUri.substringAfter("base64,")
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / maxDim
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
