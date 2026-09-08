package com.fedesack.raybanmetadat

/**
 * Cheap nearest-neighbor YUV downscale before JPEG encode.
 * Keeps even dimensions so I420 / NV21 chroma stays valid.
 */
object GazeYuvScale {
    fun fit(
        yuv: GazeYuv,
        maxWidth: Int = GazeWs.MAX_JPEG_WIDTH,
    ): GazeYuv {
        if (maxWidth <= 0 || yuv.width <= maxWidth) return yuv
        val outW = even(maxWidth)
        val outH = even(yuv.height * outW / yuv.width.coerceAtLeast(1))
        if (outW <= 0 || outH <= 0 || outW >= yuv.width) return yuv
        val bytes =
            if (yuv.nv21) {
                scaleNv21(yuv.bytes, yuv.width, yuv.height, outW, outH)
            } else {
                scaleI420(yuv.bytes, yuv.width, yuv.height, outW, outH)
            }
        return GazeYuv(bytes, outW, outH, nv21 = yuv.nv21)
    }

    fun even(value: Int): Int = (value and 0x7FFFFFFE).coerceAtLeast(2)

    fun scaleI420(
        src: ByteArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
    ): ByteArray {
        val dst = ByteArray(dstW * dstH + dstW * dstH / 2)
        for (y in 0 until dstH) {
            val sy = y * srcH / dstH
            val srcRow = sy * srcW
            val dstRow = y * dstW
            for (x in 0 until dstW) {
                dst[dstRow + x] = at(src, srcRow + x * srcW / dstW)
            }
        }
        val srcCw = srcW / 2
        val srcCh = srcH / 2
        val dstCw = dstW / 2
        val dstCh = dstH / 2
        val srcY = srcW * srcH
        val srcU = srcY + srcCw * srcCh
        val dstY = dstW * dstH
        val dstU = dstY + dstCw * dstCh
        for (y in 0 until dstCh) {
            val sy = y * srcCh / dstCh.coerceAtLeast(1)
            val srcRow = sy * srcCw
            val dstRow = y * dstCw
            for (x in 0 until dstCw) {
                val sx = x * srcCw / dstCw.coerceAtLeast(1)
                dst[dstY + dstRow + x] = at(src, srcY + srcRow + sx)
                dst[dstU + dstRow + x] = at(src, srcU + srcRow + sx)
            }
        }
        return dst
    }

    fun scaleNv21(
        src: ByteArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
    ): ByteArray {
        val dst = ByteArray(dstW * dstH + dstW * dstH / 2)
        for (y in 0 until dstH) {
            val sy = y * srcH / dstH
            val srcRow = sy * srcW
            val dstRow = y * dstW
            for (x in 0 until dstW) {
                dst[dstRow + x] = at(src, srcRow + x * srcW / dstW)
            }
        }
        val srcCw = srcW / 2
        val srcCh = srcH / 2
        val dstCw = dstW / 2
        val dstCh = dstH / 2
        val srcY = srcW * srcH
        val dstY = dstW * dstH
        for (y in 0 until dstCh) {
            val sy = y * srcCh / dstCh.coerceAtLeast(1)
            val srcRow = sy * srcCw
            val dstRow = y * dstCw
            for (x in 0 until dstCw) {
                val sx = x * srcCw / dstCw.coerceAtLeast(1)
                val si = srcY + (srcRow + sx) * 2
                val di = dstY + (dstRow + x) * 2
                dst[di] = at(src, si)
                dst[di + 1] = at(src, si + 1)
            }
        }
        return dst
    }

    private fun at(src: ByteArray, index: Int): Byte =
        if (index in src.indices) src[index] else 0
}
