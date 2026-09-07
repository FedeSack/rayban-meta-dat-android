package com.fedesack.raybanmetadat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fedesack.raybanmetadat.FeatureFlag
import com.fedesack.raybanmetadat.FeatureFlags
import com.fedesack.raybanmetadat.FrameRateFlag
import com.fedesack.raybanmetadat.GazeWs
import com.fedesack.raybanmetadat.VideoQualityFlag

@Composable
fun FeaturesToggle(
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (open) "Close" else "Features",
        style = MaterialTheme.typography.labelMedium,
        modifier =
            modifier
                .background(DatTokens.latencyFill, RoundedCornerShape(DatTokens.latencyRadius))
                .clickable(onClick = onClick)
                .padding(horizontal = DatTokens.latencyPadH, vertical = DatTokens.latencyPadV),
    )
}

@Composable
fun FeatureFlagsPanel(
    flags: FeatureFlags,
    onFlagChange: (FeatureFlag, Boolean) -> Unit,
    onVideoQualityChange: (VideoQualityFlag) -> Unit,
    onFrameRateChange: (FrameRateFlag) -> Unit,
    live: Boolean = false,
    intentWebhookUrl: String = "",
    lastIntentStatus: String? = null,
    queuedIntentCount: Int = 0,
    onIntentWebhookUrlChange: (String) -> Unit = {},
    onEnqueueChat: (String) -> Unit = {},
    gazeEndpoint: String? = null,
    gazeListening: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = 300.dp)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .background(DatTokens.panel, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Features",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Toggles persist on this device. Stubs do nothing until wired.",
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = DatTokens.muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
        )
        ChoiceRow(
            title = "Video quality",
            subtitle = "HIGH 720×1280 · MED 504×896 · LOW 360×640",
            options = VideoQualityFlag.entries,
            selected = flags.videoQuality,
            label = { it.name },
            onSelect = onVideoQualityChange,
        )
        ChoiceRow(
            title = "Frame rate",
            subtitle = "DAT accepts 15 / 24 / 30. Default 24.",
            options = FrameRateFlag.entries,
            selected = flags.frameRate,
            label = { it.fps.toString() },
            onSelect = onFrameRateChange,
        )
        Text(
            text =
                if (live) {
                    "Live changes stop and restart the stream."
                } else {
                    "Applied on Start."
                },
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = DatTokens.muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
        )
        FlagRow(
            title = "Murdoku HQ capture",
            subtitle =
                if (flags.murdokuHqCapture) {
                    "Forces HIGH / 15 fps. Wizard in-app: instrucciones → puzzle → próxima jugada."
                } else {
                    "HQ stills + wizard Murdoku. Off keeps the live preview path."
                },
            checked = flags.murdokuHqCapture,
            onCheckedChange = { onFlagChange(FeatureFlag.MURDOKU_HQ_CAPTURE, it) },
        )
        FlagRow(
            title = "Prefer sharpness",
            subtitle = "Meta: lower res/fps can look sharper under BT pressure",
            checked = flags.preferSharpness,
            onCheckedChange = { onFlagChange(FeatureFlag.PREFER_SHARPNESS, it) },
        )
        FlagRow(
            title = "Analytics overlay",
            subtitle = "Live session stats on the HUD",
            checked = flags.analyticsOverlay,
            onCheckedChange = { onFlagChange(FeatureFlag.ANALYTICS_OVERLAY, it) },
        )
        FlagRow(
            title = "Verbose Logcat",
            subtitle = "RaybanDat/Analytics extra lines",
            checked = flags.verboseLogcat,
            onCheckedChange = { onFlagChange(FeatureFlag.VERBOSE_LOGCAT, it) },
        )
        FlagRow(
            title = "Gaze bridge",
            subtitle =
                if (flags.gazeBridge) {
                    if (gazeListening) {
                        "LAN JPEG relay live · ${gazeEndpoint ?: GazeWs.endpoint(null)}"
                    } else {
                        "LAN JPEG relay. Start the DAT stream to bind ${gazeEndpoint ?: GazeWs.endpoint(null)}"
                    }
                } else {
                    "LAN JPEG relay to the Windows sidecar. Off until you need cursor."
                },
            checked = flags.gazeBridge,
            onCheckedChange = { onFlagChange(FeatureFlag.GAZE_BRIDGE, it) },
        )
        if (flags.gazeBridge) {
            Text(
                text = gazeEndpoint ?: GazeWs.endpoint(null),
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = DatTokens.white,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
            )
        }
        FlagRow(
            title = "Voice assist",
            subtitle = "Stub — not wired",
            checked = flags.voiceAssist,
            onCheckedChange = { onFlagChange(FeatureFlag.VOICE_ASSIST, it) },
        )
        FlagRow(
            title = "Dev mode (voz→fixes)",
            subtitle = "Queue + webhook to our coding agent. Not Meta Hey-wake. No STT.",
            checked = flags.voiceDevMode,
            onCheckedChange = { onFlagChange(FeatureFlag.VOICE_DEV_MODE, it) },
        )
        if (flags.voiceDevMode) {
            ChatStubSection(
                webhookUrl = intentWebhookUrl,
                lastStatus = lastIntentStatus,
                queuedCount = queuedIntentCount,
                onWebhookUrlChange = onIntentWebhookUrlChange,
                onEnqueueChat = onEnqueueChat,
            )
        }
    }
}

