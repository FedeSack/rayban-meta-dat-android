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
    val hasFrame: Boolean = false,
    val message: String? = null,
) {
    val canOpenLive: Boolean
        get() = androidReady && (source == DeviceSource.MOCK || registered)

    val streamLive: Boolean
        get() = stream == StreamState.STREAMING
}
