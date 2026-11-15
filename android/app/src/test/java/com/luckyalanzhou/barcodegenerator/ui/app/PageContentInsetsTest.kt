package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageContentInsetsTest {
    @Test fun imeChangesPagePaddingWithoutMovingTheTabBarAnchor() {
        val keyboardVisible = pageShellInsets(AppRoute.Generate, generateWithKeyboard = true, pageBottomPadding = 10.dp)
        val keyboardHidden = pageShellInsets(AppRoute.Generate, generateWithKeyboard = false, pageBottomPadding = 10.dp)

        assertEquals(0.dp, keyboardVisible.contentBottomPadding)
        assertEquals(10.dp, keyboardHidden.contentBottomPadding)
        assertEquals(10.dp, keyboardVisible.tabBarBottomPadding)
        assertEquals(keyboardHidden.tabBarBottomPadding, keyboardVisible.tabBarBottomPadding)
    }

    @Test fun leavingAListForGenerateKeepsClearanceUntilTheTransitionEnds() {
        listOf(AppRoute.History, AppRoute.Favorites, AppRoute.Settings).forEach { route ->
            assertEquals(80.dp, pageTransitionBottomInset(route, AppRoute.Generate, 72.dp))
            assertEquals(80.dp, pageTransitionBottomInset(AppRoute.Generate, route, 72.dp))
        }
        assertEquals(80.dp, pageTransitionBottomInset(AppRoute.Generate, AppRoute.Generate, 72.dp))
    }

    @Test fun transitionClearanceTracksActualTabHeight() {
        assertEquals(0.dp, pageTransitionBottomInset(AppRoute.Settings, AppRoute.History, 96.dp))
    }
    @Test
    fun listsDrawBehindNavigationAndReserveSpaceInsideTheirViewport() {
        listOf(AppRoute.History, AppRoute.Favorites, AppRoute.Settings).forEach { route ->
            assertTrue(route.drawsBehindSystemNavigation)
            assertEquals(0.dp, pageContentBottomInset(route, 72.dp))
            assertEquals(80.dp, pageListBottomPadding(route, 72.dp))
            assertEquals(114.dp, pageListBottomPadding(route, 72.dp, bottomChromeInset = 34.dp))
        }
        assertFalse(AppRoute.Generate.drawsBehindSystemNavigation)
        assertEquals("生成页也显示主 Tab 栏，滚动内容必须停在导航栏上方", 80.dp,
            pageContentBottomInset(AppRoute.Generate, 72.dp))
    }

    @Test
    fun insetTracksActualNavigationHeight() {
        assertEquals(104.dp, pageContentBottomInset(AppRoute.Generate, 96.dp))
        listOf(AppRoute.History, AppRoute.Favorites, AppRoute.Settings).forEach { route ->
            assertEquals(104.dp, pageListBottomPadding(route, 96.dp))
        }
    }

    @Test
    fun pagesWithoutMainTabBarDoNotReserveTabSpace() {
        listOf(AppRoute.Results, AppRoute.LanShare).forEach { route ->
            assertFalse(route.drawsBehindSystemNavigation)
            assertEquals(0.dp, pageContentBottomInset(route, 72.dp))
        }
    }
}
