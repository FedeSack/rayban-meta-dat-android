package com.fedesack.raybanmetadat.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fedesack.raybanmetadat.AnalyticsSnapshot
import com.fedesack.raybanmetadat.AppState
import com.fedesack.raybanmetadat.BoardCapture
import com.fedesack.raybanmetadat.FeatureFlag
import com.fedesack.raybanmetadat.FrameRateFlag
import com.fedesack.raybanmetadat.LatencyMode
import com.fedesack.raybanmetadat.StreamAnalyticsMath
import com.fedesack.raybanmetadat.VideoQualityFlag
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.core.session.DeviceSessionState

@Composable
fun LiveScreen(
    state: AppState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCaptureBoard: () -> Unit,
    onShareCapture: (BoardCapture) -> Unit,
    onSurface: (Surface) -> Unit,
    onSurfaceGone: () -> Unit,
    onFlagChange: (FeatureFlag, Boolean) -> Unit,
    onVideoQualityChange: (VideoQualityFlag) -> Unit,
    onFrameRateChange: (FrameRateFlag) -> Unit,
    onIntentWebhookUrlChange: (String) -> Unit,
    onEnqueueChat: (String) -> Unit,
) {
    var showFlags by remember { mutableStateOf(false) }
    var analyticsExpanded by remember { mutableStateOf(false) }
    BackHandler {
        if (showFlags) {
            showFlags = false
        } else {
            onBack()
        }
    }
    val streaming = state.stream == StreamState.STREAMING || state.stream == StreamState.STARTING
    val waking = state.awaitingFirstFrame && !state.hasFrame && state.message == null
    val murdoku = state.flags.murdokuHqCapture
    val chromeBottom =
        if (murdoku) {
            DatTokens.murdokuCtaH + DatTokens.buttonH + DatTokens.buttonGap * 2 + DatTokens.ctaBottom +
                DatTokens.galleryH
        } else {
            DatTokens.buttonH + DatTokens.ctaBottom + DatTokens.buttonGap
        }
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
        if (waking) {
            WakingOverlay(state = state)
        } else if (!state.hasFrame) {
            Text(
                text = "Glasses camera POV",
                style = MaterialTheme.typography.labelMedium.copy(color = DatTokens.pov),
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = DatTokens.pagePad, top = DatTokens.hudGap),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LatencyHud(
                state = state,
                expanded = analyticsExpanded && state.flags.analyticsOverlay,
                onToggle = {
                    if (state.flags.analyticsOverlay) {
                        analyticsExpanded = !analyticsExpanded
                    }
                },
            )
            if (state.flags.voiceDevMode) {
                DevModeChip()
            }
            if (state.flags.gazeBridge) {
                GazeLanChip(listening = state.gazeListening)
            }
        }
        if (murdoku) {
            Text(
                text = "Murdoku HQ",
                style = MaterialTheme.typography.labelMedium,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = DatTokens.hudGap)
                        .background(DatTokens.latencyFill, RoundedCornerShape(DatTokens.latencyRadius))
                        .padding(horizontal = DatTokens.latencyPadH, vertical = DatTokens.latencyPadV),
            )
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
                live = streaming || waking,
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
        state.message?.let {
            Text(
                text = it,
                color = DatTokens.danger,
                style = MaterialTheme.typography.labelMedium,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = DatTokens.pagePad,
                            end = DatTokens.pagePad,
                            bottom = chromeBottom,
                        ),
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = DatTokens.pagePad,
                        end = DatTokens.pagePad,
                        bottom = DatTokens.ctaBottom,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DatTokens.buttonGap),
        ) {
            if (murdoku) {
                CaptureGallery(
                    captures = state.captures,
                    onShare = onShareCapture,
                )
                DatButton(
                    label = if (state.capturing) "Guardando…" else "Capturar tablero",
                    primary = true,
                    enabled = streaming && state.hasFrame && !state.capturing && !waking,
                    onClick = onCaptureBoard,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(DatTokens.murdokuCtaH),
                )
                DatButton(
                    label = if (streaming) "Stop" else "Start",
                    primary = false,
                    enabled = !waking || streaming,
                    onClick = if (streaming) onStop else onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                DatButton(
                    label = if (streaming) "Stop" else "Start",
                    primary = true,
                    enabled = !waking || streaming,
                    onClick = if (streaming) onStop else onStart,
                    modifier =
                        Modifier
                            .width(DatTokens.startW)
                            .height(DatTokens.buttonH),
                )
            }
        }
    }
}

