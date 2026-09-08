package com.fedesack.raybanmetadat

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Locked Windows sidecar motion TEXT on the existing `/frames` socket.
 *
 * Wire JSON (required keys, this order):
 * `{"type":"motion","dx":n,"dy":n,"dt_ms":n,"ts_ms":n,"c":n}`
 *
 * Additive Perf fields (`emit_ms`, `drops`) follow those keys. Consumers that
 * read named fields keep working; the JPEG TEXT `{ts_ms,w,h}` is unchanged.
 *
 * dx/dy are pixels in ~480-wide space (x+ right, y+ down). c is 0..1.
 */
object GazeMotion {
    const val TYPE = "motion"
    const val TARGET_FPS = 24
    const val MIN_FPS = 20
    const val MAX_FPS = 30
    const val TARGET_INTERVAL_MS = 1000L / TARGET_FPS
    const val FLOW_WIDTH = 64
    const val SCALE_WIDTH = 480
    const val SEARCH_RADIUS = 6
    const val MAX_DT_MS = 250L
    const val MAX_DELTA = 240.0
}

data class GazeGray(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val acceptMs: Long,
)

data class GazeFlowResult(
    val u: Double,
    val v: Double,
    val confidence: Double,
)

data class GazeMotionDelta(
    val dx: Double,
    val dy: Double,
    val dtMs: Long,
    val tsMs: Long,
    val c: Double,
    val emitMs: Long,
    val drops: Int = 0,
) {
    fun json(): String {
        val dxn = GazeJson.num(dx)
        val dyn = GazeJson.num(dy)
        val cn = GazeJson.num(c.coerceIn(0.0, 1.0))
        return """{"type":"${GazeMotion.TYPE}","dx":$dxn,"dy":$dyn,"dt_ms":$dtMs,"ts_ms":$tsMs,"c":$cn,"emit_ms":$emitMs,"drops":$drops}"""
    }
}

object GazeJson {
    fun num(value: Double): String {
        val safe =
            when {
                value.isNaN() || value.isInfinite() -> 0.0
                else -> value
            }
        val formatted = String.format(Locale.US, "%.4f", safe)
        return formatted.trimEnd('0').trimEnd('.')
    }
}

object GazeMotionGray {
    fun extract(
        yuv: GazeYuv,
        outWidth: Int = GazeMotion.FLOW_WIDTH,
        acceptMs: Long,
    ): GazeGray? {
        if (yuv.width <= 0 || yuv.height <= 0 || outWidth <= 0) return null
        val ySize = yuv.width * yuv.height
        if (yuv.bytes.size < ySize) return null
        val outW = GazeYuvScale.even(outWidth.coerceAtMost(yuv.width).coerceAtLeast(2))
        val outH = GazeYuvScale.even((yuv.height * outW / yuv.width.coerceAtLeast(1)).coerceAtLeast(2))
        if (outW <= 0 || outH <= 0) return null
        val gray = ByteArray(outW * outH)
        val srcW = yuv.width
        val srcH = yuv.height
        val src = yuv.bytes
        for (y in 0 until outH) {
            val sy = y * srcH / outH
            val srcRow = sy * srcW
            val dstRow = y * outW
            for (x in 0 until outW) {
                val sx = x * srcW / outW
                val idx = srcRow + sx
                gray[dstRow + x] = if (idx in src.indices && idx < ySize) src[idx] else 0
            }
        }
        return GazeGray(gray, outW, outH, acceptMs)
    }
}

/**
 * Translational optical flow on a small luma grid.
 * Integer SAD search, then Lucas-Kanade subpixel refine. Scales to ~480w.
 */
object GazeMotionFlow {
    fun scaleTo480(
        u: Double,
        v: Double,
        flowWidth: Int,
        scaleWidth: Int = GazeMotion.SCALE_WIDTH,
    ): Pair<Double, Double> {
        val scale = scaleWidth.toDouble() / flowWidth.coerceAtLeast(1)
        return clamp(u * scale) to clamp(v * scale)
    }

    fun estimate(
        prev: ByteArray,
        curr: ByteArray,
        width: Int,
        height: Int,
        searchRadius: Int = GazeMotion.SEARCH_RADIUS,
    ): GazeFlowResult {
        if (width < 6 || height < 6 || prev.size < width * height || curr.size < width * height) {
            return GazeFlowResult(0.0, 0.0, 0.0)
        }
        val sad = sadSearch(prev, curr, width, height, searchRadius)
        val refined = lucasKanade(prev, curr, width, height, sad.du, sad.dv)
        val confidence = (sad.confidence * 0.45 + refined.confidence * 0.55).coerceIn(0.0, 1.0)
        return GazeFlowResult(
            u = sad.du + refined.u,
            v = sad.dv + refined.v,
            confidence = confidence,
        )
    }

