package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GazeMetaTest {
    @Test
    fun jsonIsLockedWindowsShapeInFieldOrder() {
        assertEquals(
            """{"ts_ms":1710000000000,"w":720,"h":1280}""",
            GazeFrameMeta(tsMs = 1_710_000_000_000L, w = 720, h = 1280).json(),
        )
    }

    @Test
    fun endpointAlwaysUsesFramesPathAndPort8765() {
        assertEquals("ws://192.168.1.20:8765/frames", GazeWs.endpoint("192.168.1.20"))
        assertEquals("ws://<wifi-ip>:8765/frames", GazeWs.endpoint(null))
        assertEquals("/frames", GazeWs.PATH)
        assertEquals(8765, GazeWs.PORT)
        assertFalse(GazeWs.PATH.contains("gaze"))
    }
}

class GazeLanTest {
    @Test
    fun prefersWlanPrivateIpv4AndSkipsLoopback() {
        val ip =
            GazeLan.wifiIpv4(
                listOf(
                    LanInterface("lo", up = true, loopback = true, ipv4 = "127.0.0.1"),
                    LanInterface("rmnet0", up = true, loopback = false, ipv4 = "10.54.1.2"),
                    LanInterface("wlan0", up = true, loopback = false, ipv4 = "192.168.1.8"),
                ),
            )
        assertEquals("192.168.1.8", ip)
    }

    @Test
    fun fallsBackToAnyNonLoopbackWhenNoWlan() {
        val ip =
            GazeLan.wifiIpv4(
                listOf(
                    LanInterface("eth0", up = true, loopback = false, ipv4 = "10.0.0.4"),
                ),
            )
        assertEquals("10.0.0.4", ip)
    }
}

class GazePaceTest {
    @Test
    fun firstTickIsDueThenRespectsInterval() {
        assertTrue(GazePace.due(1_000L, lastMs = -1L, intervalMs = 80L))
        assertFalse(GazePace.due(1_050L, lastMs = 1_000L, intervalMs = 80L))
        assertTrue(GazePace.due(1_080L, lastMs = 1_000L, intervalMs = 80L))
    }

    @Test
    fun targetIntervalStaysInside10to15Fps() {
        assertEquals(83L, GazeWs.TARGET_INTERVAL_MS)
        assertEquals(66L, GazeWs.MIN_INTERVAL_MS)
        assertTrue(GazeWs.TARGET_FPS in 10..15)
        assertEquals(15, GazeWs.MAX_FPS)
    }
}

class GazeWsHandshakeTest {
    @Test
    fun rfc6455AcceptKey() {
        assertEquals(
            "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
            GazeWsHandshake.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="),
        )
    }

    @Test
    fun acceptsFramesAndRejectsGazePath() {
        assertTrue(GazeWsHandshake.isFramesPath("/frames"))
        assertFalse(GazeWsHandshake.isFramesPath("/gaze"))
        assertFalse(GazeWsHandshake.isFramesPath("/frames/"))
        assertFalse(GazeWsHandshake.isFramesPath("/"))
    }

    @Test
    fun parseReadsPathWithoutQueryAndUpgradeHeaders() {
        val request =
            GazeWsHandshake.parse(
                "GET /frames?sidecar=1 HTTP/1.1\r\n" +
                    "Host: 192.168.1.8:8765\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "\r\n",
            )
        assertEquals("GET", request!!.method)
        assertEquals("/frames", request.path)
        assertTrue(request.upgrade)
        assertEquals("dGhlIHNhbXBsZSBub25jZQ==", request.key)
    }
}

class GazeWsFramesTest {
    @Test
    fun encodesUnmaskedTextAndBinary() {
        val text = GazeWsFrames.encodeText("hi")
        assertEquals(0x81.toByte(), text[0])
        assertEquals(2.toByte(), text[1])
        assertEquals('h'.code.toByte(), text[2])
        assertEquals('i'.code.toByte(), text[3])

        val bin = GazeWsFrames.encodeBinary(byteArrayOf(1, 2, 3))
        assertEquals(0x82.toByte(), bin[0])
        assertEquals(3.toByte(), bin[1])
        assertEquals(1.toByte(), bin[2])
    }

    @Test
    fun readsMaskedClientPing() {
        val payload = byteArrayOf(9, 8, 7)
        val mask = byteArrayOf(1, 2, 3, 4)
        val frame =
            byteArrayOf(
                0x89.toByte(),
                (0x80 or 3).toByte(),
                mask[0],
                mask[1],
                mask[2],
                mask[3],
                (payload[0].toInt() xor mask[0].toInt()).toByte(),
                (payload[1].toInt() xor mask[1].toInt()).toByte(),
                (payload[2].toInt() xor mask[2].toInt()).toByte(),
            )
        val incoming = GazeWsFrames.read(ByteArrayInputStream(frame))
        assertEquals(GazeWsFrames.OP_PING, incoming!!.opcode)
        assertTrue(payload.contentEquals(incoming.payload))
    }
}

