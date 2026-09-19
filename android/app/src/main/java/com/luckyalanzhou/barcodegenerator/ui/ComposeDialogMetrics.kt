package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

internal val LocalDialogMetric = compositionLocalOf<(String) -> Unit> { {} }
internal val LocalDialogSelectedElement = compositionLocalOf<MutableState<String>?> { null }
internal val LocalDialogElementBounds = compositionLocalOf<MutableState<Map<String, DialogElementBounds>>?> { null }
internal val LocalDialogElementVisuals = compositionLocalOf<MutableState<Map<String, DialogElementVisual>>?> { null }
internal val LocalDialogInspectOnly = compositionLocalOf { false }

internal data class DialogElementBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class DialogElementVisual(
    val textColor: String,
    val fontSize: String,
    val fontWeight: String,
    val backgroundColor: String? = null,
    val borderColor: String? = null,
)

internal fun Color.hexValue(): String = "#%08X".format(toArgb())

@Composable
internal fun Modifier.dialogMetricBounds(label: String, selected: Boolean, visual: DialogElementVisual? = null): Modifier {
    val boundsState = LocalDialogElementBounds.current
    val visualsState = LocalDialogElementVisuals.current
    val boundsModifier = onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInRoot()
        val measured = DialogElementBounds(rect.left, rect.top, rect.right, rect.bottom)
        if (boundsState?.value?.get(label) != measured) {
            boundsState?.value = boundsState.value.orEmpty() + (label to measured)
        }
        if (visual != null && visualsState?.value?.get(label) != visual) {
            visualsState?.value = visualsState.value.orEmpty() + (label to visual)
        }
    }
    return boundsModifier.then(if (!selected) Modifier else Modifier.drawWithContent {
        drawContent()
        val stroke = 2.dp.toPx()
        val marker = 5.dp.toPx()
        val red = Color.Red
        drawRect(red, style = Stroke(width = stroke))
        drawLine(red, Offset(0f, 0f), Offset(marker, 0f), strokeWidth = stroke)
        drawLine(red, Offset(0f, 0f), Offset(0f, marker), strokeWidth = stroke)
        drawLine(red, Offset(size.width, 0f), Offset(size.width - marker, 0f), strokeWidth = stroke)
        drawLine(red, Offset(size.width, 0f), Offset(size.width, marker), strokeWidth = stroke)
        drawLine(red, Offset(0f, size.height), Offset(marker, size.height), strokeWidth = stroke)
        drawLine(red, Offset(0f, size.height), Offset(0f, size.height - marker), strokeWidth = stroke)
        drawLine(red, Offset(size.width, size.height), Offset(size.width - marker, size.height), strokeWidth = stroke)
        drawLine(red, Offset(size.width, size.height), Offset(size.width, size.height - marker), strokeWidth = stroke)
    })
}

@Composable
internal fun Modifier.dialogMetricTarget(label: String, visual: DialogElementVisual): Modifier {
    val selected = LocalDialogSelectedElement.current?.value == label
    val onMetric = LocalDialogMetric.current
    return dialogMetricBounds(label, selected, visual).clickable { onMetric(label) }
}
