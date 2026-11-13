package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider
import com.luckyalanzhou.barcodegenerator.ui.component.SingleChoiceMenuItem
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp

/**
 * 设置页：分组呈现外观、条码显示、OCR、局域网分享、调试日志和更新检查。
 * 设置控件通过回调保存对应状态；启动分享、导出日志和检查更新是独立动作，不耦合到样式更新。
 */
@Composable
internal fun SettingsContent(
    checkingForUpdates: Boolean,
    settings: SettingsContentState,
    dark: Boolean,
    onStyleChange: (StyleSettings) -> Unit,
    onOcrMaskChange: (Int) -> Unit,
    onEnterLanShare: () -> Unit,
    onShareDebugLog: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onNotice: (String) -> Unit,
    listBottomPadding: Dp = 8.dp,
) {
    val colors = rememberSettingsColors()
    var schemeMenu by remember { mutableStateOf(false) }
    var ocrMenu by remember { mutableStateOf(false) }
    var schemeButtonWidth by remember { mutableIntStateOf(0) }
    var ocrButtonWidth by remember { mutableIntStateOf(0) }
    var schemeButtonHeight by remember { mutableIntStateOf(0) }
    var ocrButtonHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val schemeWidth = schemeButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val ocrWidth = ocrButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val schemeHeight = schemeButtonHeight.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val ocrHeight = ocrButtonHeight.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val dimensions = LocalAppDimensions.current
    val scrollState = rememberScrollState()
    fun persist(next: StyleSettings = settings.style) = onStyleChange(next)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = listBottomPadding),
        verticalArrangement = Arrangement.spacedBy(dimensions.settingsCardSpacing),
    ) {
        SettingsSection("外观", dark) {
            SettingsRow("主题", colors.settingsText.primary) {
                Box {
                    SettingsDropdownButton(
                        text = when (settings.style.colorScheme) {
                            "dark" -> "深色"
                            "light" -> "浅色"
                            else -> "跟随系统"
                        },
                        contentColor = colors.settingsText.secondary,
                        onClick = { schemeMenu = true },
                        onMeasured = { schemeButtonWidth = it.width; schemeButtonHeight = it.height },
                        pickerLabel = "外观",
                        expanded = schemeMenu,
                    )
                    SettingsDropdown(dark, schemeMenu, 160.dp, schemeWidth, schemeHeight,
                        onDismiss = { schemeMenu = false }) {
                        listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark").forEachIndexed { index, (label, value) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            val selectedScheme = settings.style.colorScheme.takeIf { it == "light" || it == "dark" } ?: "system"
                            SingleChoiceMenuItem(label, value == selectedScheme, highlightSelection = false) {
                                schemeMenu = false
                                persist(settings.style.copy(colorScheme = value))
                            }
                        }
                    }
                }
            }
        }
        SettingsSection("条码样式", dark) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = useStackedSlider(maxWidth.value, LocalDensity.current.fontScale)
                Column {
                    SettingsSliderRow("条码高度", settings.style.barHeight.toFloat(), 30f..80f, "${settings.style.barHeight} dp", colors.settingsText.primary, stacked) {
                        persist(settings.style.copy(barHeight = it.toInt()))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码宽度", settings.style.barWidth, 120f..300f, "${settings.style.barWidth.toInt()} dp", colors.settingsText.primary, stacked) {
                        persist(settings.style.copy(barWidth = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码间距", settings.style.margin.toFloat(), 0f..10f, "${settings.style.margin} dp", colors.settingsText.primary, stacked) {
                        persist(settings.style.copy(margin = it.toInt()))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码文字大小", settings.style.textSize, 10f..24f, "${settings.style.textSize.toInt()} sp", colors.settingsText.primary, stacked) {
                        persist(settings.style.copy(textSize = it))
                    }
                    SettingsDivider(dark)
                    SettingsActionRow("恢复条码默认样式", "恢复", colors.settingsText.primary, colors.controls.button) {
                        persist(settings.style.copy(textSize = 14f, barHeight = 55, barWidth = 220f, margin = 4))
                        onNotice("已恢复条码默认样式")
                    }
                }
            }
        }
        SettingsSection("识别辅助", dark) {
            SettingsRow("OCR 字符纠错", colors.settingsText.primary) {
                val selected = ocrReplacementLabels.filter { (_, bit) -> settings.ocrMask and bit != 0 }.map { it.first }
                Box {
                    SettingsDropdownButton(
                        text = when (selected.size) { 0 -> "关闭"; 1 -> selected.first(); else -> "启用 ${selected.size} 项" },
                        contentColor = colors.settingsText.secondary,
                        onClick = { ocrMenu = true },
                        onMeasured = { ocrButtonWidth = it.width; ocrButtonHeight = it.height },
                        pickerLabel = "OCR 字符纠错",
                        expanded = ocrMenu,
                    )
                    SettingsDropdown(dark, ocrMenu, 140.dp, ocrWidth, ocrHeight,
                        onDismiss = { ocrMenu = false }) {
                        ocrReplacementLabels.forEachIndexed { index, (label, bit) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            val checked = settings.ocrMask and bit != 0
                            com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuChoiceItem(
                                label = label, checked = checked, multiple = true,
                                onClick = {
                                    val mask = if (settings.ocrMask and bit == 0) settings.ocrMask or bit else settings.ocrMask and bit.inv()
                                    onOcrMaskChange(mask)
                                },
                            )
                        }
                    }
                }
            }
        }
        SettingsSection("文件传输", dark) {
            SettingsNavigationRow("局域网文件分享", onEnterLanShare)
        }
        if (BuildConfig.DEBUG_LOG_EXPORT) {
            SettingsSection("诊断", dark) {
                SettingsActionRow("导出调试日志", "分享", colors.settingsText.primary, colors.controls.button, onShareDebugLog)
            }
        }
        SettingsSection("关于", dark) {
            SettingsRow("版本", colors.settingsText.primary) {
                Text(BuildConfig.VERSION_NAME, color = colors.settingsText.secondary, fontSize = 15.sp)
            }
            SettingsDivider(dark)
            SettingsRow("作者", colors.settingsText.primary) {
                Text("Alan", color = colors.settingsText.secondary, fontSize = 15.sp)
            }
            SettingsDivider(dark)
            SettingsRow("应用更新", colors.settingsText.primary) {
                SettingsButton(
                    text = if (checkingForUpdates) "检查中…" else "检查更新",
                    busy = checkingForUpdates,
                    color = colors.controls.button,
                    contentColor = colors.controls.accent,
                    onClick = onCheckForUpdates,
                    modifier = Modifier.widthIn(min = 112.dp),
                )
            }
        }
    }
}
