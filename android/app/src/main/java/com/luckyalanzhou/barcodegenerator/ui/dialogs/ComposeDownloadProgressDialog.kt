package com.luckyalanzhou.barcodegenerator.ui.dialogs

import java.text.NumberFormat
import com.luckyalanzhou.barcodegenerator.ui.support.localization.rememberApplicationFormattingLocale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig

import com.luckyalanzhou.barcodegenerator.presentation.UpdateDownloadUiState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun ComposeSegmentedProgress(progress: Int) {
    val animation = ComposeAnimationConfig
    val animatedProgress = animateFloatAsState(
        targetValue = progress.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = animation.progressDurationMillis),
        label = "downloadProgress",
    )
    val fill = LocalAppColorScheme.current.controls.accent
    val track = LocalAppColorScheme.current.controls.progressTrack
    val highlight = LocalAppColorScheme.current.controls.progressHighlight
    Canvas(
        modifier = Modifier.fillMaxWidth().height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(track)
            .border(1.dp, fill.copy(alpha = 0.45f), RoundedCornerShape(7.dp)),
    ) {
        val filledWidth = size.width * animatedProgress.value
        if (filledWidth > 0f) {
            val glowWidth = 32.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(fill.copy(alpha = .70f), fill, highlight, fill),
                    startX = (filledWidth - glowWidth).coerceAtLeast(0f),
                    endX = (filledWidth + glowWidth).coerceAtMost(size.width),
                ),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size((filledWidth - 2.dp.toPx()).coerceAtLeast(0f), size.height - 2.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ComposeIndeterminateProgress() {
    val fill = LocalAppColorScheme.current.controls.accent
    val track = LocalAppColorScheme.current.controls.progressTrack
    val transition = rememberInfiniteTransition(label = "downloadIndeterminate")
    val offset = transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(ComposeAnimationConfig.indeterminateProgressDurationMillis, easing = LinearEasing),
        ),
        label = "downloadShimmer",
    )
    Canvas(
        Modifier.fillMaxWidth().height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(track)
            .border(1.dp, fill.copy(alpha = .45f), RoundedCornerShape(7.dp)),
    ) {
        val center = size.width * offset.value
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(fill.copy(alpha = .08f), fill, fill.copy(alpha = .08f)),
                startX = center - 64.dp.toPx(),
                endX = center + 64.dp.toPx(),
            ),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
    }
}

@Composable
internal fun ComposeDownloadProgressDialog(
    downloadState: UpdateDownloadUiState,
    dark: Boolean,
    onCancel: () -> Unit,
) {
    ComposeGlassDialogCard(dark) {
        Text(stringResource(R.string.download_update), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
        if (downloadState.indeterminate) ComposeIndeterminateProgress() else ComposeSegmentedProgress(downloadState.progress)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            val context = LocalContext.current
            val bytes = downloadState.downloadedBytes
            val status = when {
                bytes == null -> stringResource(R.string.download_preparing)
                downloadState.indeterminate -> stringResource(
                    R.string.update_downloaded_size,
                    android.text.format.Formatter.formatShortFileSize(context, bytes),
                )
                else -> stringResource(R.string.update_downloading)
            }
            Text(status, modifier = Modifier.weight(1f), color = LocalAppColorScheme.current.text.secondary, fontSize = 14.sp, maxLines = 1)
            if (!downloadState.indeterminate) Text(NumberFormat.getPercentInstance(rememberApplicationFormattingLocale()).format(downloadState.progress.coerceIn(0, 100) / 100.0), color = LocalAppColorScheme.current.text.link, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            DialogAction(stringResource(R.string.cancel_download), dark, onCancel)
        }
    }
}
