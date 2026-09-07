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
    ): ByteArray = encodeNv21(i420ToNv21(buffer, width, height), width, height, quality)

    fun encodeNv21(
        nv21: ByteArray,
        width: Int,
        height: Int,
        quality: Int,
    ): ByteArray {
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val jpeg = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, width, height), quality.coerceIn(1, 100), jpeg)
        return jpeg.toByteArray()
    }

    fun encodeYuv(yuv: GazeYuv, quality: Int): ByteArray =
        if (yuv.nv21) {
            encodeNv21(yuv.bytes, yuv.width, yuv.height, quality)
        } else {
            encode(ByteBuffer.wrap(yuv.bytes), yuv.width, yuv.height, quality)
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

    fun yuv420888ToNv21(
        width: Int,
        height: Int,
        y: ByteBuffer,
        yRowStride: Int,
        u: ByteBuffer,
        uRowStride: Int,
        uPixelStride: Int,
        v: ByteBuffer,
        vRowStride: Int,
        vPixelStride: Int,
    ): ByteArray {
        val ySize = width * height
        val nv21 = ByteArray(ySize + ySize / 2)
        val yBuf = y.duplicate()
        val yStart = yBuf.position()
        var dst = 0
        for (row in 0 until height) {
            yBuf.position((yStart + row * yRowStride).coerceAtMost(yBuf.limit()))
            val take = width.coerceAtMost(yBuf.remaining())
            yBuf.get(nv21, dst, take)
            dst += width
        }
        val uBuf = u.duplicate()
        val vBuf = v.duplicate()
        val uStart = uBuf.position()
        val vStart = vBuf.position()
        val chromaH = height / 2
        val chromaW = width / 2
        var o = ySize
        for (row in 0 until chromaH) {
            val uRow = uStart + row * uRowStride
            val vRow = vStart + row * vRowStride
            for (col in 0 until chromaW) {
                val uIndex = uRow + col * uPixelStride
                val vIndex = vRow + col * vPixelStride
                nv21[o++] = vBuf.get(vIndex.coerceIn(0, vBuf.limit() - 1))
                nv21[o++] = uBuf.get(uIndex.coerceIn(0, uBuf.limit() - 1))
            }
        }
        return nv21
    }
}
