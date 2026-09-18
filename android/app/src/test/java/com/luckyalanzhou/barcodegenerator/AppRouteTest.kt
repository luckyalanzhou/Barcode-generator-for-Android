package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.ui.AppRoute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {
    @Test
    fun knownPagesExposeSharedMetadata() {
        assertEquals(AppRoute.History, AppRoute.fromPage("history"))
        assertEquals("历史记录", AppRoute.History.title)
        assertEquals(1, AppRoute.History.mainTabIndex)
        assertTrue(AppRoute.History.chromeVisible)

        assertEquals(AppRoute.BetaTestCenter, AppRoute.fromPage("betaTestCenter"))
        assertFalse(AppRoute.BetaTestCenter.chromeVisible)
        assertEquals("Beta 测试中心", AppRoute.BetaTestCenter.title)
    }

    @Test
    fun unknownPagesSafelyReturnToGenerate() {
        assertEquals(AppRoute.Generate, AppRoute.fromPage("unknown"))
        assertEquals(0, AppRoute.Generate.mainTabIndex)
    }
}
