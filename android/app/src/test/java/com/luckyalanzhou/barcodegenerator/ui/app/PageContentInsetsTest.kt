package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PageContentInsetsTest {
    @Test
    fun listsReserveNavigationSpaceOutsideTheirViewport() {
        listOf(AppRoute.History, AppRoute.Favorites, AppRoute.Settings).forEach { route ->
            assertEquals(80.dp, pageContentBottomInset(route, 72.dp))
        }
    }

    @Test
    fun insetTracksActualNavigationHeight() {
        listOf(AppRoute.History, AppRoute.Favorites, AppRoute.Settings).forEach { route ->
            assertEquals(104.dp, pageContentBottomInset(route, 96.dp))
        }
    }

    @Test
    fun otherPagesRetainTheirExistingViewport() {
        listOf(AppRoute.Generate, AppRoute.Results, AppRoute.LanShare).forEach { route ->
            assertEquals(0.dp, pageContentBottomInset(route, 72.dp))
        }
    }
}
