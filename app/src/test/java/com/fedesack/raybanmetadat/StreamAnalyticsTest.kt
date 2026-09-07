package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAnalyticsTest {
    @Test
    fun interArrivalIgnoresNonPositiveGaps() {
        assertEquals(42L, StreamAnalyticsMath.interArrivalMs(100, 142))
        assertNull(StreamAnalyticsMath.interArrivalMs(100, 100))
        assertNull(StreamAnalyticsMath.interArrivalMs(100, 90))
    }

    @Test
    fun estimatedFpsFromUniformIntervals() {
        val fps = StreamAnalyticsMath.estimatedFps(listOf(40L, 40L, 40L, 40L))
        assertEquals(25.0, fps!!, 0.01)
    }

    @Test
    fun estimatedFpsMissingUntilTwoArrivals() {
        assertNull(StreamAnalyticsMath.estimatedFps(emptyList()))
    }

    @Test
    fun timeToFirstFrameFromStart() {
        assertEquals(1840L, StreamAnalyticsMath.timeToFirstMs(1_000L, 2_840L))
        assertEquals(0L, StreamAnalyticsMath.timeToFirstMs(50L, 50L))
        assertNull(StreamAnalyticsMath.timeToFirstMs(80L, 40L))
    }

    @Test
    fun resolutionChangeNeedsAPreviousSize() {
        assertFalse(StreamAnalyticsMath.resolutionChanged(null, null, 1280, 720))
        assertFalse(StreamAnalyticsMath.resolutionChanged(1280, 720, 1280, 720))
        assertTrue(StreamAnalyticsMath.resolutionChanged(1280, 720, 720, 1280))
        assertFalse(StreamAnalyticsMath.resolutionChanged(1280, 720, 0, 0))
    }

    @Test
    fun sessionTracksTtffFpsAndResolutionWithoutInventingBitrate() {
        val analytics = StreamSessionAnalytics(fpsWindow = 4, eventCapacity = 8)
        analytics.start(1_000L, configuredQuality = "HIGH", configuredFps = 24, compressVideo = true)
        analytics.onSessionState("STARTED", 1_010L)
        analytics.onStreamState("STARTING", 1_020L)
        analytics.onStreamState("STREAMING", 1_040L)
        analytics.onFrameArrived(1_200L, 1280, 720, DecodePath.HEVC)
        analytics.onFrameArrived(1_242L, 1280, 720, DecodePath.HEVC)
        analytics.onFrameArrived(1_284L, 720, 1280, DecodePath.HEVC)
        analytics.onPresented(2_840L, LatencyReading(12L, LatencyMode.PIPELINE))

        val snap = analytics.snapshot()
        assertEquals(1840L, snap.timeToFirstFrameMs)
        assertEquals(200L, snap.timeToFirstArrivalMs)
        assertEquals(3L, snap.framesArrived)
        assertEquals(1L, snap.framesPresented)
        assertEquals(12L, snap.latencyMs)
        assertEquals(LatencyMode.PIPELINE, snap.latencyMode)
        assertEquals(42L, snap.interArrivalMs)
        assertEquals(1000.0 / 42.0, snap.estimatedFps!!, 0.01)
        assertEquals(720, snap.width)
        assertEquals(1280, snap.height)
        assertEquals(1, snap.resolutionChanges)
        assertEquals(DecodePath.HEVC, snap.decodePath)
        assertEquals("HIGH", snap.configuredQuality)
        assertEquals(24, snap.configuredFps)
        assertEquals(true, snap.compressVideo)
        assertEquals("STREAMING", snap.streamState)
        assertEquals("STARTED", snap.sessionState)
        assertTrue(snap.running)

        val summary = analytics.stop(3_000L)!!
        assertEquals(2000L, summary.durationMs)
        assertEquals(1840L, summary.timeToFirstFrameMs)
        assertEquals(3L, summary.framesArrived)
        assertFalse(analytics.snapshot().running)
        assertTrue(summary.toFields().containsKey("fps"))
        assertFalse(summary.toFields().containsKey("bitrate"))
    }

    @Test
    fun startRecordsConfiguredQualityAndFpsFromCaller() {
        val analytics = StreamSessionAnalytics()
        analytics.start(5L, configuredQuality = "LOW", configuredFps = 15, compressVideo = true)
        val snap = analytics.snapshot()
        assertEquals("LOW", snap.configuredQuality)
        assertEquals(15, snap.configuredFps)
        assertEquals(true, snap.compressVideo)
        assertTrue(snap.running)
        val line =
            AnalyticsLog.line(
                "start",
                mapOf(
                    "cfgQuality" to snap.configuredQuality,
                    "cfgFps" to snap.configuredFps,
                    "compress" to snap.compressVideo,
                    "preferSharpness" to true,
                ),
            )
        assertEquals("start cfgQuality=LOW cfgFps=15 compress=true preferSharpness=true", line)
    }

    @Test
    fun startResetsPreviousSession() {
        val analytics = StreamSessionAnalytics()
        analytics.start(0L, "HIGH", 24, true)
        analytics.onFrameArrived(40L, 1280, 720, DecodePath.YUV)
        analytics.onPresented(50L, LatencyReading(4L, LatencyMode.PIPELINE))
        analytics.stop(80L)
        analytics.start(100L, "HIGH", 24, true)
        val snap = analytics.snapshot()
        assertEquals(0L, snap.framesArrived)
        assertEquals(0L, snap.framesPresented)
        assertNull(snap.timeToFirstFrameMs)
        assertTrue(snap.running)
    }

    @Test
    fun ringBufferDropsOldestEvents() {
        val analytics = StreamSessionAnalytics(eventCapacity = 3)
        analytics.start(0L, "HIGH", 24, true)
        analytics.onSessionState("STARTED", 1L)
        analytics.onStreamState("STARTING", 2L)
        analytics.onStreamState("STREAMING", 3L)
        val kinds = analytics.events().map { it.kind }
        assertEquals(listOf("session", "stream", "stream"), kinds)
    }

    @Test
    fun stopWithoutStartOrAfterStopReturnsNull() {
        val analytics = StreamSessionAnalytics()
        assertNull(analytics.stop(10L))
        analytics.start(0L, "HIGH", 24, true)
        assertTrue(analytics.stop(20L) != null)
        assertNull(analytics.stop(30L))
    }

    @Test
    fun analyticsLineOmitsNullsAndFormatsDoubles() {
        val line =
            AnalyticsLog.line(
                "stats",
                mapOf(
                    "fps" to 23.8123,
                    "bitrate" to null,
                    "path" to DecodePath.HEVC,
                ),
            )
        assertEquals("stats fps=23.81 path=HEVC", line)
        assertEquals("RaybanDat/Analytics", AnalyticsLog.TAG)
    }
}
