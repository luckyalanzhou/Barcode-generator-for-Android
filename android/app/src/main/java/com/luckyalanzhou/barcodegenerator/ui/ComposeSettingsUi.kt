package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.SettingsUiState
import com.luckyalanzhou.barcodegenerator.BuildConfig

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.luckyalanzhou.barcodegenerator.icons.CheckBoxIcon
import com.luckyalanzhou.barcodegenerator.icons.CheckBoxOutlineBlankIcon

@Composable
internal fun ComposeSettingsPage(
    settingsViewModel: SettingsViewModel,
    dark: Boolean,
    onApplyAppearance: () -> Unit,
    onEnterLanShare: () -> Unit,
    onRestoreFavorites: () -> Unit,
    onExportFavorites: () -> Unit,
    onShareDebugLog: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val colors = rememberSettingsColors()
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
            SettingsCard(colors.surfaces.card, dark) {
                    SettingsRow("外观", colors.text.primary) {
                        Box {
                            SettingsDropdownButton(
                                text = when (settings.scheme) {
                                    "dark" -> "深色"
                                    "light" -> "浅色"
                                    else -> "跟随系统"
                                },
                                color = colors.controls.button,
                                contentColor = colors.text.primary,
                                onClick = { schemeMenu = true },
                                onMeasured = { schemeButtonWidth = it },
                            )
                            SettingsDropdown(
                                dark = dark,
                                expanded = schemeMenu,
                                menuWidth = 110.dp,
                                anchorWidth = schemeWidth,
                                onDismiss = { schemeMenu = false },
                            ) {
                                listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark").forEachIndexed { index, (label, value) ->
                                    if (index > 0) ComposeDropdownDivider(dark)
                                    androidx.compose.material3.DropdownMenuItem(
                                        modifier = Modifier.height(40.dp),
                                        text = { Text(label, color = colors.text.primary) },
                                        onClick = { schemeMenu = false; persist(settings.copy(scheme = value)) },
                                    )
                                }
                            }
                        }
                    }
            }
        }

        item("settings-barcode") {
            SettingsCard(colors.surfaces.card, dark) {
                    SettingsSliderRow("文字大小", settings.textSize, 10f..24f, "${settings.textSize.toInt()} sp", colors.text.primary, colors.controls.accent) {
                        settingsViewModel.setTextSize(it); persist(settings.copy(textSize = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码高度", settings.barHeight, 30f..150f, "${settings.barHeight.toInt()} dp", colors.text.primary, colors.controls.accent) {
                        settingsViewModel.setBarHeight(it); persist(settings.copy(barHeight = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码宽度", settings.barWidth, 120f..360f, "${settings.barWidth.toInt()} dp", colors.text.primary, colors.controls.accent) {
                        settingsViewModel.setBarWidth(it); persist(settings.copy(barWidth = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码间距", settings.margin, 0f..40f, "${settings.margin.toInt()} dp", colors.text.primary, colors.controls.accent) {
                        settingsViewModel.setMargin(it); persist(settings.copy(margin = it))
                    }
                    SettingsDivider(dark)
                    SettingsRow("显示条码格式", colors.text.primary) {
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
                    SettingsRow("OCR 字符纠错", colors.text.primary) {
                        val selected = ocrReplacementLabels.filter { (_, bit) -> settings.ocrMask and bit != 0 }.map { it.first }
                        Box {
                            SettingsDropdownButton(
                                text = when (selected.size) { 0 -> "关闭"; 1 -> selected.first(); else -> "启用 ${selected.size} 项" },
                                color = colors.controls.button,
                                contentColor = colors.text.primary,
                                onClick = { ocrMenu = true },
                                onMeasured = { ocrButtonWidth = it },
                            )
                            SettingsDropdown(
                                dark = dark,
                                expanded = ocrMenu,
                                menuWidth = 140.dp,
                                anchorWidth = ocrWidth,
                                onDismiss = { ocrMenu = false },
                            ) {
                                ocrReplacementLabels.forEachIndexed { index, (label, bit) ->
                                    if (index > 0) ComposeDropdownDivider(dark)
                                    androidx.compose.material3.DropdownMenuItem(
                                        modifier = Modifier.height(40.dp),
                                        contentPadding = PaddingValues(start = 12.dp, end = 0.dp),
                                        text = { Text(label, color = colors.text.primary, maxLines = 1, softWrap = false) },
                                        trailingIcon = {
                                            val checked = settings.ocrMask and bit != 0
                                            Icon(
                                                imageVector = if (checked) CheckBoxIcon else CheckBoxOutlineBlankIcon,
                                                contentDescription = if (checked) "已选中" else "未选中",
                                                tint = if (checked) colors.controls.accent else colors.text.secondary,
                                                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                                            )
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

        item("settings-tools") {
            SettingsCard(colors.surfaces.card, dark) {
                    SettingsActionRow("局域网文件分享", "启动", colors.text.primary, colors.controls.button, onEnterLanShare)
                    SettingsDivider(dark)
                    SettingsActionRow("恢复默认设置", "恢复", colors.text.primary, colors.controls.button) {
                        val defaults = settings.copy(textSize = 14f, barHeight = 55f, barWidth = 220f, margin = 4f)
                        settingsViewModel.setTextSize(defaults.textSize)
                        settingsViewModel.setBarHeight(defaults.barHeight)
                        settingsViewModel.setBarWidth(defaults.barWidth)
                        settingsViewModel.setMargin(defaults.margin)
                        persist(defaults)
                        onNotice("已恢复条码默认设置")
                    }
                    SettingsDivider(dark)
                    SettingsRow("收藏备份", colors.text.primary) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsSmallButton("导入", colors.text.primary, colors.controls.button, onRestoreFavorites)
                            SettingsSmallButton("导出", colors.text.primary, colors.controls.button, onExportFavorites)
                        }
                    }
                    SettingsDivider(dark)
                    if (BuildConfig.DEBUG_LOG_EXPORT) {
                        SettingsDivider(dark)
                        SettingsActionRow("导出调试日志", "分享", colors.text.primary, colors.controls.button, onShareDebugLog)
                    }
            }
        }

        item("settings-about") {
            SettingsCard(colors.surfaces.card, dark) {
                Text("关于", color = colors.text.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 5.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("作者：Alan", color = colors.text.primary, fontSize = 13.sp)
                        Text("版本：${BuildConfig.VERSION_NAME}", color = colors.text.primary, fontSize = 13.sp)
                    }
                    SettingsButton(
                        text = "检查更新",
                        color = colors.controls.button,
                        contentColor = colors.text.primary,
                        onClick = onCheckForUpdates,
                        modifier = Modifier.width(132.dp),
                    )
                }
            }
        }
    }
}
