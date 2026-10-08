package com.luckyalanzhou.barcodegenerator.presentation.results

import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedResultStateTest {
    @Test
    fun savesOnlyStableIdentifiersAndReturnSource() {
        val handle = SavedStateHandle()
        val saved = SavedResultState(handle)
        saved.save(ResultUiState(
            items = listOf(item(3), item(1)),
            returnPage = NavigationRoute.Favorites,
            selectedFavoriteGroup = FavoriteGroup(9, "一级", "二级", 10, mutableListOf(3, 1)),
        ))

        assertEquals(
            SavedResultDescriptor(listOf(3, 1), NavigationRoute.Favorites, false, 9),
            SavedResultState(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })).read(),
        )
        assertFalse(handle.keys().any { it.contains("bitmap", ignoreCase = true) })
        saved.clear()
        assertNull(saved.read())
    }

    @Test
    fun reloadsItemsInOriginalOrderAndKeepsReturnPage() = runBlocking {
        val descriptor = SavedResultDescriptor(listOf(3, 1), NavigationRoute.History, true, null)
        val restored = loadSavedResult(descriptor, { listOf(item(1), item(3)) }, { emptyList() })
        assertEquals(listOf(3L, 1L), restored?.items?.map(CodeItem::id))
        assertEquals(NavigationRoute.History, restored?.returnPage)
        assertTrue(restored?.showingHistoryResult == true)
    }

    @Test
    fun missingItemOrFavoriteGroupCannotSilentlyBecomePartialResult() = runBlocking {
        val favorite = SavedResultDescriptor(listOf(3, 1), NavigationRoute.Favorites, false, 9)
        assertNull(loadSavedResult(favorite, { listOf(item(3)) }, { emptyList() }))
        assertNull(loadSavedResult(favorite, { listOf(item(3), item(1)) }, { emptyList() }))
        val restored = loadSavedResult(favorite, { listOf(item(3), item(1)) }) {
            listOf(FavoriteGroup(9, "一级", "二级", 10, mutableListOf(3, 1)))
        }
        assertEquals(9L, restored?.selectedFavoriteGroup?.id)
    }

    private fun item(id: Long) = CodeItem(id, "item-$id", "QR_CODE")
}
