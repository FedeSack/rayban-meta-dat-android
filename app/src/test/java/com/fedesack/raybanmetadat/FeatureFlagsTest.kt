package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {
    @Test
    fun defaultsKeepTheLiveSurfaceClean() {
        val flags = FeatureFlags()
        assertFalse(flags.analyticsOverlay)
        assertFalse(flags.verboseLogcat)
        assertFalse(flags.gazeBridge)
        assertFalse(flags.voiceAssist)
        FeatureFlag.entries.forEach { flag ->
            assertFalse(flag.default)
            assertEquals(flag.default, flags.enabled(flag))
        }
        assertTrue(FeatureFlag.GAZE_BRIDGE.stub)
        assertTrue(FeatureFlag.VOICE_ASSIST.stub)
        assertFalse(FeatureFlag.ANALYTICS_OVERLAY.stub)
    }

    @Test
    fun applyTogglesOneFlagWithoutTouchingTheOthers() {
        val next =
            FeatureFlagsCatalog.apply(
                FeatureFlags(),
                FeatureFlag.ANALYTICS_OVERLAY,
                enabled = true,
            )
        assertTrue(next.analyticsOverlay)
        assertFalse(next.verboseLogcat)
        assertFalse(next.gazeBridge)
        assertFalse(next.voiceAssist)
    }

    @Test
    fun fromStoredUsesPersistedValuesAndFallsBackToDefaults() {
        val stored = mapOf("analyticsOverlay" to true, "gazeBridge" to true)
        val flags =
            FeatureFlagsCatalog.fromStored { key, default ->
                stored[key] ?: default
            }
        assertTrue(flags.analyticsOverlay)
        assertFalse(flags.verboseLogcat)
        assertTrue(flags.gazeBridge)
        assertFalse(flags.voiceAssist)
        assertEquals(
            mapOf(
                "analyticsOverlay" to true,
                "verboseLogcat" to false,
                "gazeBridge" to true,
                "voiceAssist" to false,
            ),
            FeatureFlagsCatalog.toMap(flags),
        )
    }
}
