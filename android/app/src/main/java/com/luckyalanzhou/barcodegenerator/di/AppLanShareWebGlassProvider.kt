package com.luckyalanzhou.barcodegenerator.di

import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebGlassProvider
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository

/** Reads the loaded settings cache when the LAN browser page is requested. */
internal class AppLanShareWebGlassProvider(
    private val settingsRepository: SettingsRepository,
) : LanShareWebGlassProvider {
    override fun isLiquidGlassEnabled(): Boolean = settingsRepository.getLiquidGlassEnabled()

    override fun getLiquidGlassStrengthPercent(): Int = settingsRepository.getLiquidGlassStrengthPercent()
}
