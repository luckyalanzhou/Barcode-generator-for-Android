package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.app.platform.AppLanguage
import com.luckyalanzhou.barcodegenerator.ui.app.platform.AppLanguageController
import com.luckyalanzhou.barcodegenerator.ui.component.SingleChoiceMenuItem
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.SettingsDivider
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.SettingsDropdown
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.SettingsDropdownButton
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.SettingsIconKind
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.SettingsLeadingIcon
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.SettingsRow
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme

@Composable
internal fun AppLanguageSettingsRow(dark: Boolean) {
    val context = LocalContext.current
    var selectionVersion by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { selectionVersion++ }
    // Reading the current configuration also observes changes from Android's app language screen.
    val selected = remember(context, LocalConfiguration.current, selectionVersion) {
        AppLanguageController.currentSelection(context)
    }
    val label = stringResource(R.string.settings_row_language)
    val colors = LocalAppColorScheme.current.settingsText
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val anchorWidth = anchorSize.width.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
    val anchorHeight = anchorSize.height.takeIf { it > 0 }?.let { with(density) { it.toDp() } }

    SettingsDivider(dark)
    SettingsRow(label, colors.primary, leading = { SettingsLeadingIcon(SettingsIconKind.Language) }) {
        Box {
            SettingsDropdownButton(
                text = stringResource(selected.labelResource),
                contentColor = colors.secondary,
                onClick = { expanded = true },
                onMeasured = { anchorSize = it },
                pickerLabel = label,
                expanded = expanded,
            )
            SettingsDropdown(dark, expanded, 144.dp, anchorWidth, anchorHeight, onDismiss = { expanded = false }) {
                AppLanguage.entries.forEachIndexed { index, language ->
                    if (index > 0) ComposeDropdownDivider(dark)
                    SingleChoiceMenuItem(
                        label = stringResource(language.labelResource),
                        isSelected = selected == language,
                        highlightSelection = false,
                    ) {
                        expanded = false
                        if (selected != language) {
                            AppLanguageController.setSelection(language)
                            selectionVersion++
                        }
                    }
                }
            }
        }
    }
}

private val AppLanguage.labelResource: Int
    get() = when (this) {
        AppLanguage.FollowSystem -> R.string.settings_option_automatic
        AppLanguage.SimplifiedChinese -> R.string.language_option_simplified_chinese
        AppLanguage.TraditionalChinese -> R.string.language_option_traditional_chinese
        AppLanguage.English -> R.string.language_option_english
        AppLanguage.Japanese -> R.string.language_option_japanese
        AppLanguage.Korean -> R.string.language_option_korean
        AppLanguage.Spanish -> R.string.language_option_spanish
    }
