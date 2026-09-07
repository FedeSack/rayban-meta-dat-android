package com.fedesack.raybanmetadat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fedesack.raybanmetadat.FeatureFlag
import com.fedesack.raybanmetadat.FeatureFlags

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = 280.dp)
                .background(DatTokens.panel, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
            modifier = Modifier.padding(bottom = 4.dp),
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
            subtitle = "Stub — not wired",
            checked = flags.gazeBridge,
            onCheckedChange = { onFlagChange(FeatureFlag.GAZE_BRIDGE, it) },
        )
        FlagRow(
            title = "Voice assist",
            subtitle = "Stub — not wired",
            checked = flags.voiceAssist,
            onCheckedChange = { onFlagChange(FeatureFlag.VOICE_ASSIST, it) },
        )
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
