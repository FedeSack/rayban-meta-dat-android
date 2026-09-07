package com.fedesack.raybanmetadat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.fedesack.raybanmetadat.AppState
import com.fedesack.raybanmetadat.FeatureFlag
import com.fedesack.raybanmetadat.FrameRateFlag
import com.fedesack.raybanmetadat.R
import com.fedesack.raybanmetadat.VideoQualityFlag

@Composable
fun ConnectScreen(
    state: AppState,
    onRegister: () -> Unit,
    onMock: () -> Unit,
    onMurdoku: () -> Unit,
    onFlagChange: (FeatureFlag, Boolean) -> Unit,
    onVideoQualityChange: (VideoQualityFlag) -> Unit,
    onFrameRateChange: (FrameRateFlag) -> Unit,
    onIntentWebhookUrlChange: (String) -> Unit,
    onEnqueueChat: (String) -> Unit,
) {
    var showFlags by remember { mutableStateOf(false) }
    val connected = state.registered
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DatTokens.bg),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = DatTokens.pagePad),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = DatTokens.headerTop),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DatTokens.headerGap),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(connected = connected)
                    if (state.flags.voiceDevMode) {
                        DevModeChip()
                    }
                    if (state.flags.gazeBridge) {
                        GazeLanChip(listening = state.gazeListening)
                    }
                }
                Text(
                    text = "Ray-Ban Meta",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Stream your Ray-Ban Meta camera via Meta Wearables Device Access Toolkit.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let {
                    Text(
                        text = it,
                        color = DatTokens.danger,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = DatTokens.ctaBottom),
                verticalArrangement = Arrangement.spacedBy(DatTokens.buttonGap),
            ) {
                DatButton(
                    label = "Connect with Meta AI",
                    primary = true,
                    enabled = state.androidReady,
                    onClick = onRegister,
                    modifier = Modifier.fillMaxWidth(),
                )
                MurdokuEntryCard(
                    enabled = state.androidReady,
                    flagOn = state.flags.murdokuHqCapture,
                    onClick = onMurdoku,
                )
                DatButton(
                    label = "Use Mock Device",
                    primary = false,
                    enabled = state.androidReady,
                    onClick = onMock,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        FeaturesToggle(
            open = showFlags,
            onClick = { showFlags = !showFlags },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = DatTokens.pagePad, top = DatTokens.hudGap),
        )
        if (showFlags) {
            FeatureFlagsPanel(
                flags = state.flags,
                onFlagChange = onFlagChange,
                onVideoQualityChange = onVideoQualityChange,
                onFrameRateChange = onFrameRateChange,
                intentWebhookUrl = state.intentWebhookUrl,
                lastIntentStatus = state.lastIntentStatus,
                queuedIntentCount = state.queuedIntentCount,
                onIntentWebhookUrlChange = onIntentWebhookUrlChange,
                onEnqueueChat = onEnqueueChat,
                gazeEndpoint = state.gazeEndpoint,
                gazeListening = state.gazeListening,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = DatTokens.pagePad, top = 48.dp),
            )
        }
    }
}

@Composable
private fun StatusChip(connected: Boolean) {
    Row(
        modifier =
            Modifier
                .background(DatTokens.surface, RoundedCornerShape(DatTokens.chipRadius))
                .padding(horizontal = DatTokens.chipPadH, vertical = DatTokens.chipPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter =
                painterResource(
                    if (connected) R.drawable.status_dot_connected
                    else R.drawable.status_dot_disconnected,
                ),
            contentDescription = null,
            modifier = Modifier.size(DatTokens.dot),
        )
        Spacer(Modifier.width(DatTokens.chipGap))
        Text(
            text = if (connected) "Connected" else "Disconnected",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun DatButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(DatTokens.buttonH).then(modifier),
        shape = RoundedCornerShape(DatTokens.buttonRadius),
        contentPadding = PaddingValues(horizontal = 20.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) DatTokens.accent else DatTokens.surface,
                contentColor = DatTokens.white,
                disabledContainerColor = (if (primary) DatTokens.accent else DatTokens.surface).copy(alpha = 0.4f),
                disabledContentColor = DatTokens.white.copy(alpha = 0.5f),
            ),
    ) {
        Text(
            text = label,
            style = if (primary) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
        )
    }
}
