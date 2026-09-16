package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeSettingsPage(activity: MainActivity) {
    val dark = activity.isDark()
    val primary = if (dark) Color(0xfff2f4f8) else Color(0xff182230)
    val secondary = if (dark) Color(0xffaeb9c9) else Color(0xff6b7280)
    val card = if (dark) Color(0xff1b222d) else Color(0xfff7f9fc)
    val button = if (dark) Color(0xff233246) else Color(0xffeef3f9)
    val accent = if (dark) Color(0xffb8ccff) else Color(0xff2864d7)
    var scheme by remember { mutableStateOf(activity.style.colorScheme) }
    var schemeMenu by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(activity.style.showFormat) }
    var ocrMask by remember { mutableIntStateOf(activity.settingsStore.getOcrConfusionReplacementMask()) }
    var ocrMenu by remember { mutableStateOf(false) }
    var textSize by remember { mutableFloatStateOf(activity.style.textSize) }
    var barHeight by remember { mutableFloatStateOf(activity.style.barHeight.toFloat()) }
    var barWidth by remember { mutableFloatStateOf(activity.style.barWidth) }
    var margin by remember { mutableFloatStateOf(activity.style.margin.toFloat()) }

    fun persist() {
        activity.style.textSize = textSize
        activity.style.barHeight = barHeight.toInt()
        activity.style.barWidth = barWidth
        activity.style.margin = margin.toInt()
        activity.style.showFormat = showFormat
        activity.style.colorScheme = scheme
        activity.style.barColor = android.graphics.Color.BLACK
        activity.style.bgColor = android.graphics.Color.WHITE
        activity.style.showText = true
        activity.style.textPosition = "bottom"
        activity.saveStyle()
        activity.applyAppearance()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionLabel("显示", secondary)
        SettingCard(card) {
            SettingRow("外观", primary, trailing = {
                Box {
                    BoxedSettingButton(
                        text = when (scheme) { "dark" -> "深色"; "light" -> "浅色"; else -> "跟随系统" },
                        color = button,
                        contentColor = primary,
                        onClick = { schemeMenu = true }
                    )
                    DropdownMenu(
                        expanded = schemeMenu,
                        onDismissRequest = { schemeMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color.White.copy(alpha = .94f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                    ) {
                        listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark").forEachIndexed { index, (label, value) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            DropdownMenuItem(text = { Text(label) }, onClick = { scheme = value; schemeMenu = false; persist() })
                        }
                    }
                }
            })
        }

        SettingSectionLabel("条码", secondary)
        SettingCard(card) {
            SettingSliderRow("文字大小", textSize, 10f..24f, "${textSize.toInt()} sp", primary, accent) { textSize = it; persist() }
            SettingDivider(dark)
            SettingSliderRow("条码高度", barHeight, 30f..150f, "${barHeight.toInt()} dp", primary, accent) { barHeight = it; persist() }
            SettingDivider(dark)
            SettingSliderRow("条码宽度", barWidth, 120f..360f, "${barWidth.toInt()} dp", primary, accent) { barWidth = it; persist() }
            SettingDivider(dark)
            SettingSliderRow("条码间距", margin, 0f..40f, "${margin.toInt()} dp", primary, accent) { margin = it; persist() }
            SettingDivider(dark)
            SettingRow("条码格式", primary, trailing = {
                Switch(checked = showFormat, onCheckedChange = { showFormat = it; persist() })
            })
            SettingDivider(dark)
            SettingRow("OCR 字符纠错", primary, trailing = {
                val selected = ocrReplacementLabels.count { (_, bit) -> ocrMask and bit != 0 }
                Box {
                    BoxedSettingButton(
                        text = when (selected) { 0 -> "关闭"; 4 -> "全部启用"; else -> "已启用 ${selected} 项" },
                        color = button,
                        contentColor = primary,
                        onClick = { ocrMenu = true }
                    )
                    DropdownMenu(
                        expanded = ocrMenu,
                        onDismissRequest = { ocrMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color.White.copy(alpha = .94f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                    ) {
                        ocrReplacementLabels.forEachIndexed { index, (label, bit) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = ocrMask and bit != 0, onCheckedChange = null); Spacer(Modifier.width(6.dp)); Text(label) } },
                                onClick = { ocrMask = if (ocrMask and bit == 0) ocrMask or bit else ocrMask and bit.inv(); activity.settingsStore.setOcrConfusionReplacementMask(ocrMask) }
                            )
                        }
                    }
                }
            })
        }

        SettingSectionLabel("工具", secondary)
        SettingCard(card) {
            SettingActionRow("局域网文件分享", "启动", primary, button) { activity.enterLanShare() }
            SettingDivider(dark)
            SettingActionRow("恢复默认设置", "恢复", primary, button) {
                textSize = 14f; barHeight = 55f; barWidth = 220f; margin = 4f; persist(); activity.toast("已恢复条码默认设置")
            }
            SettingDivider(dark)
            SettingRow("收藏备份", primary, trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallSettingButton("导入", primary, button) { activity.restoreFavoritesImport() }
                    SmallSettingButton("导出", primary, button) { activity.createFavoritesExport() }
                }
            })
            if (BuildConfig.DEBUG_LOG_EXPORT) {
                SettingDivider(dark)
                SettingActionRow("功能自检", "打开", primary, button) { activity.showFeatureSelfTestDialog() }
            }
        }

        SettingCard(card) {
            Text("关于", color = primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("作者：Alan", color = secondary, fontSize = 13.sp)
                    Text("版本：${BuildConfig.VERSION_NAME}", color = secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                OutlinedButton(onClick = { activity.checkForUpdates(silent = false) }, shape = RoundedCornerShape(12.dp)) { Text("检查更新") }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private val ocrReplacementLabels = listOf("O → 0" to SettingsStore.OCR_REPLACE_O_ZERO, "I → 1" to SettingsStore.OCR_REPLACE_I_ONE, "S → 5" to SettingsStore.OCR_REPLACE_S_FIVE, "B → 8" to SettingsStore.OCR_REPLACE_B_EIGHT)

@Composable
private fun SettingSectionLabel(text: String, color: Color) { Text(text, color = color, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }

@Composable
private fun SettingCard(color: Color, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().background(color, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 4.dp), content = content) }

@Composable
private fun SettingRow(title: String, color: Color, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f)); trailing() }
}

@Composable
private fun SettingActionRow(title: String, action: String, color: Color, buttonColor: Color, onClick: () -> Unit) {
    SettingRow(title, color) { BoxedSettingButton(action, buttonColor, color, onClick) }
}

@Composable
private fun SettingSliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, color: Color, accent: Color, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = color, fontSize = 16.sp, modifier = Modifier.width(88.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            modifier = Modifier.weight(1f).height(34.dp).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = .18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(valueText, color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(66.dp))
    }
}

@Composable
private fun SettingDivider(dark: Boolean) { Spacer(Modifier.fillMaxWidth().height(1.dp).background(if (dark) Color(0xff3b4658).copy(alpha = .38f) else Color(0xff667085).copy(alpha = .12f))) }

@Composable
private fun BoxedSettingButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor), shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), modifier = Modifier.height(40.dp)) { Text(text, maxLines = 1) }
}

@Composable
private fun SmallSettingButton(text: String, color: Color, buttonColor: Color, onClick: () -> Unit) { Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = color), shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), modifier = Modifier.height(40.dp)) { Text(text) } }
