package com.fedesack.raybanmetadat

import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.core.session.DeviceSessionState

enum class Phase {
    CONNECT,
    LIVE,
}

enum class DeviceSource {
    META_AI,
    MOCK,
}

data class AppState(
    val phase: Phase = Phase.CONNECT,
    val source: DeviceSource = DeviceSource.META_AI,
    val androidReady: Boolean = false,
    val registered: Boolean = false,
    val registrationLabel: String = "sin registro",
    val session: DeviceSessionState = DeviceSessionState.IDLE,
    val stream: StreamState = StreamState.STOPPED,
    val latencyMs: Long? = null,
    val latencyMode: LatencyMode? = null,
    val hasFrame: Boolean = false,
    val awaitingFirstFrame: Boolean = false,
    val message: String? = null,
    val analytics: AnalyticsSnapshot = AnalyticsSnapshot(),
    val flags: FeatureFlags = FeatureFlags(),
    val captures: List<BoardCapture> = emptyList(),
    val capturing: Boolean = false,
    val intentWebhookUrl: String = "",
    val queuedIntentCount: Int = 0,
    val lastIntentStatus: String? = null,
    val gazeEndpoint: String? = null,
    val gazeListening: Boolean = false,
) {
    val canOpenLive: Boolean
        get() = androidReady && (source == DeviceSource.MOCK || registered)
}