class GazeJpegPipelineTest {
    @Test
    fun pollsLatestYuvAsJpegMetaAtTargetPace() {
        var now = 1_000L
        val pipeline =
            GazeJpegPipeline(
                encodeYuv = { yuv -> byteArrayOf(0xFF.toByte(), 0xD8.toByte(), yuv.width.toByte()) },
                nowMs = { now },
                intervalMs = 80L,
            )
        pipeline.start()
        pipeline.submit(720, 1280, compressed = false, codecConfig = false, presentationTimeUs = 1L) {
            ByteArray(8)
        }
        val first = pipeline.poll()
        assertEquals(720, first!!.meta.w)
        assertEquals(1280, first.meta.h)
        assertEquals(1_000L, first.meta.tsMs)
        assertEquals(0xFF.toByte(), first.jpeg[0])
        assertEquals(0xD8.toByte(), first.jpeg[1])

        now = 1_040L
        assertNull(pipeline.poll())

        now = 1_080L
        pipeline.submit(640, 480, compressed = false, codecConfig = false, presentationTimeUs = 2L) {
            ByteArray(4)
        }
        val second = pipeline.poll()
        assertEquals(640, second!!.meta.w)
        assertEquals(480, second.meta.h)
        assertEquals(1_080L, second.meta.tsMs)
        pipeline.stop()
    }

    @Test
    fun compressedFramesGoToHevcAndYuvSlotStaysEmptyUntilDecoded() {
        val hevc = RecordingHevcSink()
        val pipeline =
            GazeJpegPipeline(
                encodeYuv = { yuv -> byteArrayOf(yuv.width.toByte()) },
                hevc = hevc,
                nowMs = { 5_000L },
            )
        pipeline.start()
        pipeline.submit(720, 1280, compressed = true, codecConfig = true, presentationTimeUs = 0L) {
            byteArrayOf(1, 2, 3)
        }
        pipeline.submit(720, 1280, compressed = true, codecConfig = false, presentationTimeUs = 40_000L) {
            byteArrayOf(4, 5)
        }
        assertEquals(2, hevc.offered.size)
        assertTrue(hevc.offered[0].codecConfig)
        assertNull(pipeline.poll())
        hevc.latest = GazeYuv(ByteArray(4), 720, 1280, nv21 = true)
        val encoded = pipeline.poll()
        assertEquals(720, encoded!!.meta.w)
        assertEquals(1280, encoded.meta.h)
        pipeline.stop()
        assertTrue(hevc.stopped)
    }
}

class GazeBridgeGateTest {
    @Test
    fun startsOnlyWhenFlagAndStreamAreOnAndStopsOnEitherOff() {
        val hub = FakeHub()
        val pipeline =
            GazeJpegPipeline(
                encodeYuv = { byteArrayOf(1) },
                nowMs = { 0L },
            )
        val bridge =
            GazeBridge(
                server = hub,
                pipeline = pipeline,
                wifiIp = { "10.0.0.8" },
                sleeper = { Thread.sleep(2) },
            )
        assertEquals("ws://10.0.0.8:8765/frames", bridge.displayUrl(true))
        assertNull(bridge.displayUrl(false))

        bridge.sync(flagOn = true, streamLive = false)
        assertFalse(hub.started)

        bridge.sync(flagOn = false, streamLive = true)
        assertFalse(hub.started)

        bridge.sync(flagOn = true, streamLive = true)
        assertTrue(hub.started)
        assertEquals(1, hub.startCount.get())

        bridge.sync(flagOn = true, streamLive = true)
        assertEquals(1, hub.startCount.get())

        bridge.sync(flagOn = false, streamLive = true)
        assertTrue(hub.stopped)
        assertFalse(hub.listening)
    }

