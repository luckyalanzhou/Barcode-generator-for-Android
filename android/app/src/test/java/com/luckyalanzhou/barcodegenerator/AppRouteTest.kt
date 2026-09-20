package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.ui.AppRoute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {
    @Test
    fun knownPagesExposeSharedMetadata() {
        assertEquals(AppRoute.History, AppRoute.fromPage("history"))
        assertEquals("历史记录", AppRoute.History.title)
        assertEquals(1, AppRoute.History.mainTabIndex)
        assertTrue(AppRoute.History.chromeVisible)

        assertEquals(AppRoute.Settings, AppRoute.fromPage("settings"))
        assertEquals("设置", AppRoute.Settings.title)
    }

    @Test
    fun unknownPagesSafelyReturnToGenerate() {
        assertEquals(AppRoute.Generate, AppRoute.fromPage("unknown"))
        assertEquals(0, AppRoute.Generate.mainTabIndex)
    }
}
