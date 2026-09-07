package com.fedesack.raybanmetadat.ui

import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        PreviewSurface(
            modifier = Modifier.fillMaxSize(),
            onSurface = onSurface,
            onSurfaceGone = onSurfaceGone,
        )
        Text(
            text = state.latencyMs?.let { "$it ms" } ?: "— ms",
            modifier =
                Modifier
                    .systemBarsPadding()
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 28.sp,
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .systemBarsPadding()
                    .padding(16.dp),
        ) {
            Text(
                "sesión ${state.session.name.lowercase()} · stream ${state.stream.name.lowercase()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("Atrás") }
                if (state.stream == StreamState.STREAMING || state.stream == StreamState.STARTING) {
                    Button(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Detener") }
                } else {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Iniciar") }
                }
            }
        }
    }
}
