package com.fedesack.raybanmetadat

object Latency {
    private const val MAX_GLASS_CLOCK_AGE_US = 10_000_000L

    fun millis(
        presentationTimeUs: Long,
        nowElapsedRealtimeMs: Long,
        receivedElapsedRealtimeMs: Long,
    ): Long {
        val nowUs = nowElapsedRealtimeMs * 1_000L
        val ageUs = nowUs - presentationTimeUs
        if (presentationTimeUs > 0L && ageUs in 0L..MAX_GLASS_CLOCK_AGE_US) {
            return ageUs / 1_000L
        }
        return (nowElapsedRealtimeMs - receivedElapsedRealtimeMs).coerceAtLeast(0L)
    }
}
