package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
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

/** 解析数值输入并校验范围；非法或越界文本返回 null，由界面显示错误状态且不保存。 */
internal fun parseSliderValue(text: String, range: ClosedFloatingPointRange<Float>): Float? =
    text.trim().toIntOrNull()?.toFloat()?.takeIf { it in range }

internal fun useStackedSlider(widthDp: Float, fontScale: Float): Boolean =
    widthDp < 290f || fontScale > 1.2f

/**
 * 设置页的数值滑块：拖动后在结束时提交；点击右侧数值可直接键盘输入，失焦时校验并保存。
 * 输入越界时将数值标红并提示合法区间，确认提示后恢复到最近一次有效值。
 */
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
    var editing by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(TextFieldValue(value.roundToInt().toString())) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onChange)
    LaunchedEffect(value) { if (!dragging) draft = value
        if (!editing && !warning) input = TextFieldValue(value.roundToInt().toString()) }
    DisposableEffect(Unit) {
        onDispose {
            val finalValue = if (editing) parseSliderValue(input.text, range) else if (dragging) draft else null
            if (finalValue != null && finalValue != latestValue) latestOnChange(finalValue)
        }
    }
    val unit = valueText.substringAfter(' ', "")
    val shownValue = "${draft.roundToInt()} $unit"
    val fontScale = LocalDensity.current.fontScale
    fun commitDraft() {
        dragging = false
        if (draft != latestValue) latestOnChange(draft)
    }
    fun applyInput() {
        val parsed = parseSliderValue(input.text, range)
        if (parsed == null) warning = true
        else { draft = parsed; commitDraft() }
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
                .semantics { contentDescription = "$title，$shownValue，点击输入数值" }
                .padding(end = 8.dp).offset(x = 6.dp),
            // 只将数值显示整体右移 6.dp，利用预留尾部空间，不改变滑块或整行测量宽度。
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = if (editing || warning) input else TextFieldValue(draft.roundToInt().toString()),
                onValueChange = { input = it }, singleLine = true,
                textStyle = TextStyle(color = if ((editing || warning) && parseSliderValue(input.text, range) == null)
                    LocalAppColorScheme.current.content.deleteIcon else valueColor, fontSize = 15.sp),
                cursorBrush = SolidColor(valueColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide(); focusManager.clearFocus()
                }),
                decorationBox = { field -> Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { field() } },
                modifier = Modifier.width((38f * fontScale.coerceAtLeast(1f)).dp).heightIn(min = 48.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused && !editing) {
                            val text = draft.roundToInt().toString()
                            input = TextFieldValue(text, TextRange(0, text.length)); editing = true
                        } else if (!state.isFocused && editing) {
                            editing = false; applyInput()
                        }
                    }.semantics { contentDescription = "$title，范围 ${range.start.toInt()} 到 ${range.endInclusive.toInt()}" },
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
    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false; input = TextFieldValue(draft.roundToInt().toString()) },
            title = { Text("数值超出范围") },
            text = { Text("$title 请输入 ${range.start.toInt()}～${range.endInclusive.toInt()} 的整数，未保存无效数值。") },
            confirmButton = { TextButton(modifier = Modifier.globalButtonChrome(), onClick = {
                warning = false; input = TextFieldValue(draft.roundToInt().toString())
            }) { Text("知道了") } },
        )
    }
}
