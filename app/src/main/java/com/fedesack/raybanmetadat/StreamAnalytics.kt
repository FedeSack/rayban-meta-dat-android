package com.fedesack.raybanmetadat

import java.util.Locale

enum class DecodePath {
    HEVC,
    YUV,
}

data class AnalyticsEvent(
    val atElapsedMs: Long,
    val kind: String,
    val detail: String,
)

data class AnalyticsSnapshot(
    val latencyMs: Long? = null,
    val latencyMode: LatencyMode? = null,
    val interArrivalMs: Long? = null,
    val estimatedFps: Double? = null,
    val timeToFirstFrameMs: Long? = null,
    val timeToFirstArrivalMs: Long? = null,
    val sessionState: String? = null,
    val streamState: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val framesArrived: Long = 0,
    val framesPresented: Long = 0,
    val queueDrops: Long = 0,
    val decodeErrors: Long = 0,
    val decodePath: DecodePath? = null,
    val resolutionChanges: Int = 0,
    val sessionTransitions: Int = 0,
    val streamTransitions: Int = 0,
    val configuredQuality: String? = null,
    val configuredFps: Int? = null,
    val compressVideo: Boolean? = null,
    val lastEvent: String? = null,
    val running: Boolean = false,
)

data class SessionSummary(
    val durationMs: Long,
    val timeToFirstFrameMs: Long?,
    val timeToFirstArrivalMs: Long?,
    val framesArrived: Long,
    val framesPresented: Long,
    val estimatedFps: Double?,
    val meanInterArrivalMs: Double?,
    val width: Int?,
    val height: Int?,
    val decodePath: DecodePath?,
    val resolutionChanges: Int,
    val queueDrops: Long,
    val decodeErrors: Long,
    val sessionTransitions: Int,
    val streamTransitions: Int,
    val configuredQuality: String?,
    val configuredFps: Int?,
    val compressVideo: Boolean?,
    val events: List<AnalyticsEvent>,
) {
    fun toFields(): Map<String, Any?> =
        mapOf(
            "durationMs" to durationMs,
            "ttffMs" to timeToFirstFrameMs,
            "ttaMs" to timeToFirstArrivalMs,
            "arrived" to framesArrived,
            "presented" to framesPresented,
            "fps" to estimatedFps,
            "meanGapMs" to meanInterArrivalMs,
            "w" to width,
            "h" to height,
            "path" to decodePath?.name,
            "resChanges" to resolutionChanges,
            "drops" to queueDrops,
            "decodeErrors" to decodeErrors,
            "sessionTx" to sessionTransitions,
            "streamTx" to streamTransitions,
            "cfgQuality" to configuredQuality,
            "cfgFps" to configuredFps,
            "compress" to compressVideo,
            "events" to events.size,
        )
}

object StreamAnalyticsMath {
    fun interArrivalMs(
        previousElapsedMs: Long,
        currentElapsedMs: Long,
    ): Long? {
        val delta = currentElapsedMs - previousElapsedMs
        return if (delta > 0L) delta else null
    }

    fun estimatedFps(intervalsMs: Collection<Long>): Double? {
        if (intervalsMs.isEmpty()) return null
        val mean = intervalsMs.average()
        if (mean <= 0.0) return null
        return 1000.0 / mean
    }

    fun timeToFirstMs(
        startElapsedMs: Long,
        firstElapsedMs: Long,
    ): Long? {
        val delta = firstElapsedMs - startElapsedMs
        return if (delta >= 0L) delta else null
    }

    fun resolutionChanged(
        previousWidth: Int?,
        previousHeight: Int?,
        width: Int,
        height: Int,
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        if (previousWidth == null || previousHeight == null) return false
        return previousWidth != width || previousHeight != height
    }

    fun formatFps(fps: Double): String = String.format(Locale.US, "%.1f", fps)
}

object AnalyticsLog {
    const val TAG = "RaybanDat/Analytics"

    fun line(
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    ): String {
        val extras =
            fields.entries
                .filter { it.value != null }
                .joinToString(" ") { (key, value) ->
                    val rendered =
                        when (value) {
                            is Double -> String.format(Locale.US, "%.2f", value)
                            else -> value.toString()
                        }
                    "$key=$rendered"
                }
        return if (extras.isEmpty()) event else "$event $extras"
    }
}

