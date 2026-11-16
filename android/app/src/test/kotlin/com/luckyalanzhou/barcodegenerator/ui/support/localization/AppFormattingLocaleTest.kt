package com.luckyalanzhou.barcodegenerator.ui.support.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFormattingLocaleTest {
    @Test
    fun genericAppLanguageUsesMatchingSystemRegion() {
        val locale = resolveApplicationFormattingLocale(
            appLocale = Locale.forLanguageTag("en"),
            systemLocales = listOf(Locale.forLanguageTag("zh-CN"), Locale.forLanguageTag("en-GB")),
        )

        assertEquals(Locale.forLanguageTag("en-GB"), locale)
    }

    @Test
    fun appLanguageDoesNotBorrowRegionFromAnotherLanguage() {
        val locale = resolveApplicationFormattingLocale(
            appLocale = Locale.forLanguageTag("es"),
            systemLocales = listOf(Locale.forLanguageTag("zh-CN")),
        )

        assertEquals(Locale.forLanguageTag("es"), locale)
    }

    @Test
    fun explicitAppRegionTakesPrecedence() {
        val locale = resolveApplicationFormattingLocale(
            appLocale = Locale.forLanguageTag("en-AU"),
            systemLocales = listOf(Locale.forLanguageTag("en-GB")),
        )

        assertEquals(Locale.forLanguageTag("en-AU"), locale)
    }

    @Test
    fun followSystemUsesItsPrimaryLocale() {
        val locale = resolveApplicationFormattingLocale(
            appLocale = null,
            systemLocales = listOf(Locale.forLanguageTag("es-MX"), Locale.forLanguageTag("en-US")),
        )

        assertEquals(Locale.forLanguageTag("es-MX"), locale)
    }

    @Test
    fun simplifiedChineseKeepsItsScriptWhenUsingSystemRegion() {
        val locale = resolveApplicationFormattingLocale(
            appLocale = Locale.forLanguageTag("zh-Hans"),
            systemLocales = listOf(Locale.forLanguageTag("zh-CN")),
        )

        assertEquals(Locale.forLanguageTag("zh-Hans-CN"), locale)
    }
}
