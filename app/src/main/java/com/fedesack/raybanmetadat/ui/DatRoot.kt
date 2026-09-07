package com.fedesack.raybanmetadat.ui

import android.view.Surface
import androidx.compose.runtime.Composable
import com.fedesack.raybanmetadat.AppState
import com.fedesack.raybanmetadat.FeatureFlag
import com.fedesack.raybanmetadat.FrameRateFlag
import com.fedesack.raybanmetadat.Phase
import com.fedesack.raybanmetadat.VideoQualityFlag

@Composable
fun DatRoot(
    state: AppState,
    onRegister: () -> Unit,
    onMock: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSurface: (Surface) -> Unit,
    onSurfaceGone: () -> Unit,
    onFlagChange: (FeatureFlag, Boolean) -> Unit,
    onVideoQualityChange: (VideoQualityFlag) -> Unit,
    onFrameRateChange: (FrameRateFlag) -> Unit,
) {
    when (state.phase) {
        Phase.CONNECT ->
            ConnectScreen(
                state = state,
                onRegister = onRegister,
                onMock = onMock,
                onFlagChange = onFlagChange,
                onVideoQualityChange = onVideoQualityChange,
                onFrameRateChange = onFrameRateChange,
            )
        Phase.LIVE ->
            LiveScreen(
                state = state,
                onBack = onBack,
                onStart = onStart,
                onStop = onStop,
                onSurface = onSurface,
                onSurfaceGone = onSurfaceGone,
                onFlagChange = onFlagChange,
                onVideoQualityChange = onVideoQualityChange,
                onFrameRateChange = onFrameRateChange,
            )
    }
}
