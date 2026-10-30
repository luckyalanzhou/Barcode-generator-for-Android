package com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeState
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 回归检查：活动搜索的结果缓存会在快照重新加载时被清空。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesSearchSnapshotRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun failedFavoritesLoadOffersRetryInsteadOfAnEmptyState() {
        var retryCount = 0
        val state = FavoritesContentState(
            favorites = FavoritesListContentState(isReady = false, loadFailed = true),
        )
        compose.setContent {
            AppTheme("light") {
                FavoritesContent(
                    state = state,
                    scrollToTopEvents = emptyFlow(),
                    dark = false,
                    style = StyleSettings(),
                    onQueryChange = {},
                    onSearchFavoriteContent = {},
                    onSyncFavoriteTree = {},
                    onUpdateFavoriteSearch = { _, _ -> },
                    onRememberListPosition = { _, _ -> },
                    onLoadMoreGroups = {},
                    onToggleFolder = { _, _ -> },
                    onOpenGroup = { _, _, _, _ -> },
                    onRenameFolder = { _, _ -> false },
                    onDeleteFolder = {},
                    onDeleteGroup = {},
                    onShowSubfolderEditor = {},
                    onShowFolderEditor = { _, _ -> },
                    onEdit = {},
                    onShowMoveDialog = {},
                    onShowRenameDialog = {},
                    onConfirm = { _, _, _, _ -> },
                    onRetryLoad = { retryCount++ },
                )
            }
        }

        compose.onNodeWithText("收藏数据加载失败").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("收藏数据加载失败").assertIsDisplayed()
        assertEquals(1, retryCount)
    }

    @Test
    fun previousSearchRowsRemainVisibleWhileNextQueryIsPending() {
        val previousGroup = FavoriteGroup(42L, "Optoma", "GT2400HDR", 42L, mutableListOf(42L))
        val state = mutableStateOf(
            FavoritesContentState(
                favorites = FavoritesListContentState(groups = listOf(previousGroup), isReady = true),
                tree = FavoritesTreeState(knownFolders = setOf("Optoma")),
            ),
        )

        compose.setContent {
            AppTheme("light") {
                FavoritesContent(
                    state = state.value,
                    scrollToTopEvents = emptyFlow(),
                    dark = false,
                    style = StyleSettings(),
                    onQueryChange = {},
                    onSearchFavoriteContent = {},
                    onSyncFavoriteTree = {},
                    onUpdateFavoriteSearch = { _, _ -> },
                    onRememberListPosition = { _, _ -> },
                    onLoadMoreGroups = {},
                    onToggleFolder = { _, _ -> },
                    onOpenGroup = { _, _, _, _ -> },
                    onRenameFolder = { _, _ -> false },
                    onDeleteFolder = {},
                    onDeleteGroup = {},
                    onShowSubfolderEditor = {},
                    onShowFolderEditor = { _, _ -> },
                    onEdit = {},
                    onShowMoveDialog = {},
                    onShowRenameDialog = {},
                    onConfirm = { _, _, _, _ -> },
                )
            }
        }

        compose.waitForIdle()
        compose.runOnIdle {
            state.value = state.value.copy(
                searchResults = FavoritesListContentState(isReady = true),
                search = FavoritesSearchContentState(query = "new query", busy = true),
                query = "new query",
            )
        }

        compose.onNodeWithText("正在搜索…").assertIsDisplayed()
        compose.onNodeWithText("GT2400HDR").assertIsDisplayed()
    }

    @Test
    fun activeSearchRunsAgainWhenReloadResetsResultsForSameFavoriteSnapshot() {
        val matchedGroup = FavoriteGroup(2L, "目录", "匹配收藏", 2L, mutableListOf(2L))
        val state = mutableStateOf(
            FavoritesContentState(
                searchResults = FavoritesListContentState(isReady = true),
                search = FavoritesSearchContentState(query = "目标"),
                query = "目标",
            ),
        )
        val searchCalls = mutableListOf<String>()

        compose.setContent {
            AppTheme("light") {
                FavoritesContent(
                    state = state.value,
                    scrollToTopEvents = emptyFlow(),
                    dark = false,
                    style = StyleSettings(),
                    onQueryChange = {},
                    onSearchFavoriteContent = { query ->
                        searchCalls += query
                        state.value = state.value.copy(
                            searchResults = FavoritesListContentState(
                                groups = listOf(matchedGroup),
                                isReady = true,
                            ),
                            search = FavoritesSearchContentState(query = query),
                        )
                    },
                    onSyncFavoriteTree = {},
                    onUpdateFavoriteSearch = { _, _ -> },
                    onRememberListPosition = { _, _ -> },
                    onLoadMoreGroups = {},
                    onToggleFolder = { _, _ -> },
                    onOpenGroup = { _, _, _, _ -> },
                    onRenameFolder = { _, _ -> false },
                    onDeleteFolder = {},
                    onDeleteGroup = {},
                    onShowSubfolderEditor = {},
                    onShowFolderEditor = { _, _ -> },
                    onEdit = {},
                    onShowMoveDialog = {},
                    onShowRenameDialog = {},
                    onConfirm = { _, _, _, _ -> },
                )
            }
        }

        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("目标"), searchCalls)
            // 使用相同来源快照重新加载时，会重置独立的搜索结果缓存。
            state.value = state.value.copy(
                searchResults = FavoritesListContentState(isReady = true),
                search = FavoritesSearchContentState(query = "目标", busy = true),
                searchRefreshGeneration = 1L,
            )
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals("An active query must be reissued after its results are reset", 2, searchCalls.size)
            assertEquals(listOf(matchedGroup), state.value.searchResults.groups)
        }
    }

    @Test
    fun scrollingSearchResultsDoesNotReplaceRegularFavoritesScrollPosition() {
        val regularGroups = (0 until 20).map { index ->
            FavoriteGroup(index.toLong(), "folder-$index", "file-$index", index.toLong(), mutableListOf())
        }
        val searchedGroups = (0 until 4).map { index ->
            FavoriteGroup(100L + index, "target-folder-$index", "target-file-$index", index.toLong(), mutableListOf())
        }
        val state = mutableStateOf(
            FavoritesContentState(
                favorites = FavoritesListContentState(
                    groups = regularGroups,
                    folders = regularGroups.map { it.folder },
                    isReady = true,
                ),
            ),
        )
        var rememberedRegularPosition = 0 to 0
        var openedGroup = false

        compose.setContent {
            AppTheme("light") {
                FavoritesContent(
                    state = state.value,
                    scrollToTopEvents = emptyFlow(),
                    dark = false,
                    style = StyleSettings(),
                    onQueryChange = { query -> state.value = state.value.copy(query = query) },
                    onSearchFavoriteContent = { query ->
                        state.value = state.value.copy(
                            searchResults = FavoritesListContentState(
                                groups = if (query.isEmpty()) emptyList() else searchedGroups,
                                isReady = true,
                            ),
                            search = FavoritesSearchContentState(query = query),
                        )
                    },
                    onSyncFavoriteTree = {},
                    onUpdateFavoriteSearch = { _, _ -> },
                    onRememberListPosition = { index, offset -> rememberedRegularPosition = index to offset },
                    onLoadMoreGroups = {},
                    onToggleFolder = { _, _ -> },
                    onOpenGroup = { _, _, _, _ -> openedGroup = true },
                    onRenameFolder = { _, _ -> false },
                    onDeleteFolder = {},
                    onDeleteGroup = {},
                    onShowSubfolderEditor = {},
                    onShowFolderEditor = { _, _ -> },
                    onEdit = {},
                    onShowMoveDialog = {},
                    onShowRenameDialog = {},
                    onConfirm = { _, _, _, _ -> },
                )
            }
        }

        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(15)
        compose.waitForIdle()
        val positionBeforeSearch = compose.runOnIdle { rememberedRegularPosition }
        assertTrue("the regular list must first record a non-top position", positionBeforeSearch.first > 0)

        compose.onNodeWithContentDescription("搜索收藏名称、文件夹或条码内容").performTextInput("target")
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(
                "search-result scroll state must not overwrite the regular favorites restore position",
                positionBeforeSearch,
                rememberedRegularPosition,
            )
        }
        compose.onNodeWithText("target-file-0").performClick()
        compose.runOnIdle {
            assertTrue("the search result should still open", openedGroup)
            assertEquals(
                "opening a search result must not overwrite the regular favorites restore position",
                positionBeforeSearch,
                rememberedRegularPosition,
            )
        }
    }
}
