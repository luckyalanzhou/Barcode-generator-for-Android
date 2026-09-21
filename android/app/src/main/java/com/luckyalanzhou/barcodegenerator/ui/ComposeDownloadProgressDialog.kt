package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.BarcodeViewModel
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
private fun ComposeSegmentedProgress(progress: Int) {
    val animation = rememberComposeAnimationConfig()
    val animated = animateFloatAsState(
        targetValue = progress.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = animation.progressDurationMillis),
        label = "downloadProgress",
    ).value
    val fill = LocalAppColorScheme.current.controls.progress
    val track = LocalAppColorScheme.current.controls.progressTrack
    val highlight = LocalAppColorScheme.current.controls.progressHighlight
    Canvas(
        modifier = Modifier.fillMaxWidth().height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(track)
            .border(1.dp, fill.copy(alpha = 0.45f), RoundedCornerShape(7.dp)),
    ) {
        val filledWidth = size.width * animated
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
    val fill = LocalAppColorScheme.current.controls.progress
    val track = LocalAppColorScheme.current.controls.progressTrack
    val transition = rememberInfiniteTransition(label = "downloadIndeterminate")
    val offset by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "downloadShimmer",
    )
    Canvas(
        Modifier.fillMaxWidth().height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(track)
            .border(1.dp, fill.copy(alpha = .45f), RoundedCornerShape(7.dp)),
    ) {
        val center = size.width * offset
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
    viewModel: BarcodeViewModel,
    dark: Boolean,
    onCancel: () -> Unit,
) {
    val downloadState by viewModel.updateDownloadUiState.collectAsStateWithLifecycle()
    ComposeGlassDialogCard(dark) {
        Text("下载更新", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
        if (downloadState.indeterminate) ComposeIndeterminateProgress() else ComposeSegmentedProgress(downloadState.progress)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(downloadState.status, modifier = Modifier.weight(1f), color = LocalAppColorScheme.current.text.secondary, fontSize = 14.sp, maxLines = 1)
            if (!downloadState.indeterminate) Text("${downloadState.progress.coerceIn(0, 100)}%", color = LocalAppColorScheme.current.text.link, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            DialogAction("取消下载", dark, onCancel)
        }
    }
}
