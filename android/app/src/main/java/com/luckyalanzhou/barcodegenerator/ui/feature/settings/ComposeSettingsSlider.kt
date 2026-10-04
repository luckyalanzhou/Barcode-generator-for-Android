package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import kotlin.math.roundToInt

internal fun parseSliderValue(text: String, range: ClosedFloatingPointRange<Float>): Float? =
    text.trim().toIntOrNull()?.toFloat()?.takeIf { it in range }

internal fun useStackedSlider(widthDp: Float, fontScale: Float): Boolean =
    widthDp < 290f || fontScale > 1.2f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    color: Color,
    onChange: (Float) -> Unit,
) {
    val sliderColors = LocalAppColorScheme.current.sliders
    val valueColor = LocalAppColorScheme.current.text.primary
    var draft by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    var inputOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onChange)
    LaunchedEffect(value) { if (!dragging) draft = value }
    DisposableEffect(Unit) {
        onDispose { if (dragging && draft != latestValue) latestOnChange(draft) }
    }
    val unit = valueText.substringAfter(' ', "")
    val shownValue = "${draft.roundToInt()} $unit"
    val fontScale = LocalDensity.current.fontScale
    fun commitDraft() {
        dragging = false
        if (draft != latestValue) latestOnChange(draft)
    }
    val slider: @Composable (Modifier) -> Unit = { sliderModifier ->
        Slider(
            value = draft,
            onValueChange = { draft = it.roundToInt().toFloat().coerceIn(range); dragging = true },
            onValueChangeFinished = ::commitDraft,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            modifier = sliderModifier.heightIn(min = 48.dp).padding(horizontal = 6.dp)
                .semantics { contentDescription = title; stateDescription = shownValue },
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            track = { sliderState ->
                val fraction = ((sliderState.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val centerY = size.height / 2f
                    val centerX = size.width * fraction
                    val gap = 10.dp.toPx()
                    val stroke = 4.dp.toPx()
                    val activeEnd = (centerX - gap).coerceAtLeast(0f)
                    val inactiveStart = (centerX + gap).coerceAtMost(size.width)
                    if (activeEnd > 0f) drawLine(sliderColors.activeTrack, Offset(0f, centerY), Offset(activeEnd, centerY), stroke, StrokeCap.Round)
                    if (inactiveStart < size.width) drawLine(sliderColors.inactiveTrack, Offset(inactiveStart, centerY), Offset(size.width, centerY), stroke, StrokeCap.Round)
                }
            },
            thumb = {
                Box(
                    Modifier.requiredSize(22.dp)
                        .background(sliderColors.thumb, CircleShape)
                        .border(1.dp, sliderColors.thumbBorder, CircleShape),
                )
            },
        )
    }
    val valueDisplay: @Composable () -> Unit = {
        val valueParts = shownValue.split(' ', limit = 2)
        Row(
            modifier = Modifier.width((74f * fontScale.coerceAtLeast(1f)).dp).heightIn(min = 48.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "精确设置$title") {
                    input = draft.roundToInt().toString(); inputOpen = true
                }.semantics { contentDescription = "$title，$shownValue，点击输入数值" }
                .padding(end = 8.dp).offset(x = 6.dp),
            // Shift the complete value display 6.dp right within the reserved
            // trailing inset, without changing the slider or row measurements.
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                valueParts.firstOrNull().orEmpty(),
                style = TextStyle(color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Normal),
                textAlign = TextAlign.Start,
                modifier = Modifier.width((38f * fontScale.coerceAtLeast(1f)).dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                valueParts.getOrNull(1).orEmpty(),
                style = TextStyle(color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End,
                modifier = Modifier.width((20f * fontScale.coerceAtLeast(1f)).dp),
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (useStackedSlider(maxWidth.value, fontScale)) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    valueDisplay()
                }
                slider(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = LocalAppDimensions.current.settingsRowHeight), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = color, fontSize = 16.sp, modifier = Modifier.width(88.dp))
                slider(Modifier.weight(1f))
                valueDisplay()
            }
        }
    }
    if (inputOpen) {
        val parsed = parseSliderValue(input, range)
        fun applyInput() {
            if (parsed != null) {
                draft = parsed
                commitDraft()
                inputOpen = false
            }
        }
        AlertDialog(
            onDismissRequest = { inputOpen = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                    label = { Text("数值（$unit）") }, isError = parsed == null,
                    supportingText = { Text("请输入 ${range.start.toInt()}～${range.endInclusive.toInt()} 的整数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyInput() }))
            },
            confirmButton = { TextButton(onClick = { applyInput() }, enabled = parsed != null) { Text("确定") } },
            dismissButton = { TextButton(onClick = { inputOpen = false }) { Text("取消") } },
        )
    }
}
