package com.fedesack.raybanmetadat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fedesack.raybanmetadat.R

val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

object DatTokens {
    val bg = Color(0xFF0A0A0B)
    val surface = Color(0xFF1C1C1E)
    val accent = Color(0xFF006CEB)
    val muted = Color(0xFF8E8E93)
    val success = Color(0xFF30D158)
    val danger = Color(0xFFFF453A)
    val white = Color(0xFFFFFFFF)
    val latencyFill = Color(0xB8000000)
    val pov = Color(0x59FFFFFF)
    val stageTop = Color(0xFF0F141F)
    val stageMid = Color(0xFF1F2938)
    val stageBottom = Color(0xFF080A0D)
    val panel = Color(0xF01C1C1E)
    val scrim = Color(0x8C000000)

    val pagePad = 24.dp
    val headerGap = 16.dp
    val headerTop = 24.dp
    val chipPadH = 14.dp
    val chipPadV = 8.dp
    val chipRadius = 20.dp
    val chipGap = 8.dp
    val dot = 8.dp
    val buttonH = 52.dp
    val buttonRadius = 14.dp
    val buttonGap = 12.dp
    val ctaBottom = 16.dp
    val hudGap = 8.dp
    val latencyPadH = 12.dp
    val latencyPadV = 8.dp
    val latencyRadius = 10.dp
    val startW = 345.dp
    val murdokuCtaH = 60.dp
    val galleryH = 72.dp
    val thumb = 64.dp
}

private val Palette =
    darkColorScheme(
        primary = DatTokens.accent,
        onPrimary = DatTokens.white,
        background = DatTokens.bg,
        onBackground = DatTokens.white,
        surface = DatTokens.surface,
        onSurface = DatTokens.white,
        surfaceVariant = DatTokens.surface,
        onSurfaceVariant = DatTokens.muted,
        error = DatTokens.danger,
    )

private val Type =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.14).sp,
                color = DatTokens.white,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = DatTokens.muted,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = DatTokens.white,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = DatTokens.white,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = DatTokens.white,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = DatTokens.white,
            ),
    )

@Composable
fun DatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, typography = Type, content = content)
}
