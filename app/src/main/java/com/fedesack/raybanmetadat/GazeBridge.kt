package com.fedesack.raybanmetadat

import java.util.concurrent.atomic.AtomicBoolean

class GazeBridge(
    private val server: GazeSocketHub,
    private val pipeline: GazeJpegPipeline,
    private val wifiIp: () -> String? = { GazeLan.wifiIpv4() },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val running = AtomicBoolean(false)
    private var pump: Thread? = null

    val listening: Boolean get() = server.listening

    fun displayUrl(flagOn: Boolean): String? = if (flagOn) GazeLan.endpoint(wifiIp()) else null

    fun sync(
        flagOn: Boolean,
        streamLive: Boolean,
    ) {
        if (flagOn && streamLive) start() else stop()
    }

    fun submit(
        width: Int,
        height: Int,
        compressed: Boolean,
        codecConfig: Boolean,
        presentationTimeUs: Long,
        bytes: () -> ByteArray,
    ) {
        if (!running.get()) return
        pipeline.submit(
            width = width,
            height = height,
            compressed = compressed,
            codecConfig = codecConfig,
            presentationTimeUs = presentationTimeUs,
            bytes = bytes,
        )
    }

    @Synchronized
    fun start() {
        if (!running.compareAndSet(false, true)) return
        pipeline.start()
        if (!server.start()) {
            pipeline.stop()
            running.set(false)
            return
        }
        pump =
            Thread({ pumpLoop() }, "gaze-pump").apply {
                isDaemon = true
                start()
            }
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false)) {
            server.stop()
            pipeline.stop()
            return
        }
        pump?.interrupt()
        pump?.join(80)
        pump = null
        pipeline.stop()
        server.stop()
    }

    private fun pumpLoop() {
        while (running.get()) {
            try {
                if (server.clientCount == 0) {
                    sleeper(20)
                    continue
                }
                val encoded = pipeline.poll()
                if (encoded != null) {
                    server.broadcastFrame(encoded.meta.json(), encoded.jpeg)
                }
                sleeper(GazeWs.PUMP_SLEEP_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }
}