    fun sadSearch(
        prev: ByteArray,
        curr: ByteArray,
        width: Int,
        height: Int,
        radius: Int,
    ): SadHit {
        val r = radius.coerceIn(0, minOf(width, height) / 3)
        var bestDx = 0
        var bestDy = 0
        var bestSad = Long.MAX_VALUE
        var zeroSad = 0L
        for (dy in -r..r) {
            for (dx in -r..r) {
                val score = sadAt(prev, curr, width, height, dx, dy)
                if (dx == 0 && dy == 0) zeroSad = score
                if (score < bestSad) {
                    bestSad = score
                    bestDx = dx
                    bestDy = dy
                }
            }
        }
        val n = ((width - 2 * r) * (height - 2 * r)).coerceAtLeast(1)
        val meanZero = zeroSad.toDouble() / n
        val meanBest = bestSad.toDouble() / n
        val confidence =
            if (meanZero <= 1e-6) {
                0.0
            } else {
                ((meanZero - meanBest) / (meanZero + 8.0)).coerceIn(0.0, 1.0)
            }
        return SadHit(bestDx, bestDy, confidence)
    }

    internal fun lucasKanade(
        prev: ByteArray,
        curr: ByteArray,
        width: Int,
        height: Int,
        guessU: Int,
        guessV: Int,
    ): GazeFlowResult {
        var axx = 0.0
        var axy = 0.0
        var ayy = 0.0
        var bx = 0.0
        var by = 0.0
        var n = 0
        val x0 = 1 + abs(guessU)
        val y0 = 1 + abs(guessV)
        val x1 = width - 2 - abs(guessU)
        val y1 = height - 2 - abs(guessV)
        if (x1 - x0 < 2 || y1 - y0 < 2) {
            return GazeFlowResult(0.0, 0.0, 0.0)
        }
        var residual = 0.0
        for (y in y0 until y1) {
            val row = y * width
            val rowPrev = (y - 1) * width
            val rowNext = (y + 1) * width
            val rowCurr = (y + guessV) * width + guessU
            for (x in x0 until x1) {
                val p = lum(prev, row + x)
                val ix = (lum(prev, row + x + 1) - lum(prev, row + x - 1)) * 0.5
                val iy = (lum(prev, rowNext + x) - lum(prev, rowPrev + x)) * 0.5
                val it = lum(curr, rowCurr + x) - p
                axx += ix * ix
                axy += ix * iy
                ayy += iy * iy
                bx += ix * it
                by += iy * it
                residual += abs(it)
                n += 1
            }
        }
        if (n < 16) return GazeFlowResult(0.0, 0.0, 0.0)
        val det = axx * ayy - axy * axy
        val texture = (axx + ayy) / n
        if (abs(det) < 1e-3 || texture < 4.0) {
            return GazeFlowResult(0.0, 0.0, 0.0)
        }
        val u = (axy * by - ayy * bx) / det
        val v = (axy * bx - axx * by) / det
        val meanRes = residual / n
        val conf =
            (1.0 - (meanRes / 48.0).coerceIn(0.0, 1.0)) *
                (texture / (texture + 40.0))
        return GazeFlowResult(u, v, conf.coerceIn(0.0, 1.0))
    }

    private fun sadAt(
        prev: ByteArray,
        curr: ByteArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int,
    ): Long {
        // prev[x,y] matches curr[x+dx,y+dy] when content moved +dx,+dy.
        val x0 = maxOf(0, -dx)
        val y0 = maxOf(0, -dy)
        val x1 = minOf(width, width - dx)
        val y1 = minOf(height, height - dy)
        var sum = 0L
        for (y in y0 until y1) {
            val pr = y * width
            val cr = (y + dy) * width + dx
            for (x in x0 until x1) {
                sum += abs(lum(prev, pr + x) - lum(curr, cr + x)).toLong()
            }
        }
        return sum
    }

    private fun lum(src: ByteArray, index: Int): Int =
        if (index in src.indices) src[index].toInt() and 0xFF else 0

    private fun clamp(value: Double): Double {
        val rounded = round(value * 10000.0) / 10000.0
        return rounded.coerceIn(-GazeMotion.MAX_DELTA, GazeMotion.MAX_DELTA)
    }

    data class SadHit(
        val du: Int,
        val dv: Int,
        val confidence: Double,
    )
}
