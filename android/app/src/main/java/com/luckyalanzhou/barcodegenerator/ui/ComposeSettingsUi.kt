package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@Composable
internal fun ComposeSettingsPage(activity: MainActivity) {
    activity.settingsViewModel.initialize(activity.style, activity.settingsStore.getOcrConfusionReplacementMask())
    val settings by activity.settingsViewModel.uiState.collectAsState()
    val dark = activity.isDark()
    val primary = if (dark) Color(0xfff2f4f8) else Color(0xff182230)
    val secondary = if (dark) Color(0xffaeb9c9) else Color(0xff6b7280)
    val card = if (dark) Color(0xff1b222d) else Color(0xfff7f9fc)
    val button = if (dark) Color(0xff233246) else Color(0xffeef3f9)
    val accent = if (dark) Color(0xffb8ccff) else Color(0xff2864d7)
    var schemeMenu by remember { mutableStateOf(false) }
    var ocrMenu by remember { mutableStateOf(false) }
    var schemeButtonWidth by remember { mutableIntStateOf(0) }
    var ocrButtonWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val schemeAnchorWidth = schemeButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val ocrAnchorWidth = ocrButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }

    fun persist(next: SettingsUiState = settings) {
        val currentStyle = activity.settingsViewModel.style
        val schemeChanged = currentStyle.colorScheme != next.scheme
        activity.settingsViewModel.updateStyle(
            currentStyle.copy(
                textSize = next.textSize,
                barHeight = next.barHeight.toInt(),
                barWidth = next.barWidth,
                margin = next.margin.toInt(),
                showFormat = next.showFormat,
                colorScheme = next.scheme,
            ),
        )
        // DataStore 写入是异步的；只有外观方案变化时才需要重建主题，并且必须等写入完成，
        // 否则 Activity 重建可能在旧值落盘前读取到旧主题，导致设置看似没有生效。
        val saveJob = activity.saveStyle()
        if (schemeChanged) {
            activity.lifecycleScope.launch {
                saveJob.join()
                if (!activity.isFinishing) activity.applyAppearance()
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionLabel("显示", secondary)
        SettingCard(card) {
            SettingRow("外观", primary, trailing = {
                Box {
                    BoxedSettingButton(
                        text = when (settings.scheme) { "dark" -> "深色"; "light" -> "浅色"; else -> "跟随系统" },
                        color = button,
                        contentColor = primary,
                        modifier = Modifier.onGloballyPositioned { schemeButtonWidth = it.size.width },
                        onClick = { schemeMenu = true }
                    )
                    AnchoredDropdownMenu(
                        dark = dark,
                        expanded = schemeMenu,
                        onDismissRequest = { schemeMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                        menuWidth = (schemeAnchorWidth ?: 132.dp).coerceAtLeast(132.dp),
                        anchorWidth = schemeAnchorWidth,
                        alignEndWithAnchor = true,
                    ) {
                        listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark").forEachIndexed { index, (label, value) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                        DropdownMenuItem(text = { Text(label) }, onClick = { schemeMenu = false; persist(settings.copy(scheme = value)) })
                        }
                    }
                }
            })
        }

        SettingSectionLabel("条码", secondary)
        SettingCard(card) {
            SettingSliderRow("文字大小", settings.textSize, 10f..24f, "${settings.textSize.toInt()} sp", primary, accent) { activity.settingsViewModel.setTextSize(it); persist(settings.copy(textSize = it)) }
            SettingDivider(dark)
            SettingSliderRow("条码高度", settings.barHeight, 30f..150f, "${settings.barHeight.toInt()} dp", primary, accent) { activity.settingsViewModel.setBarHeight(it); persist(settings.copy(barHeight = it)) }
            SettingDivider(dark)
            SettingSliderRow("条码宽度", settings.barWidth, 120f..360f, "${settings.barWidth.toInt()} dp", primary, accent) { activity.settingsViewModel.setBarWidth(it); persist(settings.copy(barWidth = it)) }
            SettingDivider(dark)
            SettingSliderRow("条码间距", settings.margin, 0f..40f, "${settings.margin.toInt()} dp", primary, accent) { activity.settingsViewModel.setMargin(it); persist(settings.copy(margin = it)) }
            SettingDivider(dark)
            SettingRow("条码格式", primary, trailing = {
                Box(
                    Modifier.height(40.dp).width(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Switch(checked = settings.showFormat, onCheckedChange = { activity.settingsViewModel.setShowFormat(it); persist(settings.copy(showFormat = it)) })
                }
            })
            SettingDivider(dark)
            SettingRow("OCR 字符纠错", primary, trailing = {
                val selectedLabels = ocrReplacementLabels.filter { (_, bit) -> settings.ocrMask and bit != 0 }.map { it.first }
                Box {
                    BoxedSettingButton(
                        text = when (selectedLabels.size) { 0 -> "关闭"; 1 -> selectedLabels.first(); else -> "启用 ${selectedLabels.size} 项" },
                        color = button,
                        contentColor = primary,
                        modifier = Modifier.onGloballyPositioned { ocrButtonWidth = it.size.width },
                        onClick = { ocrMenu = true }
                    )
                    AnchoredDropdownMenu(
                        dark = dark,
                        expanded = ocrMenu,
                        onDismissRequest = { ocrMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                        menuWidth = (ocrAnchorWidth ?: 164.dp).coerceAtLeast(164.dp),
                        anchorWidth = ocrAnchorWidth,
                        alignEndWithAnchor = true,
                    ) {
                        ocrReplacementLabels.forEachIndexed { index, (label, bit) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = settings.ocrMask and bit != 0, onCheckedChange = null); Spacer(Modifier.width(6.dp)); Text(label, maxLines = 1, softWrap = false) } },
                                onClick = { val mask = if (settings.ocrMask and bit == 0) settings.ocrMask or bit else settings.ocrMask and bit.inv(); activity.settingsViewModel.setOcrMask(mask); activity.settingsStore.setOcrConfusionReplacementMask(mask) }
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
                val defaults = settings.copy(textSize = 14f, barHeight = 55f, barWidth = 220f, margin = 4f)
                activity.settingsViewModel.setTextSize(defaults.textSize); activity.settingsViewModel.setBarHeight(defaults.barHeight); activity.settingsViewModel.setBarWidth(defaults.barWidth); activity.settingsViewModel.setMargin(defaults.margin); persist(defaults); activity.toast("已恢复条码默认设置")
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
@OptIn(ExperimentalMaterial3Api::class)
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
                // 关闭 Material3 默认 thumb，避免它与下方自定义圆球叠加成外部圆环。
                thumbColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                // 轨道由下方 track 自绘，避免 Material3 默认厚轨道形成外框视觉。
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            track = { sliderState ->
                val fraction = ((sliderState.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(accent.copy(alpha = .18f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(accent, RoundedCornerShape(2.dp)),
                    )
                }
            },
            thumb = {
                Box(
                    // 明确裁剪为纯圆形，触摸区域仍由 Slider 保留，不绘制额外外框。
                    Modifier.requiredSize(18.dp).clip(CircleShape).background(accent),
                )
            },
        )
        Text(valueText, color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(66.dp))
    }
}

@Composable
private fun SettingDivider(dark: Boolean) { Spacer(Modifier.fillMaxWidth().height(1.dp).background(if (dark) Color(0xff3b4658).copy(alpha = .38f) else Color(0xff667085).copy(alpha = .12f))) }

@Composable
private fun BoxedSettingButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor), shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), modifier = modifier.height(40.dp)) { Text(text, maxLines = 1) }
}

@Composable
private fun SmallSettingButton(text: String, color: Color, buttonColor: Color, onClick: () -> Unit) { Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = color), shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), modifier = Modifier.height(40.dp)) { Text(text) } }
