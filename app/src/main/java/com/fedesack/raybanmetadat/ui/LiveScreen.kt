package com.fedesack.raybanmetadat.ui

import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import com.fedesack.raybanmetadat.AppState
import com.meta.wearable.dat.camera.types.StreamState

@Composable
fun LiveScreen(
    state: AppState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSurface: (Surface) -> Unit,
    onSurfaceGone: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val streaming = state.stream == StreamState.STREAMING || state.stream == StreamState.STARTING
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DatTokens.bg),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to DatTokens.stageTop,
                            0.45f to DatTokens.stageMid,
                            1f to DatTokens.stageBottom,
                        ),
                    ),
        )
        PreviewSurface(
            modifier = Modifier.fillMaxSize(),
            onSurface = onSurface,
            onSurfaceGone = onSurfaceGone,
        )
        if (!state.hasFrame) {
            Text(
                text = "Glasses camera POV",
                style = MaterialTheme.typography.labelMedium.copy(color = DatTokens.pov),
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = state.latencyMs?.let { "$it ms" } ?: "— ms",
            style = MaterialTheme.typography.labelLarge,
            modifier =
                Modifier
                    .offset(x = DatTokens.latencyX, y = DatTokens.latencyY)
                    .background(DatTokens.latencyFill, RoundedCornerShape(DatTokens.latencyRadius))
                    .padding(horizontal = DatTokens.latencyPadH, vertical = DatTokens.latencyPadV),
        )
        state.message?.let {
            Text(
                text = it,
                color = DatTokens.danger,
                style = MaterialTheme.typography.labelMedium,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = DatTokens.pagePad, end = DatTokens.pagePad, bottom = DatTokens.buttonH + DatTokens.safeBottom + DatTokens.buttonGap),
                textAlign = TextAlign.Center,
            )
        }
        DatButton(
            label = if (streaming) "Stop" else "Start",
            primary = true,
            enabled = true,
            onClick = if (streaming) onStop else onStart,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = DatTokens.pagePad, end = DatTokens.pagePad, bottom = DatTokens.safeBottom)
                    .width(DatTokens.startW)
                    .height(DatTokens.buttonH),
        )
    }
}
