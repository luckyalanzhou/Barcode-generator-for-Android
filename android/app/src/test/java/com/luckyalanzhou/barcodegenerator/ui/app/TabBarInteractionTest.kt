package com.luckyalanzhou.barcodegenerator.ui.app

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.theme.VisualEffectsPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabBarInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun tabsSelectIndependentlyAndMenusCarryWholeSlotAnchors() {
        val selected = mutableIntStateOf(0)
        val clicks = mutableListOf<Int>()
        val menus = mutableListOf<TabLongPressMenuState>()
        compose.setContent {
            AppTheme("dark") {
                CompositionLocalProvider(LocalVisualEffectsPolicy provides VisualEffectsPolicy(reduceMotion = true, opaqueGlass = true)) {
                    Box(Modifier.fillMaxSize()) {
                        BarcodeComposeBottomTabBar(selected.intValue, true,
                            onTabSelected = { index, _ -> clicks.add(index); selected.intValue = index },
                            onHistoryClear = {}, onFavoritesImport = {}, onFavoritesExport = {}, onCheckForUpdates = {},
                            onLongPressActionMenuRequested = menus::add,
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter), showSelectionIndicator = false)
                    }
                }
            }
        }
        listOf("历史记录", "收藏夹", "设置", "生成条码").forEach {
            compose.onNodeWithContentDescription(it).performClick()
        }
        compose.runOnIdle { assertEquals(listOf(1, 2, 3, 0), clicks) }
        listOf("历史记录", "收藏夹", "设置").forEach {
            compose.onNodeWithContentDescription(it).performSemanticsAction(SemanticsActions.OnLongClick) { action -> action() }
        }
        compose.runOnIdle {
            assertEquals(listOf("历史", "收藏", "设置"), menus.map { it.focusLabel })
            assertTrue(menus.all { it.anchorBoundsOnScreen.width > 26f && it.actions.isNotEmpty() })
            assertTrue(menus.zipWithNext().all { (left, right) -> left.anchorBoundsOnScreen.left < right.anchorBoundsOnScreen.left })
            assertEquals(listOf(1, 2, 3, 0), clicks) // 打开未选中 Tab 菜单不切换页面。
        }
    }
}
