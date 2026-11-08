package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.component.menu.LocalLongPressMenuHost
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.ComposeFavoriteRow
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 布局阶段移动行后，长按必须使用实时坐标，而非上一次组合时的矩形。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoriteRowCoordinatesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fileMenuTracksPlacementWithoutRecreatingTheRow() = checkAnchor(folder = false)
    @Test fun folderMenuTracksPlacementWithoutRecreatingTheRow() = checkAnchor(folder = true)

    private fun checkAnchor(folder: Boolean) {
        val shift = mutableIntStateOf(0)
        var pixelsPerDp = 1f
        var menu: TabLongPressMenuState? = null
        val group = FavoriteGroup(7L, "Docs", "Manual", 0L, mutableListOf())
        compose.setContent {
            AppTheme("light") {
                pixelsPerDp = LocalDensity.current.density
                CompositionLocalProvider(LocalLongPressMenuHost provides { menu = it }) {
                    // 保留完整视口，移动后行和文字仍可见，不触发被裁切锚点的回退。
                    Box(Modifier.fillMaxSize()) {
                    Box(Modifier.offset { IntOffset(0, shift.intValue) }) {
                        if (folder) {
                            FavoriteFolderRow(
                                row = ComposeFavoriteRow("Docs", "文档", 0, 0, true, true),
                                modifier = Modifier, dark = false, secondary = Color.Gray,
                                folderColor = Color.Blue, animation = ComposeAnimationConfig,
                                animateTreeChanges = false, onClick = {}, onShowSubfolderEditor = {},
                                onShowFolderEditor = { _, _ -> }, onConfirm = { _, _, _, _ -> },
                                onRenameFolder = { _, _ -> false }, onDeleteFolder = {},
                            )
                        } else {
                            FavoriteGroupRow(
                                row = ComposeFavoriteRow("Docs/Manual", "Manual", 1, 0, false, false, group),
                                modifier = Modifier, group = group, dark = false, secondary = Color.Gray,
                                fileColor = Color.Blue, animation = ComposeAnimationConfig, onClick = {},
                                onShowMoveDialog = {}, onShowRenameDialog = {}, onEdit = {},
                                onConfirm = { _, _, _, _ -> }, onDelete = {},
                            )
                        }
                    }
                    }
                }
            }
        }
        val row = compose.onNode(hasText(if (folder) "文档" else "Manual") and hasClickAction())
        row.performTouchInput { longClick() }
        compose.runOnIdle { assertNotNull(menu) }
        val before = requireNotNull(menu)
        compose.runOnIdle { shift.intValue = (40f * pixelsPerDp).toInt() }
        compose.waitForIdle()
        row.performTouchInput { longClick() }
        compose.runOnIdle {
            val after = requireNotNull(menu)
            assertEquals(shift.intValue.toFloat(), after.anchorBoundsOnScreen.top - before.anchorBoundsOnScreen.top, 1f)
            assertEquals(shift.intValue.toFloat(), after.menuAnchorBoundsOnScreen.top - before.menuAnchorBoundsOnScreen.top, 1f)
            assertEquals(before.anchorBoundsOnScreen.width, after.anchorBoundsOnScreen.width, 1f)
        }
    }
}
