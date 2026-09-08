package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GazeMotionSchemaTest {
    @Test
    fun jsonIsLockedWindowsShapeInFieldOrderWithPerfExtras() {
        val json =
            GazeMotionDelta(
                dx = -3.5,
                dy = 2.0,
                dtMs = 41,
                tsMs = 1_710_000_000_000L,
                c = 0.82,
                emitMs = 1_710_000_000_012L,
                drops = 3,
            ).json()
        assertEquals(
            """{"type":"motion","dx":-3.5,"dy":2,"dt_ms":41,"ts_ms":1710000000000,"c":0.82,"emit_ms":1710000000012,"drops":3}""",
            json,
        )
        assertTrue(json.startsWith("""{"type":"motion","dx":"""))
        assertFalse(json.contains("\"w\":"))
        assertFalse(json.contains("/motion"))
    }

    @Test
    fun jsonUsesUsLocaleAndClampsConfidence() {
        val json =
            GazeMotionDelta(
                dx = 1.23456,
                dy = -0.1,
                dtMs = 40,
                tsMs = 9,
                c = 1.4,
                emitMs = 11,
                drops = 0,
            ).json()
        val keys = lockedKeyOrder(json)
        assertEquals(listOf("type", "dx", "dy", "dt_ms", "ts_ms", "c", "emit_ms", "drops"), keys)
        val parsed = parseObject(json)
        assertEquals("motion", parsed["type"])
        assertEquals("1.2346", parsed["dx"])
        assertEquals("-0.1", parsed["dy"])
        assertEquals("40", parsed["dt_ms"])
        assertEquals("9", parsed["ts_ms"])
        assertEquals("1", parsed["c"])
        assertEquals("11", parsed["emit_ms"])
        assertEquals("0", parsed["drops"])
        assertFalse("decimals must stay Locale.US", parsed["dx"]!!.contains(','))
        assertTrue(parsed["dx"]!!.contains('.'))
    }

    @Test
    fun constantsStayOnFramesPathAt20to30HzAnd480w() {
        assertEquals("motion", GazeMotion.TYPE)
        assertEquals("/frames", GazeWs.PATH)
        assertTrue(GazeMotion.TARGET_FPS in GazeMotion.MIN_FPS..GazeMotion.MAX_FPS)
        assertEquals(24, GazeMotion.TARGET_FPS)
        assertEquals(41L, GazeMotion.TARGET_INTERVAL_MS)
        assertEquals(480, GazeMotion.SCALE_WIDTH)
        assertEquals(64, GazeMotion.FLOW_WIDTH)
        assertFalse(GazeWsHandshake.isFramesPath("/motion"))
        assertTrue(GazeWsHandshake.isFramesPath("/frames"))
    }

    private fun lockedKeyOrder(json: String): List<String> {
        val matcher = Pattern.compile("\"([a-z_]+)\":").matcher(json)
        val keys = mutableListOf<String>()
        while (matcher.find()) keys += matcher.group(1)
        return keys
    }

    private fun parseObject(json: String): Map<String, String> {
        val body = json.removePrefix("{").removeSuffix("}")
        return body.split(',').associate { part ->
            val idx = part.indexOf(':')
            val key = part.substring(0, idx).trim().trim('"')
            val value = part.substring(idx + 1).trim().trim('"')
            key to value
        }
    }
}

class GazeMotionFlowTest {
    @Test
    fun recoversIntegerShiftAndScalesTo480w() {
        val w = 32
        val h = 24
        val prev = patterned(w, h)
        val curr = shift(prev, w, h, dx = 3, dy = -2)
        val flow = GazeMotionFlow.estimate(prev, curr, w, h, searchRadius = 6)
        assertEquals(3.0, flow.u, 0.75)
        assertEquals(-2.0, flow.v, 0.75)
        assertTrue(flow.confidence > 0.15)
        val (dx, dy) = GazeMotionFlow.scaleTo480(flow.u, flow.v, w)
        val expectedScale = 480.0 / w
        assertEquals(3.0 * expectedScale, dx, expectedScale * 0.75)
        assertEquals(-2.0 * expectedScale, dy, expectedScale * 0.75)
    }

    @Test
    fun stillFrameIsNearZeroWithLowOrZeroMotion() {
        val w = 24
        val h = 16
        val frame = patterned(w, h)
        val flow = GazeMotionFlow.estimate(frame, frame.copyOf(), w, h, searchRadius = 4)
        assertEquals(0.0, flow.u, 0.35)
        assertEquals(0.0, flow.v, 0.35)
    }

    @Test
    fun sadSearchFindsKnownOffset() {
        val w = 20
        val h = 16
        val prev = patterned(w, h)
        val curr = shift(prev, w, h, dx = -2, dy = 1)
        val hit = GazeMotionFlow.sadSearch(prev, curr, w, h, radius = 4)
        assertEquals(-2, hit.du)
        assertEquals(1, hit.dv)
        assertTrue(hit.confidence > 0.1)
    }

