package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `settings choices use platform locale tags and follow system clears the override`() {
        assertEquals(
            listOf("", "zh-Hans", "zh-Hant", "en", "ja", "ko", "es"),
            AppLanguage.entries.map { it.tag },
        )
        assertEquals(AppLanguage.FollowSystem, AppLanguage.fromAppLocale(null))
        assertEquals(AppLanguage.English, AppLanguage.fromAppLocale(Locale.forLanguageTag("en-GB")))
    }

    @Test
    fun `app locale wins and the system locale falls back to simplified Chinese`() {
        assertEquals(
            "ja",
            supportedLocaleTag(Locale.JAPAN, Locale.KOREA),
        )
        assertEquals("es", supportedLocaleTag(null, Locale.forLanguageTag("es-MX")))
        assertEquals("zh-Hans", supportedLocaleTag(null, Locale.FRANCE))
    }

    @Test
    fun `Chinese script takes precedence over region and traditional regions are supported`() {
        assertEquals("zh-Hant", supportedLocaleTag(null, Locale.forLanguageTag("zh-Hant-CN")))
        assertEquals("zh-Hant", supportedLocaleTag(null, Locale.forLanguageTag("zh-HK")))
        assertEquals("zh-Hant", supportedLocaleTag(null, Locale.TAIWAN))
        assertEquals("zh-Hans", supportedLocaleTag(null, Locale.forLanguageTag("zh-Hans-TW")))
        assertEquals("zh-Hans", supportedLocaleTag(null, Locale.CHINESE))
    }
}
