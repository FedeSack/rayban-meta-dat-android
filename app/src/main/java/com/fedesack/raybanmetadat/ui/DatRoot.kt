package com.fedesack.raybanmetadat.ui

import android.view.Surface
import androidx.compose.runtime.Composable
import com.fedesack.raybanmetadat.AppState
import com.fedesack.raybanmetadat.Phase

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
) {
    when (state.phase) {
        Phase.CONNECT ->
            ConnectScreen(
                state = state,
                onRegister = onRegister,
                onMock = onMock,
            )
        Phase.LIVE ->
            LiveScreen(
                state = state,
                onBack = onBack,
                onStart = onStart,
                onStop = onStop,
                onSurface = onSurface,
                onSurfaceGone = onSurfaceGone,
            )
    }
}