    @Test
    fun pumpSendsTextJsonThenBinaryJpegWhenAClientIsConnected() {
        val hub = FakeHub()
        hub.clients.set(1)
        var now = 10_000L
        val pipeline =
            GazeJpegPipeline(
                encodeYuv = { byteArrayOf(0xFF.toByte(), 0xD8.toByte()) },
                nowMs = { now },
                intervalMs = 80L,
            )
        val emitted = CountDownLatch(1)
        hub.onBroadcast = { emitted.countDown() }
        val bridge =
            GazeBridge(
                server = hub,
                pipeline = pipeline,
                wifiIp = { "192.168.0.2" },
                sleeper = { Thread.sleep(5) },
            )
        bridge.sync(flagOn = true, streamLive = true)
        repeat(40) {
            if (hub.texts.isNotEmpty()) return@repeat
            bridge.submit(320, 240, compressed = false, codecConfig = false, presentationTimeUs = 1L) {
                ByteArray(6)
            }
            if (emitted.await(50, TimeUnit.MILLISECONDS)) return@repeat
        }
        assertEquals("""{"ts_ms":10000,"w":320,"h":240}""", hub.texts.firstOrNull())
        assertTrue(hub.binaries.firstOrNull()?.contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) == true)
        bridge.stop()
    }
}

class GazeWsServerLoopbackTest {
    @Test
    fun upgradesFramesAnd404sGaze() {
        val server = GazeWsServer(host = "127.0.0.1", port = 0)
        assertTrue(server.start())
        try {
            val frames = rawUpgrade(server.localPort, "/frames")
            assertTrue(frames.startsWith("HTTP/1.1 101"))
            assertTrue(frames.contains("Sec-WebSocket-Accept:"))

            val gaze = rawUpgrade(server.localPort, "/gaze")
            assertTrue(gaze.startsWith("HTTP/1.1 404"))
            assertFalse(gaze.contains("101"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun broadcastsLockedMetaThenJpegToAFramesClient() {
        val server = GazeWsServer(host = "127.0.0.1", port = 0)
        assertTrue(server.start())
        try {
            Socket("127.0.0.1", server.localPort).use { socket ->
                socket.tcpNoDelay = true
                val key = "dGhlIHNhbXBsZSBub25jZQ=="
                socket.getOutputStream().write(upgradeRequest("/frames", key).toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
                val head = GazeWsHandshake.readHead(socket.getInputStream())
                assertTrue(head!!.startsWith("HTTP/1.1 101"))

                val wait = CountDownLatch(1)
                Thread {
                    while (server.clientCount == 0) {
                        Thread.sleep(5)
                    }
                    wait.countDown()
                }.start()
                assertTrue(wait.await(1, TimeUnit.SECONDS))

                val meta = GazeFrameMeta(tsMs = 42L, w = 8, h = 6)
                server.broadcastText(meta.json())
                server.broadcastBinary(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00))

                val text = GazeWsFrames.read(socket.getInputStream())
                assertEquals(GazeWsFrames.OP_TEXT, text!!.opcode)
                assertEquals("""{"ts_ms":42,"w":8,"h":6}""", String(text.payload, Charsets.UTF_8))
                val bin = GazeWsFrames.read(socket.getInputStream())
                assertEquals(GazeWsFrames.OP_BINARY, bin!!.opcode)
                assertTrue(bin.payload.contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00)))
            }
        } finally {
            server.stop()
        }
    }

    private fun rawUpgrade(
        port: Int,
        path: String,
    ): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write(upgradeRequest(path, "dGhlIHNhbXBsZSBub25jZQ==").toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()
            return GazeWsHandshake.readHead(socket.getInputStream()) ?: ""
        }
    }

    private fun upgradeRequest(
        path: String,
        key: String,
    ): String =
        "GET $path HTTP/1.1\r\n" +
            "Host: 127.0.0.1\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n"
}

private class RecordingHevcSink : GazeHevcSink {
    val offered = mutableListOf<GazeRawFrame>()
    var latest: GazeYuv? = null
    var stopped = false

    override fun offer(frame: GazeRawFrame) {
        offered += frame
    }

    override fun latestYuv(): GazeYuv? = latest

    override fun start() = Unit

    override fun stop() {
        stopped = true
    }
}

private class FakeHub : GazeSocketHub {
    override var listening: Boolean = false
    val clients = AtomicInteger(0)
    override val clientCount: Int get() = clients.get()
    override val localPort: Int = 8765
    val startCount = AtomicInteger(0)
    val started: Boolean get() = startCount.get() > 0
    var stopped = false
    val texts = CopyOnWriteArrayList<String>()
    val binaries = CopyOnWriteArrayList<ByteArray>()
    var onBroadcast: (() -> Unit)? = null
    private val running = AtomicBoolean(false)

    override fun start(): Boolean {
        startCount.incrementAndGet()
        listening = true
        running.set(true)
        return true
    }

    override fun stop() {
        stopped = true
        listening = false
        running.set(false)
    }

    override fun broadcastText(text: String) {
        texts += text
        onBroadcast?.invoke()
    }

    override fun broadcastBinary(bytes: ByteArray) {
        binaries += bytes
    }
}