class StreamSessionAnalytics(
    private val fpsWindow: Int = 24,
    private val eventCapacity: Int = 128,
) {
    private val lock = Any()
    private var startedAt: Long? = null
    private var firstArrivalAt: Long? = null
    private var firstPresentedAt: Long? = null
    private var lastArrivalAt: Long? = null
    private var lastPresentedAt: Long? = null
    private var lastWidth: Int? = null
    private var lastHeight: Int? = null
    private var sessionState: String? = null
    private var streamState: String? = null
    private var decodePath: DecodePath? = null
    private var latencyMs: Long? = null
    private var latencyMode: LatencyMode? = null
    private var lastInterArrivalMs: Long? = null
    private var framesArrived = 0L
    private var framesPresented = 0L
    private var queueDrops = 0L
    private var decodeErrors = 0L
    private var resolutionChanges = 0
    private var sessionTransitions = 0
    private var streamTransitions = 0
    private var configuredQuality: String? = null
    private var configuredFps: Int? = null
    private var compressVideo: Boolean? = null
    private var lastEvent: String? = null
    private var running = false
    private val arrivalIntervals = ArrayDeque<Long>()
    private val events = ArrayDeque<AnalyticsEvent>()

    fun start(
        atElapsedMs: Long,
        configuredQuality: String,
        configuredFps: Int,
        compressVideo: Boolean,
    ) {
        synchronized(lock) {
            resetLocked()
            startedAt = atElapsedMs
            this.configuredQuality = configuredQuality
            this.configuredFps = configuredFps
            this.compressVideo = compressVideo
            running = true
            recordLocked(atElapsedMs, "start", "cfg=$configuredQuality fps=$configuredFps compress=$compressVideo")
        }
    }

    fun onSessionState(
        state: String,
        atElapsedMs: Long,
    ) {
        synchronized(lock) {
            if (sessionState == state) return
            sessionState = state
            sessionTransitions += 1
            recordLocked(atElapsedMs, "session", state)
        }
    }

    fun onStreamState(
        state: String,
        atElapsedMs: Long,
    ) {
        synchronized(lock) {
            if (streamState == state) return
            streamState = state
            streamTransitions += 1
            recordLocked(atElapsedMs, "stream", state)
        }
    }

    fun onFrameArrived(
        atElapsedMs: Long,
        width: Int,
        height: Int,
        path: DecodePath,
    ): ArrivalResult {
        synchronized(lock) {
            val first = firstArrivalAt == null
            if (first) {
                firstArrivalAt = atElapsedMs
                recordLocked(atElapsedMs, "first_arrival", "${width}x$height $path")
            }
            lastArrivalAt?.let { previous ->
                StreamAnalyticsMath.interArrivalMs(previous, atElapsedMs)?.let { gap ->
                    lastInterArrivalMs = gap
                    if (arrivalIntervals.size == fpsWindow) arrivalIntervals.removeFirst()
                    arrivalIntervals.addLast(gap)
                }
            }
            val changed = StreamAnalyticsMath.resolutionChanged(lastWidth, lastHeight, width, height)
            if (changed) {
                resolutionChanges += 1
                recordLocked(atElapsedMs, "resolution", "${lastWidth}x$lastHeight→${width}x$height")
            }
            if (width > 0 && height > 0) {
                lastWidth = width
                lastHeight = height
            }
            decodePath = path
            lastArrivalAt = atElapsedMs
            framesArrived += 1
            return ArrivalResult(first = first, resolutionChanged = changed)
        }
    }

    fun onPresented(
        atElapsedMs: Long,
        reading: LatencyReading,
    ): Long {
        synchronized(lock) {
            if (firstPresentedAt == null) {
                firstPresentedAt = atElapsedMs
                val ttff = startedAt?.let { StreamAnalyticsMath.timeToFirstMs(it, atElapsedMs) }
                recordLocked(atElapsedMs, "first_frame", "ttffMs=$ttff")
            }
            lastPresentedAt = atElapsedMs
            latencyMs = reading.millis
            latencyMode = reading.mode
            framesPresented += 1
            return framesPresented
        }
    }

    fun onQueueDrop(atElapsedMs: Long) {
        synchronized(lock) {
            queueDrops += 1
            recordLocked(atElapsedMs, "drop", "queue=$queueDrops")
        }
    }

    fun onDecodeError(
        atElapsedMs: Long,
        message: String,
    ) {
        synchronized(lock) {
            decodeErrors += 1
            recordLocked(atElapsedMs, "decode_error", message)
        }
    }

    fun onDecoderOutput(
        width: Int,
        height: Int,
        atElapsedMs: Long,
    ) {
        synchronized(lock) {
            val changed = StreamAnalyticsMath.resolutionChanged(lastWidth, lastHeight, width, height)
            if (changed) {
                resolutionChanges += 1
                recordLocked(atElapsedMs, "decoder_out", "${lastWidth}x$lastHeight→${width}x$height")
            }
            if (lastWidth == null && width > 0 && height > 0) {
                lastWidth = width
                lastHeight = height
                recordLocked(atElapsedMs, "decoder_out", "${width}x$height")
            }
        }
    }

    fun stop(atElapsedMs: Long): SessionSummary? {
        synchronized(lock) {
            if (!running) return null
            running = false
            val summary = summaryLocked(atElapsedMs)
            recordLocked(atElapsedMs, "stop", "presented=${summary.framesPresented}")
            return summary
        }
    }

    fun snapshot(): AnalyticsSnapshot =
        synchronized(lock) {
            val start = startedAt
            val firstPresented = firstPresentedAt
            val firstArrival = firstArrivalAt
            AnalyticsSnapshot(
                latencyMs = latencyMs,
                latencyMode = latencyMode,
                interArrivalMs = lastInterArrivalMs,
                estimatedFps = StreamAnalyticsMath.estimatedFps(arrivalIntervals),
                timeToFirstFrameMs =
                    if (start != null && firstPresented != null) {
                        StreamAnalyticsMath.timeToFirstMs(start, firstPresented)
                    } else {
                        null
                    },
                timeToFirstArrivalMs =
                    if (start != null && firstArrival != null) {
                        StreamAnalyticsMath.timeToFirstMs(start, firstArrival)
                    } else {
                        null
                    },
                sessionState = sessionState,
                streamState = streamState,
                width = lastWidth,
                height = lastHeight,
                framesArrived = framesArrived,
                framesPresented = framesPresented,
                queueDrops = queueDrops,
                decodeErrors = decodeErrors,
                decodePath = decodePath,
                resolutionChanges = resolutionChanges,
                sessionTransitions = sessionTransitions,
                streamTransitions = streamTransitions,
                configuredQuality = configuredQuality,
                configuredFps = configuredFps,
                compressVideo = compressVideo,
                lastEvent = lastEvent,
                running = running,
            )
        }

    fun events(): List<AnalyticsEvent> = synchronized(lock) { events.toList() }

    private fun summaryLocked(atElapsedMs: Long): SessionSummary {
        val start = startedAt ?: atElapsedMs
        return SessionSummary(
            durationMs = (atElapsedMs - start).coerceAtLeast(0L),
            timeToFirstFrameMs =
                firstPresentedAt?.let { presented ->
                    StreamAnalyticsMath.timeToFirstMs(start, presented)
                },
            timeToFirstArrivalMs =
                firstArrivalAt?.let { arrival ->
                    StreamAnalyticsMath.timeToFirstMs(start, arrival)
                },
            framesArrived = framesArrived,
            framesPresented = framesPresented,
            estimatedFps = StreamAnalyticsMath.estimatedFps(arrivalIntervals),
            meanInterArrivalMs = if (arrivalIntervals.isEmpty()) null else arrivalIntervals.average(),
            width = lastWidth,
            height = lastHeight,
            decodePath = decodePath,
            resolutionChanges = resolutionChanges,
            queueDrops = queueDrops,
            decodeErrors = decodeErrors,
            sessionTransitions = sessionTransitions,
            streamTransitions = streamTransitions,
            configuredQuality = configuredQuality,
            configuredFps = configuredFps,
            compressVideo = compressVideo,
            events = events.toList(),
        )
    }

    private fun recordLocked(
        atElapsedMs: Long,
        kind: String,
        detail: String,
    ) {
        lastEvent = "$kind $detail"
        if (events.size == eventCapacity) events.removeFirst()
        events.addLast(AnalyticsEvent(atElapsedMs, kind, detail))
    }

    private fun resetLocked() {
        startedAt = null
        firstArrivalAt = null
        firstPresentedAt = null
        lastArrivalAt = null
        lastPresentedAt = null
        lastWidth = null
        lastHeight = null
        sessionState = null
        streamState = null
        decodePath = null
        latencyMs = null
        latencyMode = null
        lastInterArrivalMs = null
        framesArrived = 0L
        framesPresented = 0L
        queueDrops = 0L
        decodeErrors = 0L
        resolutionChanges = 0
        sessionTransitions = 0
        streamTransitions = 0
        configuredQuality = null
        configuredFps = null
        compressVideo = null
        lastEvent = null
        running = false
        arrivalIntervals.clear()
        events.clear()
    }
}

data class ArrivalResult(
    val first: Boolean,
    val resolutionChanged: Boolean,
)
