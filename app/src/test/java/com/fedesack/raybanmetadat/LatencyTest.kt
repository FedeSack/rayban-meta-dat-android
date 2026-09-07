package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyTest {
    @Test
    fun usesPresentationClockWhenPtsLooksLikeElapsedRealtime() {
        val nowMs = 60_000L
        val ptsUs = 59_850_000L
        val reading = Latency.reading(ptsUs, nowMs, receivedElapsedRealtimeMs = nowMs)
        assertEquals(150L, reading.millis)
        assertEquals(LatencyMode.GLASS, reading.mode)
        assertEquals(150L, Latency.millis(ptsUs, nowMs, receivedElapsedRealtimeMs = nowMs))
    }

    @Test
    fun fallsBackToReceiveToDisplayWhenPtsIsStreamRelative() {
        val reading =
            Latency.reading(
                presentationTimeUs = 40_000L,
                nowElapsedRealtimeMs = 90_012L,
                receivedElapsedRealtimeMs = 90_000L,
            )
        assertEquals(12L, reading.millis)
        assertEquals(LatencyMode.PIPELINE, reading.mode)
    }

    @Test
    fun treatsTenSecondOldPtsAsPipelineNotGlassClock() {
        val reading =
            Latency.reading(
                presentationTimeUs = 1_000L,
                nowElapsedRealtimeMs = 20_003L,
                receivedElapsedRealtimeMs = 20_000L,
            )
        assertEquals(3L, reading.millis)
        assertEquals(LatencyMode.PIPELINE, reading.mode)
    }

    @Test
    fun neverGoesNegative() {
        val reading =
            Latency.reading(
                presentationTimeUs = 0L,
                nowElapsedRealtimeMs = 10L,
                receivedElapsedRealtimeMs = 20L,
            )
        assertEquals(0L, reading.millis)
        assertEquals(LatencyMode.PIPELINE, reading.mode)
    }
}
