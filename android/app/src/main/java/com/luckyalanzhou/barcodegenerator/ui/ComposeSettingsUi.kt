package com.luckyalanzhou.barcodegenerator

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
internal fun ComposeSettingsPage(
    settingsViewModel: SettingsViewModel,
    dark: Boolean,
    onApplyAppearance: () -> Unit,
    onEnterLanShare: () -> Unit,
    onRestoreFavorites: () -> Unit,
    onExportFavorites: () -> Unit,
    onFeatureSelfTest: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val colors = rememberSettingsColors(dark)
    var schemeMenu by remember { mutableStateOf(false) }
    var ocrMenu by remember { mutableStateOf(false) }
    var schemeButtonWidth by remember { mutableIntStateOf(0) }
    var ocrButtonWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val schemeWidth = schemeButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val ocrWidth = ocrButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }

    fun persist(next: SettingsUiState = settings) {
        val style = settings.style
        val schemeChanged = style.colorScheme != next.scheme
        settingsViewModel.updateStyle(
            style.copy(
                textSize = next.textSize,
                barHeight = next.barHeight.toInt(),
                barWidth = next.barWidth,
                margin = next.margin.toInt(),
                showFormat = next.showFormat,
                colorScheme = next.scheme,
            ),
        )
        val saveJob = settingsViewModel.save()
        if (schemeChanged) {
            scope.launch {
                saveJob.join()
                onApplyAppearance()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("settings-appearance") {
            SettingsSection("显示", colors.secondary) {
                SettingsCard(colors.card, dark) {
                    SettingsRow("外观", colors.primary) {
                        Box {
                            SettingsDropdownButton(
                                text = when (settings.scheme) {
                                    "dark" -> "深色"
                                    "light" -> "浅色"
                                    else -> "跟随系统"
                                },
                                color = colors.button,
                                contentColor = colors.primary,
                                onClick = { schemeMenu = true },
                                onMeasured = { schemeButtonWidth = it },
                            )
                            SettingsDropdown(
                                dark = dark,
                                expanded = schemeMenu,
                                menuWidth = 132.dp,
                                anchorWidth = schemeWidth,
                                onDismiss = { schemeMenu = false },
                            ) {
                                listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark").forEachIndexed { index, (label, value) ->
                                    if (index > 0) ComposeDropdownDivider(dark)
                                    androidx.compose.material3.DropdownMenuItem(
                                        modifier = Modifier.height(40.dp),
                                        text = { Text(label) },
                                        onClick = { schemeMenu = false; persist(settings.copy(scheme = value)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item("settings-barcode") {
            SettingsSection("条码", colors.secondary) {
                SettingsCard(colors.card, dark) {
                    SettingsSliderRow("文字大小", settings.textSize, 10f..24f, "${settings.textSize.toInt()} sp", colors.primary, colors.accent) {
                        settingsViewModel.setTextSize(it); persist(settings.copy(textSize = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码高度", settings.barHeight, 30f..150f, "${settings.barHeight.toInt()} dp", colors.primary, colors.accent) {
                        settingsViewModel.setBarHeight(it); persist(settings.copy(barHeight = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码宽度", settings.barWidth, 120f..360f, "${settings.barWidth.toInt()} dp", colors.primary, colors.accent) {
                        settingsViewModel.setBarWidth(it); persist(settings.copy(barWidth = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码间距", settings.margin, 0f..40f, "${settings.margin.toInt()} dp", colors.primary, colors.accent) {
                        settingsViewModel.setMargin(it); persist(settings.copy(margin = it))
                    }
                    SettingsDivider(dark)
                    SettingsRow("显示条码格式", colors.primary) {
                        SettingsToggle(
                            checked = settings.showFormat,
                            dark = dark,
                            modifier = Modifier.padding(end = 8.dp),
                            onCheckedChange = { next ->
                                settingsViewModel.setShowFormat(next)
                                persist(settings.copy(showFormat = next))
                            },
                        )
                    }
                    SettingsDivider(dark)
                    SettingsRow("OCR 字符纠错", colors.primary) {
                        val selected = ocrReplacementLabels.filter { (_, bit) -> settings.ocrMask and bit != 0 }.map { it.first }
                        Box {
                            SettingsDropdownButton(
                                text = when (selected.size) { 0 -> "关闭"; 1 -> selected.first(); else -> "启用 ${selected.size} 项" },
                                color = colors.button,
                                contentColor = colors.primary,
                                onClick = { ocrMenu = true },
                                onMeasured = { ocrButtonWidth = it },
                            )
                            SettingsDropdown(
                                dark = dark,
                                expanded = ocrMenu,
                                menuWidth = 164.dp,
                                anchorWidth = ocrWidth,
                                onDismiss = { ocrMenu = false },
                            ) {
                                ocrReplacementLabels.forEachIndexed { index, (label, bit) ->
                                    if (index > 0) ComposeDropdownDivider(dark)
                                    androidx.compose.material3.DropdownMenuItem(
                                        modifier = Modifier.height(40.dp),
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = settings.ocrMask and bit != 0, onCheckedChange = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text(label, maxLines = 1, softWrap = false)
                                            }
                                        },
                                        onClick = {
                                            val mask = if (settings.ocrMask and bit == 0) settings.ocrMask or bit else settings.ocrMask and bit.inv()
                                            settingsViewModel.setOcrMaskPersisted(mask)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item("settings-tools") {
            SettingsSection("工具", colors.secondary) {
                SettingsCard(colors.card, dark) {
                    SettingsActionRow("局域网文件分享", "启动", colors.primary, colors.button, onEnterLanShare)
                    SettingsDivider(dark)
                    SettingsActionRow("恢复默认设置", "恢复", colors.primary, colors.button) {
                        val defaults = settings.copy(textSize = 14f, barHeight = 55f, barWidth = 220f, margin = 4f)
                        settingsViewModel.setTextSize(defaults.textSize)
                        settingsViewModel.setBarHeight(defaults.barHeight)
                        settingsViewModel.setBarWidth(defaults.barWidth)
                        settingsViewModel.setMargin(defaults.margin)
                        persist(defaults)
                        onNotice("已恢复条码默认设置")
                    }
                    SettingsDivider(dark)
                    SettingsRow("收藏备份", colors.primary) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsSmallButton("导入", colors.primary, colors.button, onRestoreFavorites)
                            SettingsSmallButton("导出", colors.primary, colors.button, onExportFavorites)
                        }
                    }
                    if (BuildConfig.DEBUG_LOG_EXPORT) {
                        SettingsDivider(dark)
                        SettingsActionRow("功能自检", "打开", colors.primary, colors.button, onFeatureSelfTest)
                    }
                }
            }
        }

        item("settings-about") {
            SettingsCard(colors.card, dark) {
                Text("关于", color = colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("作者：Alan", color = colors.secondary, fontSize = 13.sp)
                        Text("版本：${BuildConfig.VERSION_NAME}", color = colors.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    ComposeGenerateActionButton(
                        icon = null,
                        iconDescription = "检查更新",
                        label = "检查更新",
                        containerColor = colors.button,
                        contentColor = colors.primary,
                        borderColor = colors.primary.copy(alpha = if (dark) .32f else .22f),
                        modifier = Modifier.width(132.dp),
                        iconSize = 20.dp,
                        contentSpacing = 5.dp,
                        onClick = onCheckForUpdates,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private data class SettingsColors(
    val primary: Color,
    val secondary: Color,
    val card: Color,
    val button: Color,
    val accent: Color,
)

@Composable
private fun rememberSettingsColors(dark: Boolean) = remember(dark) {
    SettingsColors(
        primary = if (dark) Color(0xfff2f4f8) else Color(0xff182230),
        secondary = if (dark) Color(0xffaeb9c9) else Color(0xff6b7280),
        card = if (dark) Color(0xff1b222d) else Color(0xfff7f9fc),
        button = if (dark) Color(0xff233246) else Color(0xffeef3f9),
        accent = if (dark) Color(0xffb8ccff) else Color(0xff2864d7),
    )
}

@Composable
private fun SettingsSection(label: String, color: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = color, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        content()
    }
}

@Composable
private fun SettingsCard(color: Color, dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .globalCardSurface(dark, color, RoundedCornerShape(16.dp), 2.dp)
            .padding(horizontal = 15.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun SettingsRow(title: String, color: Color, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SettingsActionRow(title: String, action: String, color: Color, buttonColor: Color, onClick: () -> Unit) {
    SettingsRow(title, color) { SettingsButton(action, buttonColor, color, onClick) }
}

@Composable
private fun SettingsDropdownButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit, onMeasured: (Int) -> Unit) {
    SettingsButton(text, color, contentColor, onClick, Modifier.onGloballyPositioned { onMeasured(it.size.width) })
}

@Composable
private fun SettingsButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = modifier.globalButtonChrome(RoundedCornerShape(14.dp), 1.dp).height(40.dp),
    ) { Text(text, maxLines = 1, style = LocalTextStyle.current.copy(background = Color.Transparent)) }
}

@Composable
private fun SettingsSmallButton(text: String, color: Color, buttonColor: Color, onClick: () -> Unit) {
    SettingsButton(text, buttonColor, color, onClick)
}

@Composable
private fun SettingsDropdown(
    dark: Boolean,
    expanded: Boolean,
    menuWidth: androidx.compose.ui.unit.Dp,
    anchorWidth: androidx.compose.ui.unit.Dp?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnchoredDropdownMenu(
        dark = dark,
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        menuWidth = menuWidth.coerceAtLeast(132.dp),
        anchorWidth = anchorWidth,
        alignEndWithAnchor = true,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, color: Color, accent: Color, onChange: (Float) -> Unit) {
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
                Box(Modifier.fillMaxWidth().height(4.dp).background(sliderAccent.copy(alpha = .18f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(sliderAccent, RoundedCornerShape(2.dp)))
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
            val valueStyle = androidx.compose.ui.text.TextStyle(
                color = sliderAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
            Text(
                valueParts.firstOrNull().orEmpty(),
                style = valueStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.width(38.dp),
            )
            Text(
                valueParts.getOrNull(1).orEmpty(),
                style = valueStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.width(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsDivider(dark: Boolean) {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(if (dark) Color(0xff3b4658).copy(alpha = .38f) else Color(0xff667085).copy(alpha = .12f)))
}

@Composable
private fun SettingsToggle(checked: Boolean, dark: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val trackColor by animateColorAsState(
        targetValue = when {
            checked && dark -> Color(0xff4f8fe8)
            checked -> Color(0xff3478d3)
            dark -> Color(0xff4a5565)
            else -> Color(0xffd5dbe4)
        },
        animationSpec = spring(stiffness = 700f),
        label = "settings-toggle-track",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f),
        label = "settings-toggle-thumb",
    )
    Box(
        modifier = modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(trackColor).clickable { onCheckedChange(!checked) }.padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = thumbOffset).size(28.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

private val ocrReplacementLabels = listOf(
    "O → 0" to SettingsStore.OCR_REPLACE_O_ZERO,
    "I → 1" to SettingsStore.OCR_REPLACE_I_ONE,
    "S → 5" to SettingsStore.OCR_REPLACE_S_FIVE,
    "B → 8" to SettingsStore.OCR_REPLACE_B_EIGHT,
)
