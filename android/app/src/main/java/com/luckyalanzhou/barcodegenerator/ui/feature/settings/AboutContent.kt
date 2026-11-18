package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.component.glass.GlassBackButton
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme

@Composable
internal fun AboutUsContent(
    appVersion: String,
    versionCode: Int,
    checkingForUpdates: Boolean,
    dark: Boolean,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    val colors = rememberSettingsColors()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondarySettingsHeader(stringResource(R.string.title_about), onBack)
        SettingsSection(stringResource(R.string.about_app_info), dark) {
            SettingsRow(stringResource(R.string.about_app_version), colors.settingsText.primary) {
                Text(stringResource(R.string.about_version_with_code, appVersion, versionCode), color = colors.settingsText.secondary, fontSize = 15.sp)
            }
            SettingsDivider(dark)
            SettingsRow(stringResource(R.string.about_font), colors.settingsText.primary) {
                Text(stringResource(R.string.about_misans_vendor), color = colors.settingsText.secondary, fontSize = 15.sp)
            }
            SettingsDivider(dark)
            SettingsRow(stringResource(R.string.about_app_updates), colors.settingsText.primary) {
                SettingsButton(
                    text = if (checkingForUpdates) stringResource(R.string.checking_for_updates) else stringResource(R.string.check_for_updates),
                    busy = checkingForUpdates,
                    color = colors.controls.button,
                    contentColor = colors.controls.accent,
                    onClick = onCheckForUpdates,
                    modifier = Modifier.widthIn(min = 112.dp, max = 220.dp).height(48.dp),
                )
            }
        }
        SettingsSection(stringResource(R.string.title_licenses), dark) {
            SettingsNavigationRow(stringResource(R.string.title_licenses), onOpenLicenses)
        }
    }
}

@Composable
internal fun LicensesContent(
    dark: Boolean,
    onBack: () -> Unit,
    onShowOpenSourceLicenses: () -> Unit,
    onOpenMiSansLicense: () -> Unit,
) {
    val colors = rememberSettingsColors()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondarySettingsHeader(stringResource(R.string.title_licenses), onBack)
        SettingsSection(stringResource(R.string.license_open_source_components), dark) {
            SettingsNavigationRow(stringResource(R.string.license_open_source_view), onShowOpenSourceLicenses)
            SettingsDivider(dark)
            Text(
                stringResource(R.string.license_open_source_description),
                color = colors.settingsText.secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
        SettingsSection(stringResource(R.string.license_third_party_resources), dark) {
            SettingsRow(stringResource(R.string.license_misans_font), colors.settingsText.primary) {
                Text(stringResource(R.string.license_misans_agreement), color = colors.settingsText.secondary, fontSize = 13.sp)
            }
            SettingsDivider(dark)
            Text(
                stringResource(R.string.license_misans_description),
                color = colors.settingsText.secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            SettingsDivider(dark)
            SettingsNavigationRow(stringResource(R.string.license_misans_view), onOpenMiSansLicense)
            SettingsDivider(dark)
            SettingsRow("Material Symbols Outlined", colors.settingsText.primary) {
                Text("Apache 2.0", color = colors.settingsText.secondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun SecondarySettingsHeader(title: String, onBack: () -> Unit) {
    val colors = LocalAppColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBackButton(
            onClick = onBack,
            contentDescription = stringResource(R.string.action_back),
            backdropCaptureKey = "$title-${colors.surfaces.background}",
        )
        Text(
            title,
            color = colors.text.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(56.dp))
    }
}
