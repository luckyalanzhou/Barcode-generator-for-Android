package com.luckyalanzhou.barcodegenerator.ui.app

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.luckyalanzhou.barcodegenerator.ui.component.glass.LocalGlassBackdrop
import com.luckyalanzhou.barcodegenerator.ui.component.glass.recordGlassBackdrop
import com.luckyalanzhou.barcodegenerator.ui.component.glass.rememberGlassBackdrop
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
        var photoFillTriggered = false
        compose.setContent {
            AppTheme("dark") {
                CompositionLocalProvider(LocalVisualEffectsPolicy provides VisualEffectsPolicy(reduceMotion = true, opaqueGlass = true)) {
                    Box(Modifier.fillMaxSize()) {
                        BarcodeComposeBottomTabBar(selected.intValue, true,
                            onTabSelected = { index, _ -> clicks.add(index); selected.intValue = index },
                            onGeneratePhotoFill = { photoFillTriggered = true },
                            onHistoryClear = {}, onFavoritesImport = {}, onFavoritesExport = {}, onFavoritesClear = {}, onCheckForUpdates = {},
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
        listOf("生成条码", "历史记录", "收藏夹", "设置").forEach {
            val semantics = compose.onNodeWithContentDescription(it).fetchSemanticsNode().config
            assertTrue(semantics.contains(SemanticsActions.OnLongClick))
            assertFalse(semantics.contains(SemanticsActions.CustomActions))
            compose.onNodeWithContentDescription(it).performSemanticsAction(SemanticsActions.OnLongClick) { action -> action() }
        }
        compose.runOnIdle {
            assertEquals(listOf("生成", "历史", "收藏", "设置"), menus.map { it.focusLabel })
            assertTrue(menus.all { it.anchorBoundsOnScreen.width > 26f && it.actions.isNotEmpty() })
            assertTrue(menus.zipWithNext().all { (left, right) -> left.anchorBoundsOnScreen.left < right.anchorBoundsOnScreen.left })
            assertEquals(listOf("拍照填充"), menus.first().actions.map { it.label })
            menus.first().actions.single().onClick()
            assertTrue(photoFillTriggered)
            assertEquals(listOf(1, 2, 3, 0), clicks) // 打开未选中 Tab 菜单不切换页面。
        }
    }

    @Test fun tabIconsAndLabelsRemainVisibleWhilePressedAndDragged() {
        val canvas = Color(0xFFF0F0F0)
        val selected = mutableIntStateOf(0)
        var swipeReachedAnotherTab = false
        val swipeSelections = mutableListOf<Int>()
        lateinit var recordedBackdrop: com.luckyalanzhou.barcodegenerator.ui.component.glass.GlassBackdropSource
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            recordedBackdrop = backdrop
            AppTheme("light") {
                CompositionLocalProvider(
                    LocalGlassBackdrop provides backdrop,
                    LocalVisualEffectsPolicy provides VisualEffectsPolicy(),
                ) {
                    Box(Modifier.fillMaxSize().background(canvas).recordGlassBackdrop(backdrop))
                    Box(Modifier.fillMaxSize()) {
                        BarcodeComposeBottomTabBar(
                            selectedIndex = selected.intValue,
                            dark = false,
                            onTabSelected = { index, fromSwipe ->
                                selected.intValue = index
                                if (fromSwipe) {
                                    swipeSelections.add(index)
                                    if (index != 0) swipeReachedAnotherTab = true
                                }
                            },
                            onGeneratePhotoFill = {}, onHistoryClear = {}, onFavoritesImport = {}, onFavoritesClear = {},
                            onFavoritesExport = {}, onCheckForUpdates = {},
                            onLongPressActionMenuRequested = {},
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onRoot()
        val tabBounds = listOf("生成条码", "历史记录", "收藏夹", "设置").map {
            compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
        }
        val start = tabBounds[0].center
        val next = tabBounds[1].center
        root.performTouchInput { down(start) }
        assertTabPixelsVisible(canvas, tabBounds)
        root.performTouchInput {
            advanceEventTime(24)
            moveTo(next)
            advanceEventTime(24)
        }
        compose.runOnIdle {
            assertTrue("The test gesture must exercise the production drag-to-switch path", swipeReachedAnotherTab)
            assertTrue("The page backdrop must be recorded during the Tab swipe", recordedBackdrop.ready && recordedBackdrop.hasFrame)
        }
        assertTabPixelsVisible(canvas, tabBounds)
        root.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals("A drag crossing should commit its target once, not again on release", listOf(1), swipeSelections)
        }
    }

    private fun assertTabPixelsVisible(background: Color, bounds: List<androidx.compose.ui.geometry.Rect>) {
        val image = compose.onRoot().captureToImage()
        val pixels = image.toPixelMap()
        val imageBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        bounds.forEachIndexed { index, rect ->
            val left = (rect.left - imageBounds.left).toInt().coerceIn(0, image.width - 1)
            val right = (rect.right - imageBounds.left).toInt().coerceIn(left + 1, image.width)
            val top = (rect.top - imageBounds.top).toInt().coerceIn(0, image.height - 1)
            val bottom = (rect.bottom - imageBounds.top).toInt().coerceIn(top + 1, image.height)
            var visible = 0
            for (y in top until bottom) for (x in left until right) {
                val color = pixels[x, y]
                if (kotlin.math.abs(color.red - background.red) +
                    kotlin.math.abs(color.green - background.green) +
                    kotlin.math.abs(color.blue - background.blue) > .12f
                ) visible++
            }
            assertTrue("Tab $index should keep visible pixels during touch; found $visible", visible > 12)
        }
    }
}
