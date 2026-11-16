package com.luckyalanzhou.barcodegenerator.di

import android.content.Context
import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebLocaleProvider
import com.luckyalanzhou.barcodegenerator.ui.app.platform.AppLanguageController

/** Bridges the app's selected/system locale to the platform-free LAN-share web module. */
internal class AppLanShareWebLocaleProvider(
    private val context: Context,
) : LanShareWebLocaleProvider {
    override fun currentSupportedLocaleTag(): String =
        AppLanguageController.currentSupportedLocaleTag(context)
}
