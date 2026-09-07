package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {
    @Test
    fun defaultsKeepTheLiveSurfaceCleanAndStreamOnHigh24() {
        val flags = FeatureFlags()
        assertFalse(flags.analyticsOverlay)
        assertFalse(flags.verboseLogcat)
        assertFalse(flags.preferSharpness)
        assertFalse(flags.murdokuHqCapture)
        assertFalse(flags.gazeBridge)
        assertFalse(flags.voiceAssist)
        assertFalse(flags.voiceDevMode)
        assertEquals(VideoQualityFlag.HIGH, flags.videoQuality)
        assertEquals(FrameRateFlag.FPS_24, flags.frameRate)
        FeatureFlag.entries.forEach { flag ->
            assertFalse(flag.default)
            assertEquals(flag.default, flags.enabled(flag))
        }
        assertFalse(FeatureFlag.GAZE_BRIDGE.stub)
        assertTrue(FeatureFlag.VOICE_ASSIST.stub)
        assertTrue(FeatureFlag.VOICE_DEV_MODE.stub)
        assertFalse(FeatureFlag.ANALYTICS_OVERLAY.stub)
        assertFalse(FeatureFlag.PREFER_SHARPNESS.stub)
        assertFalse(FeatureFlag.MURDOKU_HQ_CAPTURE.stub)
        val config = flags.streamConfig()
        assertEquals("HIGH", config.qualityName)
        assertEquals(24, config.fps)
        assertTrue(config.compressVideo)
        assertFalse(config.preferSharpness)
        assertFalse(config.murdokuHq)
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
        assertFalse(next.preferSharpness)
        assertFalse(next.murdokuHqCapture)
        assertFalse(next.gazeBridge)
        assertFalse(next.voiceAssist)
        assertFalse(next.voiceDevMode)
        assertEquals(VideoQualityFlag.HIGH, next.videoQuality)
        assertEquals(FrameRateFlag.FPS_24, next.frameRate)
    }

    @Test
    fun applyQualityAndFrameRateLeaveBooleanFlagsAlone() {
        val current =
            FeatureFlagsCatalog.apply(
                FeatureFlags(),
                FeatureFlag.VERBOSE_LOGCAT,
                enabled = true,
            )
        val quality = FeatureFlagsCatalog.applyQuality(current, VideoQualityFlag.LOW)
        val fps = FeatureFlagsCatalog.applyFrameRate(quality, FrameRateFlag.FPS_15)
        assertTrue(fps.verboseLogcat)
        assertEquals(VideoQualityFlag.LOW, fps.videoQuality)
        assertEquals(FrameRateFlag.FPS_15, fps.frameRate)
        assertEquals("LOW", fps.streamConfig().qualityName)
        assertEquals(15, fps.streamConfig().fps)
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
        assertFalse(flags.preferSharpness)
        assertFalse(flags.murdokuHqCapture)
        assertTrue(flags.gazeBridge)
        assertFalse(flags.voiceAssist)
        assertFalse(flags.voiceDevMode)
        assertEquals(VideoQualityFlag.HIGH, flags.videoQuality)
        assertEquals(FrameRateFlag.FPS_24, flags.frameRate)
        assertEquals(
            mapOf(
                "analyticsOverlay" to true,
                "verboseLogcat" to false,
                "preferSharpness" to false,
                "murdokuHqCapture" to false,
                "gazeBridge" to true,
                "voiceAssist" to false,
                "voiceDevMode" to false,
            ),
            FeatureFlagsCatalog.toMap(flags),
        )
        assertEquals(
            mapOf(
                "videoQuality" to "HIGH",
                "frameRate" to 24,
                "preferSharpness" to false,
                "murdokuHqCapture" to false,
            ),
            FeatureFlagsCatalog.streamSettings(flags),
        )
    }

    @Test
    fun fromStoredReadsQualityFpsAndSharpnessHint() {
        val flags =
            FeatureFlagsCatalog.fromStoredValues(
                mapOf(
                    "videoQuality" to "medium",
                    "frameRate" to 30,
                    "preferSharpness" to true,
                    "verboseLogcat" to true,
                ),
            )
        assertEquals(VideoQualityFlag.MEDIUM, flags.videoQuality)
        assertEquals(FrameRateFlag.FPS_30, flags.frameRate)
        assertTrue(flags.preferSharpness)
        assertTrue(flags.verboseLogcat)
        assertEquals("MEDIUM", flags.streamConfig().qualityName)
        assertEquals(30, flags.streamConfig().fps)
        assertTrue(flags.streamConfig().preferSharpness)
    }

    @Test
    fun videoQualityParsesKnownValuesAndFallsBackToHigh() {
        assertEquals(VideoQualityFlag.HIGH, VideoQualityFlag.parse(null))
        assertEquals(VideoQualityFlag.HIGH, VideoQualityFlag.parse(""))
        assertEquals(VideoQualityFlag.HIGH, VideoQualityFlag.parse("  "))
        assertEquals(VideoQualityFlag.HIGH, VideoQualityFlag.parse("high"))
        assertEquals(VideoQualityFlag.HIGH, VideoQualityFlag.parse("HIGH"))
        assertEquals(VideoQualityFlag.MEDIUM, VideoQualityFlag.parse("Medium"))
        assertEquals(VideoQualityFlag.LOW, VideoQualityFlag.parse("low"))
        assertEquals(VideoQualityFlag.HIGH, VideoQualityFlag.parse("ultra"))
        assertEquals(VideoQualityFlag.HIGH, VideoQualityFlag.parse("720p"))
    }

    @Test
    fun frameRateParsesKnownValuesAndFallsBackTo24() {
        assertEquals(FrameRateFlag.FPS_24, FrameRateFlag.parse(null as Int?))
        assertEquals(FrameRateFlag.FPS_24, FrameRateFlag.parse(null as String?))
        assertEquals(FrameRateFlag.FPS_15, FrameRateFlag.parse(15))
        assertEquals(FrameRateFlag.FPS_24, FrameRateFlag.parse(24))
        assertEquals(FrameRateFlag.FPS_30, FrameRateFlag.parse(30))
        assertEquals(FrameRateFlag.FPS_30, FrameRateFlag.parse(" 30 "))
        assertEquals(FrameRateFlag.FPS_24, FrameRateFlag.parse(2))
        assertEquals(FrameRateFlag.FPS_24, FrameRateFlag.parse(7))
        assertEquals(FrameRateFlag.FPS_24, FrameRateFlag.parse(60))
        assertEquals(FrameRateFlag.FPS_24, FrameRateFlag.parse("abc"))
        assertEquals(FrameRateFlag.FPS_15, FrameRateFlag.parse("15"))
    }

    @Test
    fun voiceDevModePersistsOffByDefaultAndCanBeEnabled() {
        val enabled =
            FeatureFlagsCatalog.apply(
                FeatureFlags(),
                FeatureFlag.VOICE_DEV_MODE,
                enabled = true,
            )
        assertTrue(enabled.voiceDevMode)
        assertTrue(enabled.enabled(FeatureFlag.VOICE_DEV_MODE))
        assertFalse(enabled.voiceAssist)
        val restored =
            FeatureFlagsCatalog.fromStoredValues(
                mapOf("voiceDevMode" to true, "voiceAssist" to false),
            )
        assertTrue(restored.voiceDevMode)
        assertFalse(restored.voiceAssist)
        assertEquals(true, FeatureFlagsCatalog.toMap(restored)["voiceDevMode"])
    }

    @Test
    fun fromStoredIgnoresUnknownQualityAndFps() {
        val flags =
            FeatureFlagsCatalog.fromStoredValues(
                mapOf(
                    "videoQuality" to "ULTRA",
                    "frameRate" to "60",
                ),
            )
        assertEquals(VideoQualityFlag.HIGH, flags.videoQuality)
        assertEquals(FrameRateFlag.FPS_24, flags.frameRate)
    }

    @Test
    fun murdokuHqCaptureOverridesStreamToHigh15WithoutMutatingSavedQuality() {
        val stored =
            FeatureFlags(
                murdokuHqCapture = true,
                videoQuality = VideoQualityFlag.LOW,
                frameRate = FrameRateFlag.FPS_30,
                preferSharpness = false,
            )
        val config = stored.streamConfig()
        assertTrue(stored.murdokuHqCapture)
        assertEquals(VideoQualityFlag.LOW, stored.videoQuality)
        assertEquals(FrameRateFlag.FPS_30, stored.frameRate)
        assertEquals(VideoQualityFlag.HIGH, config.quality)
        assertEquals(FrameRateFlag.FPS_15, config.frameRate)
        assertEquals("HIGH", config.qualityName)
        assertEquals(15, config.fps)
        assertTrue(config.compressVideo)
        assertTrue(config.preferSharpness)
        assertTrue(config.murdokuHq)
        assertEquals(
            mapOf(
                "videoQuality" to "HIGH",
                "frameRate" to 15,
                "preferSharpness" to true,
                "murdokuHqCapture" to true,
            ),
            FeatureFlagsCatalog.streamSettings(stored),
        )
        val off = FeatureFlagsCatalog.apply(stored, FeatureFlag.MURDOKU_HQ_CAPTURE, enabled = false)
        assertFalse(off.murdokuHqCapture)
        assertEquals(VideoQualityFlag.LOW, off.videoQuality)
        assertEquals(FrameRateFlag.FPS_30, off.frameRate)
        assertEquals(30, off.streamConfig().fps)
        assertEquals("LOW", off.streamConfig().qualityName)
        assertFalse(off.streamConfig().murdokuHq)
    }

    @Test
    fun fromStoredReadsMurdokuHqCapture() {
        val flags =
            FeatureFlagsCatalog.fromStoredValues(
                mapOf(
                    "murdokuHqCapture" to true,
                    "videoQuality" to "LOW",
                    "frameRate" to 30,
                ),
            )
        assertTrue(flags.murdokuHqCapture)
        assertEquals(VideoQualityFlag.LOW, flags.videoQuality)
        assertEquals(15, flags.streamConfig().fps)
        assertEquals("HIGH", flags.streamConfig().qualityName)
    }
}
