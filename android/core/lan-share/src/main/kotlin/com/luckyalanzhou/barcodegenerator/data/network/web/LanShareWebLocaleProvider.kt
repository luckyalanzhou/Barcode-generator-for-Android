package com.luckyalanzhou.barcodegenerator.data.network.web

/** Supplies the current app language as zh-Hans, zh-Hant, en, ja, ko, or es. */
fun interface LanShareWebLocaleProvider {
    fun currentSupportedLocaleTag(): String
}

internal val defaultLanShareWebLocaleProvider = LanShareWebLocaleProvider {
    LanShareWebLocale.SIMPLIFIED_CHINESE.tag
}