@Composable
private fun WakingOverlay(state: AppState) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DatTokens.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = DatTokens.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Encendiendo cámara…",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = wakingHint(state),
                style = MaterialTheme.typography.labelMedium.copy(color = DatTokens.muted),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LatencyHud(
    state: AppState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlay = state.flags.analyticsOverlay
    Column(
        modifier =
            modifier
                .widthIn(max = 220.dp)
                .background(DatTokens.latencyFill, RoundedCornerShape(DatTokens.latencyRadius))
                .then(if (overlay) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(horizontal = DatTokens.latencyPadH, vertical = DatTokens.latencyPadV),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = state.latencyMs?.let { "$it ms" } ?: "— ms",
            style = MaterialTheme.typography.labelLarge,
        )
        if (state.latencyMs != null && state.latencyMode == LatencyMode.PIPELINE) {
            Text(
                text = "pipeline",
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = DatTokens.muted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
            )
        }
        if (overlay && !expanded) {
            Text(
                text = "stats",
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = DatTokens.muted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
            )
        }
        if (expanded) {
            AnalyticsLines(state.analytics)
        }
    }
}

@Composable
private fun AnalyticsLines(analytics: AnalyticsSnapshot) {
    val fps = analytics.estimatedFps?.let { StreamAnalyticsMath.formatFps(it) }
    val size =
        if (analytics.width != null && analytics.height != null) {
            "${analytics.width}×${analytics.height}"
        } else {
            null
        }
    val lines =
        listOfNotNull(
            fps?.let { line ->
                analytics.interArrivalMs?.let { gap -> "$line fps · Δ $gap ms" } ?: "$line fps"
            },
            analytics.timeToFirstFrameMs?.let { "TTFF $it ms" },
            listOfNotNull(size, analytics.decodePath?.name).takeIf { it.isNotEmpty() }?.joinToString(" "),
            "frames ${analytics.framesPresented}/${analytics.framesArrived}",
            if (analytics.queueDrops > 0L) "drops ${analytics.queueDrops}" else null,
            listOfNotNull(analytics.sessionState, analytics.streamState).joinToString(" / ").ifBlank { null },
            analytics.configuredQuality?.let { quality ->
                val fpsCfg = analytics.configuredFps?.let { " $it" } ?: ""
                "cfg $quality$fpsCfg"
            },
        )
    lines.forEach { line ->
        Text(
            text = line,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = DatTokens.muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
        )
    }
}

@Composable
private fun CaptureGallery(
    captures: List<BoardCapture>,
    onShare: (BoardCapture) -> Unit,
) {
    if (captures.isEmpty()) {
        Text(
            text = "Sin capturas · Exportar abre share",
            style = MaterialTheme.typography.labelMedium.copy(color = DatTokens.muted),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(captures, key = { it.id }) { capture ->
            CaptureThumb(capture = capture, onShare = { onShare(capture) })
        }
    }
}

@Composable
private fun CaptureThumb(
    capture: BoardCapture,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap =
        remember(capture.uri) {
            context.contentResolver.openInputStream(Uri.parse(capture.uri))?.use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = 8 },
                )
            }
        }
    Box(
        modifier =
            Modifier
                .size(DatTokens.thumb)
                .clip(RoundedCornerShape(8.dp))
                .background(DatTokens.surface)
                .clickable(onClick = onShare),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = capture.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "Exportar",
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = DatTokens.white,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(DatTokens.latencyFill)
                    .padding(vertical = 2.dp),
            textAlign = TextAlign.Center,
        )
    }
}

private fun wakingHint(state: AppState): String {
    val arrived = state.analytics.framesArrived
    return when {
        arrived > 0L -> "Decodificando primer frame…"
        state.stream == StreamState.STARTING -> "STARTING"
        state.stream == StreamState.STREAMING -> "Esperando primer frame"
        state.session != DeviceSessionState.STARTED -> state.session.name
        else -> "Preparando stream"
    }
}
