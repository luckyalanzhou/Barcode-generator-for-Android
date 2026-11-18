package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.component.SingleChoiceMenuItem
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider

@Composable
internal fun AppearanceThemeContent(
    settings: SettingsContentState,
    dark: Boolean,
    onBack: () -> Unit,
    onStyleChange: (StyleSettings) -> Unit,
    onLiquidGlassEnabledChange: (Boolean) -> Unit,
    onLiquidGlassStrengthChange: (Int) -> Unit,
    onBottomTabLabelsVisibleChange: (Boolean) -> Unit,
) {
    val colors = rememberSettingsColors()
    var schemeMenu by remember { mutableStateOf(false) }
    var schemeButtonWidth by remember { mutableIntStateOf(0) }
    var schemeButtonHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val schemeWidth = schemeButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val schemeHeight = schemeButtonHeight.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val schemeTitle = stringResource(R.string.settings_row_application_theme)
    val glassTitle = stringResource(R.string.settings_liquid_glass)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondarySettingsHeader(stringResource(R.string.settings_appearance_theme), onBack)
        SettingsCard(colors.surfaces.card, dark) {
            SettingsRow(schemeTitle, colors.settingsText.primary) {
                androidx.compose.foundation.layout.Box {
                    SettingsDropdownButton(
                        text = when (settings.style.colorScheme) {
                            "dark" -> stringResource(R.string.theme_dark)
                            "light" -> stringResource(R.string.theme_light)
                            else -> stringResource(R.string.settings_option_automatic)
                        },
                        contentColor = colors.settingsText.secondary,
                        onClick = { schemeMenu = true },
                        onMeasured = { schemeButtonWidth = it.width; schemeButtonHeight = it.height },
                        pickerLabel = schemeTitle,
                        expanded = schemeMenu,
                    )
                    SettingsDropdown(dark, schemeMenu, 160.dp, schemeWidth, schemeHeight,
                        onDismiss = { schemeMenu = false }) {
                        listOf(
                            stringResource(R.string.settings_option_automatic) to "system",
                            stringResource(R.string.theme_light) to "light",
                            stringResource(R.string.theme_dark) to "dark",
                        ).forEachIndexed { index, (label, value) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            val selectedScheme = settings.style.colorScheme.takeIf { it == "light" || it == "dark" } ?: "system"
                            SingleChoiceMenuItem(label, value == selectedScheme, highlightSelection = false) {
                                schemeMenu = false
                                onStyleChange(settings.style.copy(colorScheme = value))
                            }
                        }
                    }
                }
            }
            SettingsDivider(dark)
            SettingsRow(glassTitle, colors.settingsText.primary) {
                Switch(
                    checked = settings.liquidGlassEnabled,
                    onCheckedChange = onLiquidGlassEnabledChange,
                    // Preserve the visible trailing inset used by the existing theme picker row.
                    modifier = Modifier.padding(end = 12.dp)
                        .semantics { contentDescription = glassTitle },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = colors.controls.toggleOn,
                        uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                        uncheckedTrackColor = colors.controls.toggleOff,
                    ),
                )
            }
            SettingsDivider(dark)
            LiquidGlassStrengthSliderRow(
                title = stringResource(R.string.settings_liquid_glass_strength),
                value = settings.liquidGlassStrengthPercent,
                enabled = settings.liquidGlassEnabled,
                onValueChange = onLiquidGlassStrengthChange,
            )
        }
        SettingsSection(stringResource(R.string.settings_navigation_bar), dark) {
            val labelsTitle = stringResource(R.string.settings_navigation_bar_labels)
            SettingsRow(labelsTitle, colors.settingsText.primary) {
                Switch(
                    checked = settings.bottomTabLabelsVisible,
                    onCheckedChange = onBottomTabLabelsVisibleChange,
                    modifier = Modifier.padding(end = 12.dp)
                        .semantics { contentDescription = labelsTitle },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = colors.controls.toggleOn,
                        uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                        uncheckedTrackColor = colors.controls.toggleOff,
                    ),
                )
            }
        }
    }
}
