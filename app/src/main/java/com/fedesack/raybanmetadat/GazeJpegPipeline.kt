package com.fedesack.raybanmetadat

import java.util.concurrent.atomic.AtomicReference

interface GazeHevcSink {
    fun offer(frame: GazeRawFrame)

    fun latestYuv(): GazeYuv?

    fun start()

    fun stop()
}

class GazeJpegPipeline(
    private val encodeYuv: (yuv: GazeYuv) -> ByteArray?,
    private val hevc: GazeHevcSink? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val intervalMs: Long = GazeWs.TARGET_INTERVAL_MS,
) {
    private val latestYuv = AtomicReference<GazeYuv?>(null)
    @Volatile private var lastAcceptMs = -1L
    @Volatile private var lastEmitMs = -1L

    fun start() {
        hevc?.start()
        lastAcceptMs = -1L
        lastEmitMs = -1L
        latestYuv.set(null)
    }

    fun stop() {
        latestYuv.set(null)
        lastAcceptMs = -1L
        lastEmitMs = -1L
        hevc?.stop()
    }

    fun submit(
        width: Int,
        height: Int,
        compressed: Boolean,
        codecConfig: Boolean,
        presentationTimeUs: Long,
        bytes: () -> ByteArray,
    ) {
        if (compressed || codecConfig) {
            hevc?.offer(
                GazeRawFrame(
                    bytes = bytes(),
                    width = width,
                    height = height,
                    compressed = true,
                    codecConfig = codecConfig,
                    presentationTimeUs = presentationTimeUs,
                ),
            )
            return
        }
        val now = nowMs()
        if (!GazePace.due(now, lastAcceptMs, GazeWs.MIN_INTERVAL_MS)) return
        lastAcceptMs = now
        latestYuv.set(GazeYuv(bytes(), width, height, nv21 = false))
    }

    fun poll(): EncodedGaze? {
        val now = nowMs()
        if (!GazePace.due(now, lastEmitMs, intervalMs)) return null
        val yuv = latestYuv.get() ?: hevc?.latestYuv() ?: return null
        val jpeg = encodeYuv(yuv) ?: return null
        lastEmitMs = now
        return EncodedGaze(jpeg, GazeFrameMeta(tsMs = now, w = yuv.width, h = yuv.height))
    }
}