@Composable
fun GazeLanChip(
    listening: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (listening) "Gaze LAN" else "Gaze",
        style =
            MaterialTheme.typography.labelMedium.copy(
                color = DatTokens.white,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
        modifier =
            modifier
                .background(DatTokens.surface, RoundedCornerShape(DatTokens.chipRadius))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun DevModeChip(modifier: Modifier = Modifier) {
    Text(
        text = "Dev mode",
        style =
            MaterialTheme.typography.labelMedium.copy(
                color = DatTokens.white,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
        modifier =
            modifier
                .background(DatTokens.surface, RoundedCornerShape(DatTokens.chipRadius))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ChatStubSection(
    webhookUrl: String,
    lastStatus: String?,
    queuedCount: Int,
    onWebhookUrlChange: (String) -> Unit,
    onEnqueueChat: (String) -> Unit,
) {
    var utterance by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Chat stub", style = MaterialTheme.typography.labelMedium)
        Text(
            text = "source=chat. Smoke the Dev pipeline without STT or a Meta wake word.",
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = DatTokens.muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
        )
        DevField(
            value = webhookUrl,
            onValueChange = onWebhookUrlChange,
            placeholder = "https://webhook…",
            keyboardType = KeyboardType.Uri,
        )
        DevField(
            value = utterance,
            onValueChange = { utterance = it },
            placeholder = "utterance",
        )
        Text(
            text = "Enqueue chat",
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier
                    .background(DatTokens.accent, RoundedCornerShape(8.dp))
                    .clickable { onEnqueueChat(utterance) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        lastStatus?.let { status ->
            Text(
                text = status,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = DatTokens.muted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
            )
        }
        if (queuedCount > 0) {
            Text(
                text = "Queue $queuedCount",
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = DatTokens.muted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
            )
        }
    }
}

@Composable
private fun DevField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(DatTokens.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = DatTokens.muted,
                        fontSize = 12.sp,
                    ),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.labelMedium.copy(
                    color = DatTokens.white,
                    fontSize = 12.sp,
                ),
            cursorBrush = SolidColor(DatTokens.accent),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium)
        Text(
            text = subtitle,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = DatTokens.muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val on = option == selected
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    modifier =
                        Modifier
                            .background(
                                if (on) DatTokens.accent else DatTokens.surface,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onSelect(option) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FlagRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = subtitle,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = DatTokens.muted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = DatTokens.white,
                    checkedTrackColor = DatTokens.accent,
                    uncheckedThumbColor = DatTokens.white,
                    uncheckedTrackColor = DatTokens.surface,
                ),
        )
    }
}
