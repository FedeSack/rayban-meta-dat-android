package com.fedesack.raybanmetadat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object YuvJpeg {
    const val PREVIEW_QUALITY = 80

    fun encode(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        quality: Int,
    ): ByteArray {
        val nv21 = i420ToNv21(buffer, width, height)
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val jpeg = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, width, height), quality.coerceIn(1, 100), jpeg)
        return jpeg.toByteArray()
    }

    fun decodeBitmap(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        quality: Int = PREVIEW_QUALITY,
    ): Bitmap? {
        val jpeg = encode(buffer, width, height, quality)
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    fun copyBuffer(buffer: ByteBuffer): ByteArray {
        val duplicate = buffer.duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    fun i420ToNv21(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
    ): ByteArray {
        val ySize = width * height
        val cSize = ySize / 4
        val src = buffer.duplicate()
        val nv21 = ByteArray(ySize + cSize * 2)
        src.get(nv21, 0, ySize)
        val u = ByteArray(cSize)
        val v = ByteArray(cSize)
        if (src.remaining() >= cSize * 2) {
            src.get(u)
            src.get(v)
        }
        var o = ySize
        for (i in 0 until cSize) {
            nv21[o++] = v[i]
            nv21[o++] = u[i]
        }
        return nv21
    }
}