    private fun patterned(width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = ((x * 13 + y * 7) % 180) + 40
                out[y * width + x] = v.toByte()
            }
        }
        // Bright block so gradients / SAD have a unique peak.
        for (y in 4 until 10) {
            for (x in 5 until 12) {
                out[y * width + x] = 230.toByte()
            }
        }
        return out
    }

    private fun shift(
        src: ByteArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int,
    ): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sx = (x - dx).coerceIn(0, width - 1)
                val sy = (y - dy).coerceIn(0, height - 1)
                out[y * width + x] = src[sy * width + sx]
            }
        }
        return out
    }
}

class GazeMotionPipelineTest {
    @Test
    fun latestGrayWinsAndDropsAreReportedOnEmit() {
        var now = 1_000L
        val pipeline =
            GazeMotionPipeline(
                nowMs = { now },
                intervalMs = 40L,
                flowWidth = 16,
                estimate = { _, curr ->
                    GazeFlowResult(u = curr.width / 16.0, v = 0.5, confidence = 0.7)
                },
            )
        pipeline.start()
        submitGray(pipeline, 32, 24)
        assertNull("first gray only seeds prev", pipeline.poll())

        now = 1_040L
        submitGray(pipeline, 32, 24)
        submitGray(pipeline, 32, 24)
        submitGray(pipeline, 32, 24)
        assertEquals(2, pipeline.overwrittenGrayCount)
        val delta = pipeline.poll()
        assertNotNull(delta)
        assertEquals(40L, delta!!.dtMs)
        assertEquals(1_040L, delta.tsMs)
        assertEquals(1_040L, delta.emitMs)
        assertEquals(2, delta.drops)
        assertEquals(0.7, delta.c, 0.0001)
        val (dx, _) = GazeMotionFlow.scaleTo480(16 / 16.0, 0.5, 16)
        assertEquals(dx, delta.dx, 0.0001)
        assertFalse(pipeline.pendingGray)
        assertNull("consumed gray must not re-emit", pipeline.poll())
        pipeline.stop()
    }

    @Test
    fun paceStaysInside20to30HzAndSkipsCompressed() {
        var now = 5_000L
        var estimates = 0
        var skips = 0
        val pipeline =
            GazeMotionPipeline(
                nowMs = { now },
                intervalMs = GazeMotion.TARGET_INTERVAL_MS,
                flowWidth = 12,
                estimate = { _, _ ->
                    estimates += 1
                    GazeFlowResult(0.0, 0.0, 0.4)
                },
                onCompressedSkip = { skips += 1 },
            )
        pipeline.start()
        pipeline.submit(16, 12, compressed = true, codecConfig = false, presentationTimeUs = 1L) {
            error("motion must not read HEVC bytes")
        }
        pipeline.submit(16, 12, compressed = false, codecConfig = true, presentationTimeUs = 2L) {
            error("motion must not read codec-config bytes")
        }
        assertEquals(2, skips)
        submitGray(pipeline, 16, 12)
        assertNull(pipeline.poll())
        now = 5_041L
        submitGray(pipeline, 16, 12)
        assertNotNull(pipeline.poll())
        submitGray(pipeline, 16, 12)
        now = 5_080L
        assertNull("must wait the 24 Hz interval", pipeline.poll())
        now = 5_082L
        submitGray(pipeline, 16, 12)
        assertNotNull(pipeline.poll())
        assertEquals(2, estimates)
        assertTrue(GazeMotion.TARGET_FPS in 20..30)
        pipeline.stop()
    }

    @Test
    fun largeGapResetsWithoutEmittingStaleDelta() {
        var now = 10L
        val pipeline =
            GazeMotionPipeline(
                nowMs = { now },
                intervalMs = 20L,
                flowWidth = 12,
                estimate = { _, _ -> GazeFlowResult(4.0, 0.0, 1.0) },
            )
        pipeline.start()
        submitGray(pipeline, 16, 12)
        assertNull(pipeline.poll())
        now = 400L
        submitGray(pipeline, 16, 12)
        assertNull("dt > MAX_DT_MS must drop the pair", pipeline.poll())
        now = 440L
        submitGray(pipeline, 16, 12)
        val delta = pipeline.poll()
        assertNotNull(delta)
        assertEquals(40L, delta!!.dtMs)
        pipeline.stop()
    }

    private fun submitGray(
        pipeline: GazeMotionPipeline,
        width: Int,
        height: Int,
    ) {
        val ySize = width * height
        pipeline.submit(
            width = width,
            height = height,
            compressed = false,
            codecConfig = false,
            presentationTimeUs = 0L,
        ) {
            ByteArray(ySize + ySize / 2) { 80 }
        }
    }
}

