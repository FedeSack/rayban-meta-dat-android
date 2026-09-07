package com.fedesack.raybanmetadat

import android.content.Context

class FeatureFlagsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): FeatureFlags =
        FeatureFlagsCatalog.fromStored { key, default -> prefs.getBoolean(key, default) }

    fun set(
        flag: FeatureFlag,
        enabled: Boolean,
    ): FeatureFlags {
        prefs.edit().putBoolean(flag.key, enabled).apply()
        return load()
    }

    companion object {
        const val PREFS_NAME = "rayban_dat_flags"
    }
}
