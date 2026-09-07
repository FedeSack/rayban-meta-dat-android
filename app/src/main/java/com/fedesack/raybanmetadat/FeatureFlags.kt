package com.fedesack.raybanmetadat

enum class FeatureFlag(
    val key: String,
    val default: Boolean,
    val stub: Boolean = false,
) {
    ANALYTICS_OVERLAY("analyticsOverlay", default = false),
    VERBOSE_LOGCAT("verboseLogcat", default = false),
    PREFER_SHARPNESS("preferSharpness", default = false),
    MURDOKU_HQ_CAPTURE("murdokuHqCapture", default = false),
    GAZE_BRIDGE("gazeBridge", default = false, stub = true),
    VOICE_ASSIST("voiceAssist", default = false, stub = true),
    VOICE_DEV_MODE("voiceDevMode", default = false, stub = true),
}

enum class VideoQualityFlag {
    HIGH,
    MEDIUM,
    LOW,
    ;

    val key: String get() = name

    companion object {
        val DEFAULT = HIGH

        fun parse(raw: String?): VideoQualityFlag {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return DEFAULT
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class FrameRateFlag(
    val fps: Int,
) {
    FPS_15(15),
    FPS_24(24),
    FPS_30(30),
    ;

    companion object {
        val DEFAULT = FPS_24

        fun parse(raw: Int?): FrameRateFlag =
            entries.firstOrNull { it.fps == raw } ?: DEFAULT

        fun parse(raw: String?): FrameRateFlag = parse(raw?.trim()?.toIntOrNull())
    }
}

data class FeatureFlags(
    val analyticsOverlay: Boolean = FeatureFlag.ANALYTICS_OVERLAY.default,
    val verboseLogcat: Boolean = FeatureFlag.VERBOSE_LOGCAT.default,
    val preferSharpness: Boolean = FeatureFlag.PREFER_SHARPNESS.default,
    val murdokuHqCapture: Boolean = FeatureFlag.MURDOKU_HQ_CAPTURE.default,
    val gazeBridge: Boolean = FeatureFlag.GAZE_BRIDGE.default,
    val voiceAssist: Boolean = FeatureFlag.VOICE_ASSIST.default,
    val voiceDevMode: Boolean = FeatureFlag.VOICE_DEV_MODE.default,
    val videoQuality: VideoQualityFlag = VideoQualityFlag.DEFAULT,
    val frameRate: FrameRateFlag = FrameRateFlag.DEFAULT,
) {
    fun enabled(flag: FeatureFlag): Boolean =
        when (flag) {
            FeatureFlag.ANALYTICS_OVERLAY -> analyticsOverlay
            FeatureFlag.VERBOSE_LOGCAT -> verboseLogcat
            FeatureFlag.PREFER_SHARPNESS -> preferSharpness
            FeatureFlag.MURDOKU_HQ_CAPTURE -> murdokuHqCapture
            FeatureFlag.GAZE_BRIDGE -> gazeBridge
            FeatureFlag.VOICE_ASSIST -> voiceAssist
            FeatureFlag.VOICE_DEV_MODE -> voiceDevMode
        }

    fun streamConfig(): StreamCaptureConfig =
        if (murdokuHqCapture) {
            StreamCaptureConfig(
                quality = VideoQualityFlag.HIGH,
                frameRate = FrameRateFlag.FPS_15,
                compressVideo = true,
                preferSharpness = true,
                murdokuHq = true,
            )
        } else {
            StreamCaptureConfig(
                quality = videoQuality,
                frameRate = frameRate,
                preferSharpness = preferSharpness,
            )
        }
}

data class StreamCaptureConfig(
    val quality: VideoQualityFlag = VideoQualityFlag.DEFAULT,
    val frameRate: FrameRateFlag = FrameRateFlag.DEFAULT,
    val compressVideo: Boolean = true,
    val preferSharpness: Boolean = false,
    val murdokuHq: Boolean = false,
) {
    val qualityName: String get() = quality.key
    val fps: Int get() = frameRate.fps
}

object FeatureFlagsCatalog {
    const val VIDEO_QUALITY_KEY = "videoQuality"
    const val FRAME_RATE_KEY = "frameRate"
    const val INTENT_WEBHOOK_URL_KEY = "intentWebhookUrl"

    fun apply(
        current: FeatureFlags,
        flag: FeatureFlag,
        enabled: Boolean,
    ): FeatureFlags =
        when (flag) {
            FeatureFlag.ANALYTICS_OVERLAY -> current.copy(analyticsOverlay = enabled)
            FeatureFlag.VERBOSE_LOGCAT -> current.copy(verboseLogcat = enabled)
            FeatureFlag.PREFER_SHARPNESS -> current.copy(preferSharpness = enabled)
            FeatureFlag.MURDOKU_HQ_CAPTURE -> current.copy(murdokuHqCapture = enabled)
            FeatureFlag.GAZE_BRIDGE -> current.copy(gazeBridge = enabled)
            FeatureFlag.VOICE_ASSIST -> current.copy(voiceAssist = enabled)
            FeatureFlag.VOICE_DEV_MODE -> current.copy(voiceDevMode = enabled)
        }

    fun applyQuality(
        current: FeatureFlags,
        quality: VideoQualityFlag,
    ): FeatureFlags = current.copy(videoQuality = quality)

    fun applyFrameRate(
        current: FeatureFlags,
        frameRate: FrameRateFlag,
    ): FeatureFlags = current.copy(frameRate = frameRate)

    fun fromStored(getBoolean: (key: String, default: Boolean) -> Boolean): FeatureFlags =
        fromStored(
            getBoolean = getBoolean,
            getString = { _, default -> default },
            getInt = { _, default -> default },
        )

    fun fromStored(
        getBoolean: (key: String, default: Boolean) -> Boolean,
        getString: (key: String, default: String) -> String,
        getInt: (key: String, default: Int) -> Int,
    ): FeatureFlags =
        FeatureFlags(
            analyticsOverlay = getBoolean(FeatureFlag.ANALYTICS_OVERLAY.key, FeatureFlag.ANALYTICS_OVERLAY.default),
            verboseLogcat = getBoolean(FeatureFlag.VERBOSE_LOGCAT.key, FeatureFlag.VERBOSE_LOGCAT.default),
            preferSharpness = getBoolean(FeatureFlag.PREFER_SHARPNESS.key, FeatureFlag.PREFER_SHARPNESS.default),
            murdokuHqCapture = getBoolean(FeatureFlag.MURDOKU_HQ_CAPTURE.key, FeatureFlag.MURDOKU_HQ_CAPTURE.default),
            gazeBridge = getBoolean(FeatureFlag.GAZE_BRIDGE.key, FeatureFlag.GAZE_BRIDGE.default),
            voiceAssist = getBoolean(FeatureFlag.VOICE_ASSIST.key, FeatureFlag.VOICE_ASSIST.default),
            voiceDevMode = getBoolean(FeatureFlag.VOICE_DEV_MODE.key, FeatureFlag.VOICE_DEV_MODE.default),
            videoQuality = VideoQualityFlag.parse(getString(VIDEO_QUALITY_KEY, VideoQualityFlag.DEFAULT.key)),
            frameRate = FrameRateFlag.parse(getInt(FRAME_RATE_KEY, FrameRateFlag.DEFAULT.fps)),
        )

    fun fromStoredValues(stored: Map<String, Any?>): FeatureFlags =
        fromStored(
            getBoolean = { key, default -> (stored[key] as? Boolean) ?: default },
            getString = { key, default ->
                when (val value = stored[key]) {
                    is String -> value
                    else -> default
                }
            },
            getInt = { key, default ->
                when (val value = stored[key]) {
                    is Int -> value
                    is String -> value.toIntOrNull() ?: default
                    else -> default
                }
            },
        )

    fun toMap(flags: FeatureFlags): Map<String, Boolean> =
        FeatureFlag.entries.associate { it.key to flags.enabled(it) }

    fun streamSettings(flags: FeatureFlags): Map<String, Any> {
        val config = flags.streamConfig()
        return mapOf(
            VIDEO_QUALITY_KEY to config.qualityName,
            FRAME_RATE_KEY to config.fps,
            FeatureFlag.PREFER_SHARPNESS.key to config.preferSharpness,
            FeatureFlag.MURDOKU_HQ_CAPTURE.key to config.murdokuHq,
        )
    }
}
