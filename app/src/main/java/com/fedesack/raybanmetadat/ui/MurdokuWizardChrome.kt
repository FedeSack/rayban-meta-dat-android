package com.fedesack.raybanmetadat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fedesack.raybanmetadat.BoardCapture
import com.fedesack.raybanmetadat.MurdokuWizardCopy
import com.fedesack.raybanmetadat.MurdokuWizardState
import com.fedesack.raybanmetadat.MurdokuWizardStep

@Composable
fun MurdokuWizardCard(
    wizard: MurdokuWizardState,
    onShareCapture: (BoardCapture) -> Unit,
    onEnqueueAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(DatTokens.panel, RoundedCornerShape(DatTokens.buttonRadius))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = wizard.stepTitle,
                style = MaterialTheme.typography.labelLarge,
            )
            VoiceStubChip()
        }
        if (wizard.step != MurdokuWizardStep.GUIDE) {
            Text(
                text = wizard.prompt,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = MurdokuWizardCopy.STEP3_HEADING,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            GuideMoves(wizard)
        }
        wizard.status?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium.copy(color = DatTokens.success),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (wizard.sessionCaptures.isNotEmpty()) {
            CaptureGallery(
                captures = wizard.sessionCaptures,
                onShare = onShareCapture,
                emptyLabel = "",
            )
        }
        if (wizard.step == MurdokuWizardStep.GUIDE) {
            Text(
                text = "Galería · ${MurdokuWizardCopy.GALLERY_PATH}",
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = DatTokens.muted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
            )
            Text(
                text = MurdokuWizardCopy.ENQUEUE_CTA,
                style = MaterialTheme.typography.labelMedium,
                modifier =
                    Modifier
                        .background(DatTokens.accent, RoundedCornerShape(8.dp))
                        .clickable(onClick = onEnqueueAnalysis)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            wizard.enqueueStatus?.let { status ->
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
        }
    }
}

@Composable
fun VoiceStubChip(modifier: Modifier = Modifier) {
    Text(
        text = MurdokuWizardCopy.VOICE_STUB,
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
fun MurdokuModeChip(modifier: Modifier = Modifier) {
    Text(
        text = "Modo ${MurdokuWizardCopy.MODE}",
        style = MaterialTheme.typography.labelMedium,
        modifier =
            modifier
                .background(DatTokens.latencyFill, RoundedCornerShape(DatTokens.latencyRadius))
                .padding(horizontal = DatTokens.latencyPadH, vertical = DatTokens.latencyPadV),
    )
}

@Composable
fun MurdokuEntryCard(
    enabled: Boolean,
    flagOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(DatTokens.surface, RoundedCornerShape(DatTokens.buttonRadius))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Modo Murdoku",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Wizard in-app · instrucciones, tablero y próxima jugada",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        DatButton(
            label = "Modo Murdoku",
            primary = true,
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text =
                if (flagOn) {
                    "HQ capture · HIGH 15 fps · wizard listo"
                } else {
                    "Enciende murdokuHqCapture y entra al Live"
                },
            style = MaterialTheme.typography.labelMedium.copy(color = DatTokens.muted),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GuideMoves(wizard: MurdokuWizardState) {
    val moves = wizard.analysis?.moves.orEmpty()
    if (moves.isEmpty()) {
        Text(
            text = "Todavía no hay jugadas. El análisis vuelve {sessionId, moves:[{row,col,value,reason}]} (row/col 0-index).",
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = DatTokens.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        moves.forEach { move ->
            Text(
                text = "fila ${move.row} · col ${move.col} = ${move.value} — ${move.reason}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
