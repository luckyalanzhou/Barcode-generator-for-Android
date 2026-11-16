package com.luckyalanzhou.barcodegenerator.ui.app.platform

import android.app.Application
import android.app.LocaleManager
import android.content.res.Configuration
import android.os.LocaleList
import com.luckyalanzhou.barcodegenerator.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppLanguagePlatformTest {
    @Test
    fun `system app locale is reflected in settings and the sharing page language`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(LocaleManager::class.java)
        val previous = manager.applicationLocales
        try {
            manager.applicationLocales = LocaleList(Locale.JAPANESE)
            assertEquals(AppLanguage.Japanese, AppLanguageController.currentSelection(context))
            assertEquals("ja", AppLanguageController.currentSupportedLocaleTag(context))
            manager.applicationLocales = LocaleList.getEmptyLocaleList()
            assertEquals(AppLanguage.FollowSystem, AppLanguageController.currentSelection(context))
        } finally {
            manager.applicationLocales = previous
        }
    }

    @Test
    fun `locale resources format a message and an unsupported locale uses simplified Chinese`() {
        val context = RuntimeEnvironment.getApplication()
        fun resourcesFor(tag: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            },
        ).resources

        assertEquals("Fila 3: error", resourcesFor("es").getString(R.string.row_error, 3, "error"))
        assertEquals("第 3 行：错误", resourcesFor("fr").getString(R.string.row_error, 3, "错误"))
    }
}
