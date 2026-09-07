package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyTest {
    @Test
    fun usesPresentationClockWhenPtsLooksLikeElapsedRealtime() {
        val nowMs = 60_000L
        val ptsUs = 59_850_000L
        assertEquals(150L, Latency.millis(ptsUs, nowMs, receivedElapsedRealtimeMs = nowMs))
    }

    @Test
    fun fallsBackToReceiveToDisplayWhenPtsIsStreamRelative() {
        assertEquals(
            12L,
            Latency.millis(
                presentationTimeUs = 40_000L,
                nowElapsedRealtimeMs = 90_012L,
                receivedElapsedRealtimeMs = 90_000L,
            ),
        )
    }

    @Test
    fun treatsTenSecondOldPtsAsPipelineNotGlassClock() {
        assertEquals(
            3L,
            Latency.millis(
                presentationTimeUs = 1_000L,
                nowElapsedRealtimeMs = 20_003L,
                receivedElapsedRealtimeMs = 20_000L,
            ),
        )
    }

    @Test
    fun neverGoesNegative() {
        assertEquals(
            0L,
            Latency.millis(
                presentationTimeUs = 0L,
                nowElapsedRealtimeMs = 10L,
                receivedElapsedRealtimeMs = 20L,
            ),
        )
    }
}
