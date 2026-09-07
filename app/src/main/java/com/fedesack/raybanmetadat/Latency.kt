package com.fedesack.raybanmetadat

enum class LatencyMode {
    /** PTS looks like elapsedRealtime µs: capture → display. */
    GLASS,

    /** PTS is stream-relative: receive → draw. Not a glass clock. */
    PIPELINE,
}

data class LatencyReading(
    val millis: Long,
    val mode: LatencyMode,
)

object Latency {
    private const val MAX_GLASS_CLOCK_AGE_US = 10_000_000L

    fun reading(
        presentationTimeUs: Long,
        nowElapsedRealtimeMs: Long,
        receivedElapsedRealtimeMs: Long,
    ): LatencyReading {
        val nowUs = nowElapsedRealtimeMs * 1_000L
        val ageUs = nowUs - presentationTimeUs
        if (presentationTimeUs > 0L && ageUs in 0L..MAX_GLASS_CLOCK_AGE_US) {
            return LatencyReading(ageUs / 1_000L, LatencyMode.GLASS)
        }
        return LatencyReading(
            (nowElapsedRealtimeMs - receivedElapsedRealtimeMs).coerceAtLeast(0L),
            LatencyMode.PIPELINE,
        )
    }

    fun millis(
        presentationTimeUs: Long,
        nowElapsedRealtimeMs: Long,
        receivedElapsedRealtimeMs: Long,
    ): Long = reading(presentationTimeUs, nowElapsedRealtimeMs, receivedElapsedRealtimeMs).millis
}
