package com.fedesack.raybanmetadat

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GazeJpegPipeline(
    private val encodeYuv: (yuv: GazeYuv) -> ByteArray?,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val intervalMs: Long = GazeWs.TARGET_INTERVAL_MS,
    private val maxWidth: Int = GazeWs.MAX_JPEG_WIDTH,
    private val onCompressedSkip: (() -> Unit)? = null,
) {
    private val latestYuv = AtomicReference<GazeYuv?>(null)
    private val overwrittenYuv = AtomicInteger(0)
    @Volatile private var lastAcceptMs = -1L
    @Volatile private var lastEmitMs = -1L

    val pendingYuv: Boolean get() = latestYuv.get() != null
    val overwrittenYuvCount: Int get() = overwrittenYuv.get()

    fun start() {
        lastAcceptMs = -1L
        lastEmitMs = -1L
        latestYuv.set(null)
        overwrittenYuv.set(0)
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
            val scaled =
                GazeYuvScale.fit(
                    GazeYuv(bytes(), width, height, nv21 = false),
                    maxWidth,
                )
            val previous = latestYuv.getAndSet(scaled)
            if (previous != null) overwrittenYuv.incrementAndGet()
            lastAcceptMs = nowMs()
        } catch (_: Throwable) {
            // Copy / encode must never take down the live preview.
        }
    }

    fun poll(): EncodedGaze? {
        val now = nowMs()
        if (!GazePace.due(now, lastEmitMs, intervalMs)) return null
        val yuv = latestYuv.getAndSet(null) ?: return null
        val jpeg =
            try {
                encodeYuv(yuv)
            } catch (_: Throwable) {
                null
            } ?: return null
        // Pace from encode completion so a slow encode lowers fps
        // instead of catching up by blasting the next frame immediately.
        lastEmitMs = nowMs()
        return EncodedGaze(jpeg, GazeFrameMeta(tsMs = lastEmitMs, w = yuv.width, h = yuv.height))
    }
}
