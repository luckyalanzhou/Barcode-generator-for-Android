package com.luckyalanzhou.barcodegenerator.ui.app.platform

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

internal enum class AppLanguage(val tag: String) {
    FollowSystem(""),
    SimplifiedChinese("zh-Hans"),
    TraditionalChinese("zh-Hant"),
    English("en"),
    Japanese("ja"),
    Korean("ko"),
    Spanish("es");

    companion object {
        fun fromAppLocale(locale: Locale?): AppLanguage = forLocale(locale) ?: FollowSystem

        fun forLocale(locale: Locale?): AppLanguage? = when (locale?.language) {
            "zh" -> when (locale.script) {
                "Hant" -> TraditionalChinese
                "Hans" -> SimplifiedChinese
                else -> if (locale.country in setOf("TW", "HK", "MO")) {
                    TraditionalChinese
                } else {
                    SimplifiedChinese
                }
            }
            "en" -> English
            "ja" -> Japanese
            "ko" -> Korean
            "es" -> Spanish
            else -> null
        }
    }
}

internal fun supportedLocaleTag(appLocale: Locale?, systemLocale: Locale?): String =
    (AppLanguage.forLocale(appLocale ?: systemLocale) ?: AppLanguage.SimplifiedChinese).tag

internal object AppLanguageController {
    fun currentSelection(context: Context): AppLanguage = AppLanguage.fromAppLocale(currentAppLocale(context))

    fun setSelection(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    fun currentSupportedLocaleTag(context: Context): String =
        supportedLocaleTag(currentAppLocale(context), LocaleManagerCompat.getSystemLocales(context)[0])

    private fun currentAppLocale(context: Context): Locale? =
        if (Build.VERSION.SDK_INT >= 33) LocaleManagerCompat.getApplicationLocales(context)[0]
        else AppCompatDelegate.getApplicationLocales()[0]
}
