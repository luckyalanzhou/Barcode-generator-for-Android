package com.luckyalanzhou.barcodegenerator.ui.app

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute
import com.luckyalanzhou.barcodegenerator.ui.app.favorites.FavoriteSaveRequest
import com.luckyalanzhou.barcodegenerator.ui.app.favorites.FavoriteSaveForm
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicReference

/** 验证收藏保存表单把实际选择的层级、名称和条码 ID 交给保存回调。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h873dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoriteSaveFormInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectingFoldersAndEnteringNameEmitsTheExpectedSaveRequest() {
        val item = CodeItem(id = 42L, text = "TEST-123", format = "Code 128-B")
        val request = AtomicReference<FavoriteSaveRequest?>()
        val validationMessage = AtomicReference<String?>()

        compose.setContent {
            AppTheme("light") {
                FavoriteSaveForm(
                    resultState = ResultUiState(items = listOf(item)),
                    dataState = BarcodeDataState(folders = listOf("Root", "Root/Child")),
                    dark = false,
                    saving = false,
                    findExistingGroupId = { _, _, _ -> null },
                    onSaveRequest = { saveRequest -> request.set(saveRequest) },
                    onOpenRootFolderEditor = { _ -> },
                    onOpenChildFolderEditor = { _, _ -> },
                    onShowOverwriteConfirmation = { _, _, _ -> error("unexpected name conflict") },
                    onValidationMessage = { validationMessage.set(it) },
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithText("选择一级文件夹").performClick()
        compose.onNodeWithText("Root").performClick()
        compose.onNodeWithText("选择二级文件夹").performClick()
        compose.onNodeWithText("Child").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Saved file")
        compose.onNodeWithText("保存").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertNotNull("Validation message: ${validationMessage.get()}", request.get())
            assertEquals(
                FavoriteSaveRequest(
                    itemIds = listOf(42L),
                    editingGroupId = null,
                    targetGroupId = null,
                    folder = "Root/Child",
                    name = "Saved file",
                ),
                request.get(),
            )
        }
    }

    @Test
    fun editingFavoriteKeepsItsIdentityAndSelectedFolder() {
        val item = CodeItem(id = 42L, text = "TEST-123", format = "Code 128-B")
        val group = FavoriteGroup(
            id = 77L,
            folder = "Root/Child",
            name = "Existing file",
            savedAt = 1L,
            itemIds = mutableListOf(item.id),
        )
        val request = AtomicReference<FavoriteSaveRequest?>()
        val validationMessage = AtomicReference<String?>()

        compose.setContent {
            AppTheme("light") {
                FavoriteSaveForm(
                    resultState = ResultUiState(
                        items = listOf(item),
                        returnPage = NavigationRoute.Favorites,
                        selectedFavoriteGroup = group,
                    ),
                    dataState = BarcodeDataState(folders = listOf("Root", "Root/Child"), groups = listOf(group)),
                    dark = false,
                    saving = false,
                    findExistingGroupId = { _, _, excluded ->
                        assertEquals(setOf(77L), excluded)
                        null
                    },
                    onSaveRequest = { request.set(it) },
                    onOpenRootFolderEditor = { _ -> },
                    onOpenChildFolderEditor = { _, _ -> },
                    onShowOverwriteConfirmation = { _, _, _ -> error("unexpected name conflict") },
                    onValidationMessage = { validationMessage.set(it) },
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithText("保存").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertNull("Validation message: ${validationMessage.get()}", validationMessage.get())
            assertEquals(
                FavoriteSaveRequest(
                    itemIds = listOf(42L),
                    editingGroupId = 77L,
                    targetGroupId = 77L,
                    folder = "Root/Child",
                    name = "Existing file",
                ),
                request.get(),
            )
        }
    }

    @Test
    fun conflictingFavoriteWaitsForConfirmationAndTargetsExistingGroup() {
        val item = CodeItem(id = 42L, text = "TEST-123", format = "Code 128-B")
        val request = AtomicReference<FavoriteSaveRequest?>()
        val confirmation = AtomicReference<(() -> Unit)?>()

        compose.setContent {
            AppTheme("light") {
                FavoriteSaveForm(
                    resultState = ResultUiState(items = listOf(item)),
                    dataState = BarcodeDataState(folders = listOf("Root", "Root/Child")),
                    dark = false,
                    saving = false,
                    findExistingGroupId = { _, _, excluded ->
                        assertEquals(emptySet<Long>(), excluded)
                        84L
                    },
                    onSaveRequest = { request.set(it) },
                    onOpenRootFolderEditor = { _ -> },
                    onOpenChildFolderEditor = { _, _ -> },
                    onShowOverwriteConfirmation = { title, message, onConfirm ->
                        assertEquals("覆盖收藏", title)
                        assertTrue(message.contains("Root/Child/Saved file"))
                        confirmation.set(onConfirm)
                    },
                    onValidationMessage = { error(it) },
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithText("选择一级文件夹").performClick()
        compose.onNodeWithText("Root").performClick()
        compose.onNodeWithText("选择二级文件夹").performClick()
        compose.onNodeWithText("Child").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Saved file")
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertNull("Overwrite confirmation must gate the save request", request.get())
            assertNotNull(confirmation.get())
            confirmation.get()?.invoke()
            assertEquals(
                FavoriteSaveRequest(
                    itemIds = listOf(42L),
                    editingGroupId = null,
                    targetGroupId = 84L,
                    folder = "Root/Child",
                    name = "Saved file",
                ),
                request.get(),
            )
        }
    }
}
