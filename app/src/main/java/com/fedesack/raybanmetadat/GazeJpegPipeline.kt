package com.fedesack.raybanmetadat

import java.util.concurrent.atomic.AtomicReference

class GazeJpegPipeline(
    private val encodeYuv: (yuv: GazeYuv) -> ByteArray?,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val intervalMs: Long = GazeWs.TARGET_INTERVAL_MS,
    private val onCompressedSkip: (() -> Unit)? = null,
) {
    private val latestYuv = AtomicReference<GazeYuv?>(null)
    @Volatile private var lastAcceptMs = -1L
    @Volatile private var lastEmitMs = -1L

    fun start() {
        lastAcceptMs = -1L
        lastEmitMs = -1L
        latestYuv.set(null)
    }

    fun stop() {
        latestYuv.set(null)
        lastAcceptMs = -1L
        lastEmitMs = -1L
    }

    fun submit(
        width: Int,
        height: Int,
        compressed: Boolean,
        codecConfig: Boolean,
        presentationTimeUs: Long,
        bytes: () -> ByteArray,
    ) {
        try {
            if (compressed || codecConfig) {
                // Never side-decode HEVC into ImageReader / YUV_420_888 planes.
                // Samsung Tab S10 Lite (SM-X400) SIGSEGVs (SEGV_ACCERR) on
                // DirectByteBuffer.get of MediaCodec ImageReader planes.
                // JPEG relay uses uncompressed DAT VideoFrame YUV only.
                onCompressedSkip?.invoke()
                return
            }
            val now = nowMs()
            if (!GazePace.due(now, lastAcceptMs, GazeWs.MIN_INTERVAL_MS)) return
            lastAcceptMs = now
            latestYuv.set(GazeYuv(bytes(), width, height, nv21 = false))
        } catch (_: Throwable) {
            // Copy / encode must never take down the live preview.
        }
    }

    fun poll(): EncodedGaze? {
        val now = nowMs()
        if (!GazePace.due(now, lastEmitMs, intervalMs)) return null
        val yuv = latestYuv.get() ?: return null
        val jpeg =
            try {
                encodeYuv(yuv)
            } catch (_: Throwable) {
                null
            } ?: return null
        lastEmitMs = now
        return EncodedGaze(jpeg, GazeFrameMeta(tsMs = now, w = yuv.width, h = yuv.height))
    }
}