class GazeMotionMailboxTest {
    @Test
    fun latestWinsMotionMailboxDropsStaleJson() {
        val sent = mutableListOf<String>()
        val queued = ArrayDeque<() -> Unit>()
        val mailbox =
            GazeLatestSend<String>(
                deliver = {
                    sent += it
                    true
                },
                execute = { queued.add(it) },
            )
        mailbox.offer("""{"type":"motion","dx":1,"dy":0,"dt_ms":40,"ts_ms":1,"c":1}""")
        mailbox.offer("""{"type":"motion","dx":2,"dy":0,"dt_ms":40,"ts_ms":2,"c":1}""")
        mailbox.offer("""{"type":"motion","dx":3,"dy":0,"dt_ms":40,"ts_ms":3,"c":1}""")
        assertEquals(2, mailbox.dropCount)
        queued.removeFirst().invoke()
        assertEquals(1, sent.size)
        assertTrue(sent.single().contains("\"dx\":3"))
        assertEquals(1, mailbox.sentCount)
        assertFalse(mailbox.hasPending)
    }

    @Test
    fun inFlightMotionSendKeepsOnlyLatest() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sent = CopyOnWriteArrayList<String>()
        val mailbox =
            GazeLatestSend<String>(
                deliver = {
                    started.countDown()
                    assertTrue(release.await(2, TimeUnit.SECONDS))
                    sent += it
                    true
                },
                execute = { task ->
                    Thread(task, "gaze-motion-mailbox-test").apply {
                        isDaemon = true
                        start()
                    }
                },
            )
        mailbox.offer("m1")
        assertTrue(started.await(1, TimeUnit.SECONDS))
        mailbox.offer("m2")
        mailbox.offer("m3")
        release.countDown()
        val done = CountDownLatch(1)
        Thread {
            while (mailbox.busy || mailbox.hasPending) {
                Thread.sleep(5)
            }
            done.countDown()
        }.start()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals("m3", sent.last())
        assertFalse(sent.contains("m2"))
        assertTrue(mailbox.dropCount >= 1)
    }
}

class GazeMotionBridgeTest {
    @Test
    fun pumpEmitsMotionTextWithoutBreakingJpegMeta() {
        val hub = FakeHub()
        hub.clients.set(1)
        var now = 20_000L
        val jpeg =
            GazeJpegPipeline(
                encodeYuv = { byteArrayOf(0xFF.toByte(), 0xD8.toByte()) },
                nowMs = { now },
                intervalMs = 80L,
            )
        val motion =
            GazeMotionPipeline(
                nowMs = { now },
                intervalMs = 40L,
                flowWidth = 12,
                estimate = { _, _ -> GazeFlowResult(u = 1.0, v = -0.5, confidence = 0.6) },
            )
        val seenMotion = CountDownLatch(1)
        hub.onBroadcast = {
            if (hub.motions.isNotEmpty()) seenMotion.countDown()
        }
        val bridge =
            GazeBridge(
                server = hub,
                pipeline = jpeg,
                motion = motion,
                wifiIp = { "192.168.0.9" },
                sleeper = { Thread.sleep(3) },
            )
        bridge.sync(flagOn = true, streamLive = true)
        repeat(12) {
            bridge.submit(16, 12, compressed = false, codecConfig = false, presentationTimeUs = 1L) {
                ByteArray(16 * 12 * 3 / 2) { 90 }
            }
            now += 40L
            if (seenMotion.await(25, TimeUnit.MILLISECONDS)) return@repeat
        }
        assertTrue(seenMotion.await(400, TimeUnit.MILLISECONDS))
        assertTrue(hub.motions.isNotEmpty())
        val motionJson = hub.motions.first()
        assertTrue(motionJson.startsWith("""{"type":"motion""""))
        assertTrue(motionJson.contains("\"dt_ms\":"))
        assertTrue(motionJson.contains("\"emit_ms\":"))
        assertTrue(motionJson.contains("\"drops\":"))
        assertTrue(hub.texts.any { it.startsWith("""{"ts_ms":""") && it.contains("\"w\":16") })
        assertTrue(hub.binaries.firstOrNull()?.contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) == true)
        bridge.stop()
    }

    @Test
    fun compressedOnlyStreamNeverEmitsMotionOrJpeg() {
        val hub = FakeHub()
        hub.clients.set(1)
        val jpeg =
            GazeJpegPipeline(
                encodeYuv = { byteArrayOf(1) },
                nowMs = { 1_000L },
            )
        val motion =
            GazeMotionPipeline(
                nowMs = { 1_000L },
                estimate = { _, _ -> error("no flow on HEVC") },
            )
        val bridge =
            GazeBridge(
                server = hub,
                pipeline = jpeg,
                motion = motion,
                wifiIp = { "10.0.0.8" },
                sleeper = { Thread.sleep(2) },
            )
        bridge.sync(flagOn = true, streamLive = true)
        repeat(6) {
            bridge.submit(720, 1280, compressed = true, codecConfig = it == 0, presentationTimeUs = it * 40_000L) {
                error("compressed path must not read Image or HEVC bytes")
            }
            Thread.sleep(10)
        }
        assertTrue(hub.motions.isEmpty())
        assertTrue(hub.texts.isEmpty())
        assertTrue(hub.binaries.isEmpty())
        bridge.stop()
    }
}
