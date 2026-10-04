package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsUiState
import com.luckyalanzhou.barcodegenerator.BuildConfig

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.icons.CheckBoxIcon
import com.luckyalanzhou.barcodegenerator.icons.CheckBoxOutlineBlankIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowRightIcon

@Composable
internal fun SettingsContent(
    settings: SettingsUiState,
    dark: Boolean,
    onPersist: (SettingsUiState) -> Unit,
    onOcrMaskChange: (Int) -> Unit,
    onEnterLanShare: () -> Unit,
    onShareDebugLog: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val colors = rememberSettingsColors()
    var schemeMenu by remember { mutableStateOf(false) }
    var ocrMenu by remember { mutableStateOf(false) }
    var displayEffectsOpen by rememberSaveable { mutableStateOf(false) }
    val effectsSettings = displayEffectsSettingsState(settings.style, LocalVisualEffectsPolicy.current)
    var schemeButtonWidth by remember { mutableIntStateOf(0) }
    var ocrButtonWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val schemeWidth = schemeButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val ocrWidth = ocrButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val dimensions = LocalAppDimensions.current
    val listState = rememberLazyListState()
    val contentExceedsViewport by remember(listState) {
        derivedStateOf {
            // The shell bounds this viewport above the Tab bar. Check both directions
            // so a scrolled-to-bottom list can still return to the top.
            listState.canScrollForward || listState.canScrollBackward
        }
    }

    fun persist(next: SettingsUiState = settings) = onPersist(next)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        userScrollEnabled = contentExceedsViewport,
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(dimensions.settingsCardSpacing),
    ) {
        item("settings-appearance") {
            SettingsCard(colors.surfaces.card, dark) {
                    SettingsRow("外观", colors.settingsText.primary) {
                        Box {
                            SettingsDropdownButton(
                                text = when (settings.scheme) {
                                    "dark" -> "深色"
                                    "light" -> "浅色"
                                    else -> "跟随系统"
                                },
                                color = colors.controls.button,
                                contentColor = colors.settingsText.primary,
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
                                        text = { Text(label, color = colors.settingsText.primary) },
                                        onClick = { schemeMenu = false; persist(settings.copy(scheme = value)) },
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider(dark)
                    SettingsRow("显示与动效", colors.settingsText.primary,
                        Modifier.clickable(role = Role.Button) { displayEffectsOpen = true }) {
                        Text(effectsSettings.summary, color = colors.settingsText.secondary, fontSize = 14.sp)
                        Icon(KeyboardArrowRightIcon, contentDescription = null,
                            tint = colors.settingsText.secondary, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                    }
            }
        }

        item("settings-barcode") {
            SettingsCard(colors.surfaces.card, dark) {
                    SettingsSliderRow("文字大小", settings.textSize, 10f..24f, "${settings.textSize.toInt()} sp", colors.settingsText.primary) {
                        persist(settings.copy(textSize = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码高度", settings.barHeight, 30f..80f, "${settings.barHeight.toInt()} dp", colors.settingsText.primary) {
                        persist(settings.copy(barHeight = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码宽度", settings.barWidth, 120f..300f, "${settings.barWidth.toInt()} dp", colors.settingsText.primary) {
                        persist(settings.copy(barWidth = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow("条码间距", settings.margin, 0f..10f, "${settings.margin.toInt()} dp", colors.settingsText.primary) {
                        persist(settings.copy(margin = it))
                    }
                    SettingsDivider(dark)
                    SettingsRow("显示条码格式", colors.settingsText.primary) {
                        SettingsToggle(
                            checked = settings.showFormat,
                            dark = dark,
                            modifier = Modifier.padding(end = 8.dp),
                            onCheckedChange = { next ->
                                persist(settings.copy(showFormat = next))
                            },
                        )
                    }
                    SettingsDivider(dark)
                    SettingsRow("OCR 字符纠错", colors.settingsText.primary) {
                        val selected = ocrReplacementLabels.filter { (_, bit) -> settings.ocrMask and bit != 0 }.map { it.first }
                        Box {
                            SettingsDropdownButton(
                                text = when (selected.size) { 0 -> "关闭"; 1 -> selected.first(); else -> "启用 ${selected.size} 项" },
                                color = colors.controls.button,
                                contentColor = colors.settingsText.primary,
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
                                        text = { Text(label, color = colors.settingsText.primary, maxLines = 1, softWrap = false) },
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
                                            onOcrMaskChange(mask)
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
                    SettingsRow("局域网文件分享", colors.settingsText.primary) {
                        SettingsButton(
                            text = "启动",
                            color = lerp(colors.surfaces.card, colors.controls.accent, if (dark) .24f else .10f),
                            contentColor = if (dark) colors.settingsText.primary else colors.controls.accent,
                            onClick = onEnterLanShare,
                        )
                    }
                    SettingsDivider(dark)
                    SettingsActionRow("恢复默认设置", "恢复", colors.settingsText.primary, colors.controls.button) {
                        val defaults = settings.copy(
                            textSize = 14f,
                            barHeight = 55f,
                            barWidth = 220f,
                            margin = 4f,
                            showFormat = false,
                        )
                        persist(defaults)
                        onNotice("已恢复条码默认设置")
                    }
                    if (BuildConfig.DEBUG_LOG_EXPORT) {
                        SettingsDivider(dark)
                        SettingsActionRow("导出调试日志", "分享", colors.settingsText.primary, colors.controls.button, onShareDebugLog)
                    }
            }
        }

        item("settings-about") {
            SettingsCard(colors.surfaces.card, dark) {
                Text("关于", color = colors.settingsText.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 1.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("作者：Alan", color = colors.settingsText.secondary, fontSize = 13.sp)
                        Text("版本：${BuildConfig.VERSION_NAME}", color = colors.settingsText.secondary, fontSize = 13.sp)
                    }
                    SettingsButton(
                        text = "检查更新",
                        color = colors.controls.button,
                        contentColor = colors.settingsText.primary,
                        onClick = onCheckForUpdates,
                        modifier = Modifier.width(132.dp),
                    )
                }
            }
        }
    }
    if (displayEffectsOpen) {
        DisplayEffectsSettingsDialog(
            style = settings.style,
            dark = dark,
            onStyleChange = { persist(settings.copy(style = it)) },
            onDismiss = { displayEffectsOpen = false },
        )
    }
}
