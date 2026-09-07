package com.fedesack.raybanmetadat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fedesack.raybanmetadat.AppState
import com.fedesack.raybanmetadat.DeviceSource

@Composable
fun ConnectScreen(
    state: AppState,
    onRegister: () -> Unit,
    onMock: () -> Unit,
    onOpenLive: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ray-Ban Meta DAT", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Registro oficial via Meta AI o Mock Device Kit. Sin SDK de cámara web.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("Bluetooth: ${if (state.androidReady) "ok" else "pendiente"}")
        Text("Registro: ${state.registrationLabel}")
        Text(
            "Fuente: ${if (state.source == DeviceSource.MOCK) "Mock Device" else "Meta AI"}",
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRegister,
            enabled = state.androidReady,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text("Registrar con Meta AI")
        }
        Button(
            onClick = onMock,
            enabled = state.androidReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Usar Mock Device")
        }
        TextButton(
            onClick = onOpenLive,
            enabled = state.canOpenLive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Abrir preview")
        }
    }
}
