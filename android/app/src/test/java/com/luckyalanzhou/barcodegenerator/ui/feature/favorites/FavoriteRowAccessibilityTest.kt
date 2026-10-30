package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.component.menu.LocalLongPressMenuHost
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.ComposeFavoriteRow
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 收藏层级行的点击和长按语义应让辅助技术识别为按钮。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoriteRowAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun folderRowExposesButtonRoleAndLongClickAction() {
        compose.setContent {
            AppTheme("light") {
                CompositionLocalProvider(LocalLongPressMenuHost provides {}) {
                    FavoriteFolderRow(
                        row = ComposeFavoriteRow("Docs", "文档", 0, 0, true, true),
                        dark = false,
                        secondary = Color.Gray,
                        folderColor = Color.Blue,
                        animation = ComposeAnimationConfig,
                        onClick = {},
                        onShowSubfolderEditor = {},
                        onShowFolderEditor = { _, _ -> },
                        onConfirm = { _, _, _, _ -> },
                        onRenameFolder = { _, _ -> false },
                        onDeleteFolder = {},
                    )
                }
            }
        }

        val row = compose.onNode(hasText("文档") and hasClickAction())
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
    }

    @Test
    fun favoriteFileRowExposesButtonRoleAndLongClickAction() {
        val group = FavoriteGroup(7L, "Docs", "Manual", 0L, mutableListOf())
        compose.setContent {
            AppTheme("light") {
                CompositionLocalProvider(LocalLongPressMenuHost provides {}) {
                    FavoriteGroupRow(
                        row = ComposeFavoriteRow("Docs/Manual", "Manual", 1, 0, false, false, group),
                        group = group,
                        dark = false,
                        secondary = Color.Gray,
                        fileColor = Color.Blue,
                        animation = ComposeAnimationConfig,
                        onClick = {},
                        onShowMoveDialog = {},
                        onShowRenameDialog = {},
                        onEdit = {},
                        onConfirm = { _, _, _, _ -> },
                        onDelete = {},
                    )
                }
            }
        }

        val row = compose.onNode(hasText("Manual") and hasClickAction())
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
    }
}
