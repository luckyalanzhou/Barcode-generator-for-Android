package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {
    @Test
    fun knownPagesExposeSharedMetadata() {
        assertEquals(AppRoute.History, AppRoute.fromPage("history"))
        assertEquals(R.string.title_history, AppRoute.History.titleResId)
        assertEquals(1, AppRoute.History.mainTabIndex)
        assertTrue(AppRoute.History.chromeVisible)

        assertEquals(AppRoute.Settings, AppRoute.fromPage("settings"))
        assertEquals(R.string.title_settings, AppRoute.Settings.titleResId)
    }

    @Test
    fun unknownPagesSafelyReturnToGenerate() {
        assertEquals(AppRoute.Generate, AppRoute.fromPage("unknown"))
        assertEquals(0, AppRoute.Generate.mainTabIndex)
    }
}
