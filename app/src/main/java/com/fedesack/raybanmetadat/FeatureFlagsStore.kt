package com.fedesack.raybanmetadat

import android.content.Context

class FeatureFlagsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): FeatureFlags =
        FeatureFlagsCatalog.fromStored(
            getBoolean = { key, default -> prefs.getBoolean(key, default) },
            getString = { key, default -> prefs.getString(key, default) ?: default },
            getInt = { key, default -> prefs.getInt(key, default) },
        )

    fun set(
        flag: FeatureFlag,
        enabled: Boolean,
    ): FeatureFlags {
        prefs.edit().putBoolean(flag.key, enabled).apply()
        return load()
    }

    fun setVideoQuality(quality: VideoQualityFlag): FeatureFlags {
        prefs.edit().putString(FeatureFlagsCatalog.VIDEO_QUALITY_KEY, quality.key).apply()
        return load()
    }

    fun setFrameRate(frameRate: FrameRateFlag): FeatureFlags {
        prefs.edit().putInt(FeatureFlagsCatalog.FRAME_RATE_KEY, frameRate.fps).apply()
        return load()
    }

    companion object {
        const val PREFS_NAME = "rayban_dat_flags"
    }
}
