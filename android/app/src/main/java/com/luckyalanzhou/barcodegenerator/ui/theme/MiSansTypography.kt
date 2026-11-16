package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.luckyalanzhou.barcodegenerator.R

@OptIn(ExperimentalTextApi::class)
private fun miSansFont(weight: Int) = Font(
    resId = R.font.misans_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
internal val MiSansFontFamily = FontFamily(
    miSansFont(150),
    miSansFont(200),
    miSansFont(300),
    miSansFont(400),
    miSansFont(500),
    miSansFont(600),
    miSansFont(700),
)

private fun TextStyle.withMiSans() = copy(fontFamily = MiSansFontFamily)

private val DefaultAppTypography = Typography()

/** Preserve Material's existing size, line-height and weight roles while applying MiSans globally. */
internal val AppTypography = Typography(
    displayLarge = DefaultAppTypography.displayLarge.withMiSans(),
    displayMedium = DefaultAppTypography.displayMedium.withMiSans(),
    displaySmall = DefaultAppTypography.displaySmall.withMiSans(),
    headlineLarge = DefaultAppTypography.headlineLarge.withMiSans(),
    headlineMedium = DefaultAppTypography.headlineMedium.withMiSans(),
    headlineSmall = DefaultAppTypography.headlineSmall.withMiSans(),
    titleLarge = DefaultAppTypography.titleLarge.withMiSans(),
    titleMedium = DefaultAppTypography.titleMedium.withMiSans(),
    titleSmall = DefaultAppTypography.titleSmall.withMiSans(),
    bodyLarge = DefaultAppTypography.bodyLarge.withMiSans(),
    bodyMedium = DefaultAppTypography.bodyMedium.withMiSans(),
    bodySmall = DefaultAppTypography.bodySmall.withMiSans(),
    labelLarge = DefaultAppTypography.labelLarge.withMiSans(),
    labelMedium = DefaultAppTypography.labelMedium.withMiSans(),
    labelSmall = DefaultAppTypography.labelSmall.withMiSans(),
)
