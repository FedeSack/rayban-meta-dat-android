package com.fedesack.raybanmetadat.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fedesack.raybanmetadat.BoardCapture
import com.fedesack.raybanmetadat.MurdokuAssetKind

@Composable
fun CaptureGallery(
    captures: List<BoardCapture>,
    onShare: (BoardCapture) -> Unit,
    emptyLabel: String = "Sin capturas · Exportar abre share",
) {
    if (captures.isEmpty()) {
        Text(
            text = emptyLabel,
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
fun CaptureThumb(
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
    val caption =
        when (capture.kind) {
            MurdokuAssetKind.INSTRUCTIONS -> "Instrucciones"
            MurdokuAssetKind.PUZZLE -> "Puzzle"
            null -> "Exportar"
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
            text = caption,
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
