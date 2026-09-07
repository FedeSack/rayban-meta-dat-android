package com.fedesack.raybanmetadat

import java.time.Instant
import java.util.UUID

enum class IntentSource(val json: String) {
    DAT("dat"),
    MIC("mic"),
    CHAT("chat"),
    ;

    companion object {
        fun parse(raw: String?): IntentSource =
            entries.firstOrNull { it.json.equals(raw?.trim(), ignoreCase = true) } ?: CHAT
    }
}

enum class IntentPrefer(val json: String) {
    SKILL("skill"),
    APK("apk"),
    AUTO("auto"),
    ;

    companion object {
        val DEFAULT = AUTO

        fun parse(raw: String?): IntentPrefer =
            entries.firstOrNull { it.json.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

data class VoiceIntent(
    val id: String,
    val utterance: String,
    val source: IntentSource,
    val ts: String,
    val deviceId: String,
    val appVersion: String,
    val voiceDevMode: Boolean,
    val prefer: IntentPrefer = IntentPrefer.DEFAULT,
) {
    companion object {
        fun create(
            utterance: String,
            source: IntentSource,
            deviceId: String,
            appVersion: String,
            voiceDevMode: Boolean,
            prefer: IntentPrefer = IntentPrefer.DEFAULT,
            id: String = UUID.randomUUID().toString(),
            ts: String = Instant.now().toString(),
        ): VoiceIntent =
            VoiceIntent(
                id = id,
                utterance = utterance.trim(),
                source = source,
                ts = ts,
                deviceId = deviceId,
                appVersion = appVersion,
                voiceDevMode = voiceDevMode,
                prefer = prefer,
            )
    }
}

enum class DevIntentStatus(val json: String) {
    QUEUED("queued"),
    BUILDING("building"),
    READY("ready"),
    FAILED("failed"),
    ;

    companion object {
        fun parse(raw: String?): DevIntentStatus? =
            entries.firstOrNull { it.json.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class DevIntentKind(val json: String) {
    SKILL("skill"),
    APK("apk"),
    ;

    companion object {
        fun parse(raw: String?): DevIntentKind? =
            entries.firstOrNull { it.json.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class DevIntentAck(
    val intentId: String,
    val status: DevIntentStatus,
    val kind: DevIntentKind? = null,
    val prUrl: String? = null,
    val commit: String? = null,
    val skillId: String? = null,
    val message: String? = null,
)
