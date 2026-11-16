package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
    stacked: Boolean,
    leading: (@Composable () -> Unit)? = null,
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
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val titleTextStyle = LocalTextStyle.current.copy(color = color, fontSize = 16.sp)
    val textMeasurer = rememberTextMeasurer()
    val titleWidthPx = remember(title, titleTextStyle, density, layoutDirection, textMeasurer) {
        textMeasurer.measure(
            text = AnnotatedString(title),
            style = titleTextStyle,
            softWrap = false,
            maxLines = 1,
            layoutDirection = layoutDirection,
            density = density,
        ).size.width
    }
    val titleWidth = with(density) { titleWidthPx.toDp() }.coerceAtLeast(1.dp)
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
    val fontScale = density.fontScale
    val valueWidth = (74f * fontScale.coerceAtLeast(1f)).dp
    val leadingWidth = if (leading == null) 0.dp else 40.dp
    val minimumSliderWidth = 64.dp
    fun commitDraft() {
        dragging = false
        if (draft != latestValue) latestOnChange(draft)
    }
    fun applyInput() {
        val parsed = parseSliderValue(input.text, range)
        if (parsed == null) warning = true
        else { warning = false; draft = parsed; commitDraft() }
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
        val valueDescription = stringResource(R.string.slider_edit_value_description, title, shownValue)
        val rangeDescription = stringResource(R.string.slider_range_description, title, range.start.toInt(), range.endInclusive.toInt())
        val valueParts = shownValue.split(' ', limit = 2)
        Row(
            modifier = Modifier.width(valueWidth).heightIn(min = 48.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .semantics { contentDescription = valueDescription }
                .padding(end = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = if (editing || warning) input else TextFieldValue(draft.roundToInt().toString()),
                onValueChange = { input = it; warning = false }, singleLine = true,
                textStyle = TextStyle(color = if ((editing || warning) && parseSliderValue(input.text, range) == null)
                    LocalAppColorScheme.current.content.deleteIcon else valueColor, fontSize = 16.sp,
                    fontFamily = MiSansFontFamily, textAlign = TextAlign.End),
                cursorBrush = SolidColor(valueColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide(); focusManager.clearFocus()
                }),
                decorationBox = { field -> Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { field() } },
                modifier = Modifier.width((38f * fontScale.coerceAtLeast(1f)).dp).heightIn(min = 48.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused && !editing) {
                            val text = if (warning) input.text else draft.roundToInt().toString()
                            input = TextFieldValue(text, TextRange(0, text.length)); editing = true
                        } else if (!state.isFocused && editing) {
                            editing = false; applyInput()
                        }
                    }.semantics { contentDescription = rangeDescription },
            )
            Spacer(Modifier.width(4.dp))
            Text(
                valueParts.getOrNull(1).orEmpty(),
                style = TextStyle(color = LocalAppColorScheme.current.text.secondary, fontSize = 14.sp,
                    fontFamily = MiSansFontFamily),
                textAlign = TextAlign.Start,
                modifier = Modifier.width((20f * fontScale.coerceAtLeast(1f)).dp),
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val titleAndValueWidth = leadingWidth + titleWidth + valueWidth
        val showInline = !stacked && availableWidth >= titleAndValueWidth + minimumSliderWidth
        if (showInline) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = LocalAppDimensions.current.settingsRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = titleTextStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(titleWidth),
                )
                slider(Modifier.weight(1f).widthIn(min = minimumSliderWidth))
                valueDisplay()
            }
        } else {
            Column {
                if (availableWidth >= titleAndValueWidth) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (leading != null) {
                            leading()
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            title,
                            style = titleTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(titleWidth),
                        )
                        valueDisplay()
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (leading != null) {
                            leading()
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            title,
                            style = titleTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        valueDisplay()
                    }
                }
                slider(Modifier.fillMaxWidth())
            }
        }
    }
    if (warning) {
        Text(
            stringResource(R.string.slider_value_prompt, title, range.start.toInt(), range.endInclusive.toInt()),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = LocalAppColorScheme.current.text.destructive,
            fontSize = 14.sp,
        )
    }
}
