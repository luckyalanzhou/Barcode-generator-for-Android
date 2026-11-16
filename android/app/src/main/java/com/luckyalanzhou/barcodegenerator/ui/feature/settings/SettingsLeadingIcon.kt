package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.R

/** Decorative icons used only by rows on the top-level Settings page. */
internal enum class SettingsIconKind {
    Theme,
    Language,
    Correction,
    LanShare,
    Logs,
    About,
    BarcodeHeight,
    BarcodeWidth,
    BarcodeSpacing,
    TextSize,
    RestoreStyle,
}

private val SettingsIconBlue = Color(0xFF1688F8)

@Composable
internal fun SettingsLeadingIcon(kind: SettingsIconKind) {
    val background = when (kind) {
        SettingsIconKind.Theme -> Color(0xFFE91E63)
        SettingsIconKind.Language, SettingsIconKind.About -> Color(0xFF1688F8)
        SettingsIconKind.Correction -> Color(0xFF1A9B57)
        SettingsIconKind.LanShare -> Color(0xFFD87800)
        SettingsIconKind.Logs -> Color(0xFF9C35B8)
        SettingsIconKind.BarcodeHeight,
        SettingsIconKind.BarcodeWidth,
        SettingsIconKind.BarcodeSpacing,
        SettingsIconKind.TextSize,
        SettingsIconKind.RestoreStyle -> SettingsIconBlue
    }
    val glyphModifier = when (kind) {
        SettingsIconKind.BarcodeHeight -> Modifier.size(21.dp).absoluteOffset(x = (-0.35).dp, y = 0.dp)
        SettingsIconKind.BarcodeWidth -> Modifier.size(21.dp).absoluteOffset(y = (-0.6).dp)
        SettingsIconKind.BarcodeSpacing -> Modifier.size(21.dp).absoluteOffset(x = 0.35.dp)
        else -> Modifier.size(22.dp)
    }
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        SettingsGlyph(kind, Color.White, glyphModifier)
    }
}

@Composable
private fun SettingsGlyph(kind: SettingsIconKind, color: Color, modifier: Modifier) {
    val vector = when (kind) {
        SettingsIconKind.Theme -> R.drawable.settings_symbol_palette
        SettingsIconKind.Language -> R.drawable.settings_symbol_language
        SettingsIconKind.Correction -> R.drawable.settings_symbol_spellcheck
        SettingsIconKind.LanShare -> R.drawable.settings_symbol_wifi
        SettingsIconKind.Logs -> R.drawable.settings_symbol_bug_report
        SettingsIconKind.About -> R.drawable.settings_symbol_info
        SettingsIconKind.TextSize -> R.drawable.settings_symbol_format_size
        SettingsIconKind.RestoreStyle -> R.drawable.settings_symbol_tune
        SettingsIconKind.BarcodeHeight,
        SettingsIconKind.BarcodeWidth,
        SettingsIconKind.BarcodeSpacing -> null
    }
    if (vector != null) {
        Icon(
            painter = painterResource(vector),
            contentDescription = null,
            tint = color,
            modifier = modifier,
        )
    } else {
        Canvas(modifier) {
            val factor = size.width / 24f
            withTransform({ scale(factor, factor, Offset.Zero) }) {
                fun line(x1: Float, y1: Float, x2: Float, y2: Float, stroke: Float = 1.6f) {
                    drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke, StrokeCap.Round)
                }
                fun barcode(left: Float, top: Float, bottom: Float, horizontalScale: Float = 1f) {
                    val bars = listOf(0f to 1.1f, 2.2f to 1.2f, 4.7f to 1.8f, 8f to 1.1f, 10.3f to 1.3f, 13f to 1.9f, 16.3f to 1f)
                    bars.forEach { (offset, width) ->
                        drawLine(color, Offset(left + offset * horizontalScale, top), Offset(left + offset * horizontalScale, bottom), width)
                    }
                }
                when (kind) {
                    SettingsIconKind.BarcodeHeight -> {
                        barcode(1.5f, 5f, 19f, horizontalScale = 0.9f)
                        line(21.3f, 5f, 21.3f, 19f)
                        line(19.3f, 7f, 21.3f, 5f)
                        line(23.3f, 7f, 21.3f, 5f)
                        line(19.3f, 17f, 21.3f, 19f)
                        line(23.3f, 17f, 21.3f, 19f)
                    }
                    SettingsIconKind.BarcodeWidth -> {
                        barcode(3.8f, 3f, 16.8f)
                        line(3.8f, 20.9f, 20.1f, 20.9f)
                        line(5.8f, 19.1f, 3.8f, 20.9f)
                        line(5.8f, 22.7f, 3.8f, 20.9f)
                        line(18.1f, 19.1f, 20.1f, 20.9f)
                        line(18.1f, 22.7f, 20.1f, 20.9f)
                    }
                    SettingsIconKind.BarcodeSpacing -> {
                        barcode(2f, 2.5f, 7.5f, horizontalScale = 0.9f)
                        barcode(2f, 16.5f, 21.5f, horizontalScale = 0.9f)
                        // Keep the two outward arrows visibly separate so their heads do not
                        // merge into a diamond, and place them close to the barcode edge.
                        line(19.6f, 10.8f, 19.6f, 9.2f, 1.45f)
                        line(18.2f, 10.8f, 19.6f, 9.2f, 1.45f)
                        line(21f, 10.8f, 19.6f, 9.2f, 1.45f)
                        line(19.6f, 13.2f, 19.6f, 14.8f, 1.45f)
                        line(18.2f, 13.2f, 19.6f, 14.8f, 1.45f)
                        line(21f, 13.2f, 19.6f, 14.8f, 1.45f)
                    }
                    else -> Unit
                }
            }
        }
    }
}
