package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onEnterAbout: () -> Unit = {},
    listBottomPadding: Dp = 8.dp,
    languageRow: @Composable () -> Unit = {},
    onLiquidGlassEnabledChange: (Boolean) -> Unit = {},
) {
    val localizedResources = LocalResources.current
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
        SettingsSection(stringResource(R.string.settings_section_general), dark) {
            SettingsRow(
                stringResource(R.string.settings_row_application_theme),
                colors.settingsText.primary,
                leading = { SettingsLeadingIcon(SettingsIconKind.Theme) },
            ) {
                Box {
                    SettingsDropdownButton(
                        text = when (settings.style.colorScheme) {
                            "dark" -> stringResource(R.string.theme_dark)
                            "light" -> stringResource(R.string.theme_light)
                            else -> stringResource(R.string.settings_option_automatic)
                        },
                        contentColor = colors.settingsText.secondary,
                        onClick = { schemeMenu = true },
                        onMeasured = { schemeButtonWidth = it.width; schemeButtonHeight = it.height },
                        pickerLabel = stringResource(R.string.settings_row_application_theme),
                        expanded = schemeMenu,
                    )
                    SettingsDropdown(dark, schemeMenu, 160.dp, schemeWidth, schemeHeight,
                        onDismiss = { schemeMenu = false }) {
                        listOf(stringResource(R.string.settings_option_automatic) to "system", stringResource(R.string.theme_light) to "light", stringResource(R.string.theme_dark) to "dark").forEachIndexed { index, (label, value) ->
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
            val liquidGlassLabel = stringResource(R.string.settings_liquid_glass)
            SettingsRow(
                liquidGlassLabel,
                colors.settingsText.primary,
                leading = { SettingsLeadingIcon(SettingsIconKind.LiquidGlass) },
            ) {
                Switch(
                    checked = settings.liquidGlassEnabled,
                    onCheckedChange = onLiquidGlassEnabledChange,
                    modifier = Modifier.semantics { contentDescription = liquidGlassLabel },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.controls.toggleOn,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = colors.controls.toggleOff,
                    ),
                )
            }
            languageRow()
        }
        SettingsSection(stringResource(R.string.settings_barcode_style), dark) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val sliderTitles = listOf(
                    stringResource(R.string.settings_barcode_height),
                    stringResource(R.string.settings_barcode_width),
                    stringResource(R.string.settings_barcode_spacing),
                    stringResource(R.string.settings_barcode_text_size),
                )
                val sliderTitleWidth = rememberSettingsSliderTitleWidth(sliderTitles)
                val stacked = useStackedSlider(maxWidth.value, LocalDensity.current.fontScale, sliderTitleWidth.value)
                Column {
                    SettingsSliderRow(stringResource(R.string.settings_barcode_height), settings.style.barHeight.toFloat(), 30f..80f, stringResource(R.string.dp_value, settings.style.barHeight), colors.settingsText.primary, stacked, sliderTitleWidth, leading = { SettingsLeadingIcon(SettingsIconKind.BarcodeHeight) }) {
                        persist(settings.style.copy(barHeight = it.toInt()))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow(stringResource(R.string.settings_barcode_width), settings.style.barWidth, 120f..300f, stringResource(R.string.dp_value, settings.style.barWidth.toInt()), colors.settingsText.primary, stacked, sliderTitleWidth, leading = { SettingsLeadingIcon(SettingsIconKind.BarcodeWidth) }) {
                        persist(settings.style.copy(barWidth = it))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow(stringResource(R.string.settings_barcode_spacing), settings.style.margin.toFloat(), 0f..10f, stringResource(R.string.dp_value, settings.style.margin), colors.settingsText.primary, stacked, sliderTitleWidth, leading = { SettingsLeadingIcon(SettingsIconKind.BarcodeSpacing) }) {
                        persist(settings.style.copy(margin = it.toInt()))
                    }
                    SettingsDivider(dark)
                    SettingsSliderRow(stringResource(R.string.settings_barcode_text_size), settings.style.textSize, 10f..24f, stringResource(R.string.sp_value, settings.style.textSize.toInt()), colors.settingsText.primary, stacked, sliderTitleWidth, leading = { SettingsLeadingIcon(SettingsIconKind.TextSize) }) {
                        persist(settings.style.copy(textSize = it))
                    }
                    SettingsDivider(dark)
                    SettingsActionRow(stringResource(R.string.settings_restore_barcode_style), stringResource(R.string.action_restore), colors.settingsText.primary, colors.controls.button, leading = { SettingsLeadingIcon(SettingsIconKind.RestoreStyle) }) {
                        persist(settings.style.copy(textSize = 14f, barHeight = 55, barWidth = 220f, margin = 4))
                        onNotice(localizedResources.getString(R.string.settings_barcode_style_restored))
                    }
                }
            }
        }
        SettingsSection(stringResource(R.string.settings_recognition_assistance), dark) {
            SettingsRow(
                stringResource(R.string.settings_ocr_correction),
                colors.settingsText.primary,
                leading = { SettingsLeadingIcon(SettingsIconKind.Correction) },
            ) {
                val selected = ocrReplacementLabels.filter { (_, bit) -> settings.ocrMask and bit != 0 }.map { it.first }
                Box {
                    SettingsDropdownButton(
                        text = when (selected.size) { 0 -> stringResource(R.string.action_off); 1 -> selected.first(); else -> pluralStringResource(R.plurals.settings_ocr_enabled_count, selected.size, selected.size) },
                        contentColor = colors.settingsText.secondary,
                        onClick = { ocrMenu = true },
                        onMeasured = { ocrButtonWidth = it.width; ocrButtonHeight = it.height },
                        pickerLabel = stringResource(R.string.settings_ocr_correction),
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
        SettingsSection(stringResource(R.string.settings_file_transfer), dark) {
            SettingsNavigationRow(
                stringResource(R.string.settings_lan_file_share),
                onEnterLanShare,
                leading = { SettingsLeadingIcon(SettingsIconKind.LanShare) },
            )
        }
        if (BuildConfig.DEBUG_LOG_EXPORT) {
            SettingsSection(stringResource(R.string.settings_diagnostics), dark) {
                SettingsActionRow(
                    stringResource(R.string.settings_export_logs),
                    stringResource(R.string.action_share),
                    colors.settingsText.primary,
                    colors.controls.button,
                    leading = { SettingsLeadingIcon(SettingsIconKind.Logs) },
                    onClick = onShareDebugLog,
                )
            }
        }
        SettingsCard(colors.surfaces.card, dark) {
            SettingsNavigationRow(
                stringResource(R.string.title_about),
                onEnterAbout,
                leading = { SettingsLeadingIcon(SettingsIconKind.About) },
            )
        }
    }
}
