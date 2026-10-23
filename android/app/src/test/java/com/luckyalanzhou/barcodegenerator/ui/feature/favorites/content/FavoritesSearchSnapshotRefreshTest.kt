package com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression probe for an active search whose result cache is cleared during a snapshot reload. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesSearchSnapshotRefreshTest {
    @get:Rule val compose = createComposeRule()

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
                    onClearAll = {},
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
            // A reload with the same source snapshot resets the independent search-result cache.
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
}
