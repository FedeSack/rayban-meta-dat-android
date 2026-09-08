package com.fedesack.raybanmetadat

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Latest-wins luma mailbox + paced optical-flow. Target ~20–30 Hz.
 * Overwrites pending gray instead of queueing a backlog.
 */
class GazeMotionPipeline(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val intervalMs: Long = GazeMotion.TARGET_INTERVAL_MS,
    private val flowWidth: Int = GazeMotion.FLOW_WIDTH,
    private val estimate: (GazeGray, GazeGray) -> GazeFlowResult =
        { prev, curr ->
            GazeMotionFlow.estimate(prev.bytes, curr.bytes, curr.width, curr.height)
        },
    private val onCompressedSkip: (() -> Unit)? = null,
) {
    private val latest = AtomicReference<GazeGray?>(null)
    private val overwritten = AtomicInteger(0)
    private val emitted = AtomicInteger(0)

    @Volatile private var prev: GazeGray? = null

    @Volatile private var lastEmitMs = -1L

    val pendingGray: Boolean get() = latest.get() != null
    val overwrittenGrayCount: Int get() = overwritten.get()
    val emitCount: Int get() = emitted.get()

    fun start() {
        latest.set(null)
        prev = null
        overwritten.set(0)
        emitted.set(0)
        lastEmitMs = -1L
    }

    fun stop() {
        latest.set(null)
        prev = null
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
                onCompressedSkip?.invoke()
                return
            }
            val gray =
                GazeMotionGray.extract(
                    GazeYuv(bytes(), width, height, nv21 = false),
                    outWidth = flowWidth,
                    acceptMs = nowMs(),
                ) ?: return
            if (latest.getAndSet(gray) != null) overwritten.incrementAndGet()
        } catch (_: Throwable) {
            // Copy / flow must never take down the live preview.
        }
    }

    fun poll(): GazeMotionDelta? {
        val now = nowMs()
        if (!GazePace.due(now, lastEmitMs, intervalMs)) return null
        val curr = latest.getAndSet(null) ?: return null
        val previous = prev
        prev = curr
        if (previous == null) return null
        if (previous.width != curr.width || previous.height != curr.height) return null
        val dt = curr.acceptMs - previous.acceptMs
        if (dt <= 0L || dt > GazeMotion.MAX_DT_MS) return null
        val flow =
            try {
                estimate(previous, curr)
            } catch (_: Throwable) {
                return null
            }
        val (dx, dy) = GazeMotionFlow.scaleTo480(flow.u, flow.v, curr.width)
        val drops = overwritten.getAndSet(0)
        lastEmitMs = now
        emitted.incrementAndGet()
        return GazeMotionDelta(
            dx = dx,
            dy = dy,
            dtMs = dt,
            tsMs = curr.acceptMs,
            c = flow.confidence.coerceIn(0.0, 1.0),
            emitMs = now,
            drops = drops,
        )
    }
}
