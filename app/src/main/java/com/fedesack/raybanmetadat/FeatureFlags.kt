package com.fedesack.raybanmetadat

enum class FeatureFlag(
    val key: String,
    val default: Boolean,
    val stub: Boolean = false,
) {
    ANALYTICS_OVERLAY("analyticsOverlay", default = false),
    VERBOSE_LOGCAT("verboseLogcat", default = false),
    GAZE_BRIDGE("gazeBridge", default = false, stub = true),
    VOICE_ASSIST("voiceAssist", default = false, stub = true),
}

data class FeatureFlags(
    val analyticsOverlay: Boolean = FeatureFlag.ANALYTICS_OVERLAY.default,
    val verboseLogcat: Boolean = FeatureFlag.VERBOSE_LOGCAT.default,
    val gazeBridge: Boolean = FeatureFlag.GAZE_BRIDGE.default,
    val voiceAssist: Boolean = FeatureFlag.VOICE_ASSIST.default,
) {
    fun enabled(flag: FeatureFlag): Boolean =
        when (flag) {
            FeatureFlag.ANALYTICS_OVERLAY -> analyticsOverlay
            FeatureFlag.VERBOSE_LOGCAT -> verboseLogcat
            FeatureFlag.GAZE_BRIDGE -> gazeBridge
            FeatureFlag.VOICE_ASSIST -> voiceAssist
        }
}

object FeatureFlagsCatalog {
    fun apply(
        current: FeatureFlags,
        flag: FeatureFlag,
        enabled: Boolean,
    ): FeatureFlags =
        when (flag) {
            FeatureFlag.ANALYTICS_OVERLAY -> current.copy(analyticsOverlay = enabled)
            FeatureFlag.VERBOSE_LOGCAT -> current.copy(verboseLogcat = enabled)
            FeatureFlag.GAZE_BRIDGE -> current.copy(gazeBridge = enabled)
            FeatureFlag.VOICE_ASSIST -> current.copy(voiceAssist = enabled)
        }

    fun fromStored(get: (key: String, default: Boolean) -> Boolean): FeatureFlags =
        FeatureFlags(
            analyticsOverlay = get(FeatureFlag.ANALYTICS_OVERLAY.key, FeatureFlag.ANALYTICS_OVERLAY.default),
            verboseLogcat = get(FeatureFlag.VERBOSE_LOGCAT.key, FeatureFlag.VERBOSE_LOGCAT.default),
            gazeBridge = get(FeatureFlag.GAZE_BRIDGE.key, FeatureFlag.GAZE_BRIDGE.default),
            voiceAssist = get(FeatureFlag.VOICE_ASSIST.key, FeatureFlag.VOICE_ASSIST.default),
        )

    fun toMap(flags: FeatureFlags): Map<String, Boolean> =
        FeatureFlag.entries.associate { it.key to flags.enabled(it) }
}
