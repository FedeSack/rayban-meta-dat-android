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

    /**
     * Pack YUV_420_888 planes into NV21 (Y plane + interleaved VU).
     *
     * Image.Plane DirectByteBuffers on some devices (Samsung MediaCodec /
     * ImageReader) report a [ByteBuffer.limit] larger than the mapped native
     * memory. The last row of each plane has no row-stride padding:
     * `rowStride * (rows - 1) + pixelStride * (cols - 1) + 1`. Reading past
     * that with `get()`/`memcpy` SIGSEGVs (SEGV_ACCERR). Never trust limit()
     * as the readable end, and never clamp an invalid index into that range.
     */
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
        yPixelStride: Int = 1,
        cropLeft: Int = 0,
        cropTop: Int = 0,
    ): ByteArray {
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("yuv size $width x $height")
        }
        if (width > MAX_DIM || height > MAX_DIM) {
            throw IllegalArgumentException("yuv size too large $width x $height")
        }
        val outW = width and 0x7FFFFFFE
        val outH = height and 0x7FFFFFFE
        if (outW <= 0 || outH <= 0) {
            throw IllegalArgumentException("yuv even size $width x $height")
        }
        if (y.remaining() <= 0) {
            throw IllegalArgumentException("empty y plane")
        }
        val ySize = outW * outH
        val nv21 = ByteArray(ySize + ySize / 2)
        copyPlane(
            src = y,
            rowStride = yRowStride,
            pixelStride = yPixelStride,
            width = outW,
            height = outH,
            cropLeft = cropLeft,
            cropTop = cropTop,
            dst = nv21,
            dstOffset = 0,
            dstPixelStride = 1,
        )
        val chromaW = outW / 2
        val chromaH = outH / 2
        copyPlane(
            src = v,
            rowStride = vRowStride,
            pixelStride = vPixelStride,
            width = chromaW,
            height = chromaH,
            cropLeft = cropLeft / 2,
            cropTop = cropTop / 2,
            dst = nv21,
            dstOffset = ySize,
            dstPixelStride = 2,
        )
        copyPlane(
            src = u,
            rowStride = uRowStride,
            pixelStride = uPixelStride,
            width = chromaW,
            height = chromaH,
            cropLeft = cropLeft / 2,
            cropTop = cropTop / 2,
            dst = nv21,
            dstOffset = ySize + 1,
            dstPixelStride = 2,
        )
        return nv21
    }

    /**
     * Bytes actually mapped for an Image.Plane of [width] x [height] samples.
     * The last row has no stride padding.
     */
    fun planeAccessibleBytes(
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 0
        val px = pixelStride.coerceAtLeast(1)
        val lastRow = px * (width - 1) + 1
        val stride = if (rowStride <= 0) lastRow else rowStride
        return stride * (height - 1) + lastRow
    }

    private fun copyPlane(
        src: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        cropLeft: Int,
        cropTop: Int,
        dst: ByteArray,
        dstOffset: Int,
        dstPixelStride: Int,
    ) {
        if (width <= 0 || height <= 0) return
        if (rowStride > MAX_STRIDE) {
            throw IllegalArgumentException("rowStride $rowStride")
        }
        val srcBuf = src.duplicate()
        val origin = srcBuf.position()
        val px = pixelStride.coerceAtLeast(1)
        if (px > MAX_PIXEL_STRIDE) {
            throw IllegalArgumentException("pixelStride $pixelStride")
        }
        val dstStep = dstPixelStride.coerceAtLeast(1)
        val cropX = cropLeft.coerceAtLeast(0)
        val cropY = cropTop.coerceAtLeast(0)
        val lastRow = px * (width - 1) + 1
        val stride = if (rowStride <= 0) lastRow else rowStride
        val conservative =
            planeAccessibleBytes(stride, px, width + cropX, height + cropY)
        // Never use an overstated limit() as the readable end. For heap
        // buffers that are smaller than the formula, remaining() is tighter.
        val endExclusive = minOf(origin + conservative, origin + srcBuf.remaining())
        var out = dstOffset
        for (row in 0 until height) {
            val rowStart = origin + (cropY + row) * stride + cropX * px
            if (px == 1 && dstStep == 1) {
                val available = (endExclusive - rowStart).coerceAtLeast(0)
                val take = minOf(width, available, (dst.size - out).coerceAtLeast(0))
                if (take > 0 && rowStart >= origin && rowStart < endExclusive) {
                    srcBuf.position(rowStart)
                    srcBuf.get(dst, out, take)
                }
                out += width
            } else {
                for (col in 0 until width) {
                    if (out !in dst.indices) return
                    val idx = rowStart + col * px
                    if (idx >= origin && idx < endExclusive) {
                        dst[out] = srcBuf.get(idx)
                    }
                    out += dstStep
                }
            }
        }
    }

    private const val MAX_DIM = 8192
    private const val MAX_STRIDE = 16_384
    private const val MAX_PIXEL_STRIDE = 16
}
