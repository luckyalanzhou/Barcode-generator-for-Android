package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    color: Color,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    val sliderAccent = accent.copy(alpha = 0.72f)
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = color, fontSize = 16.sp, modifier = Modifier.width(88.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            modifier = Modifier.weight(1f).height(34.dp).padding(horizontal = 6.dp),
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
                    if (activeEnd > 0f) drawLine(sliderAccent, Offset(0f, centerY), Offset(activeEnd, centerY), stroke, StrokeCap.Round)
                    if (inactiveStart < size.width) drawLine(sliderAccent.copy(alpha = .18f), Offset(inactiveStart, centerY), Offset(size.width, centerY), stroke, StrokeCap.Round)
                }
            },
            thumb = { Box(Modifier.requiredSize(18.dp).clip(CircleShape).background(sliderAccent)) },
        )
        val valueParts = valueText.split(' ', limit = 2)
        Row(
            modifier = Modifier.width(74.dp).padding(end = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val valueStyle = TextStyle(color = sliderAccent, fontSize = 15.sp, fontWeight = FontWeight.Normal)
            Text(valueParts.firstOrNull().orEmpty(), style = valueStyle, textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
            Text(valueParts.getOrNull(1).orEmpty(), style = valueStyle, textAlign = TextAlign.End, modifier = Modifier.width(20.dp))
        }
    }
}
