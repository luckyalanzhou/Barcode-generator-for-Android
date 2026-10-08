package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content.FavoritesSearchContentState
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content.favoriteEmptyMessage
import org.junit.Assert.*
import org.junit.Test

class FavoriteSearchStatusTest {
    @Test fun oldQueryCannotBePresentedAsCompletedSearch() {
        assertTrue(FavoritesSearchContentState("old").isPending("new"))
    }
    @Test fun debounceAndDatabaseWorkRemainPending() {
        assertTrue(FavoritesSearchContentState("new", busy = true).isPending("new"))
    }
    @Test fun completedQueryAndClearedQueryAreNotPending() {
        assertFalse(FavoritesSearchContentState("new").isPending("new"))
        assertFalse(FavoritesSearchContentState("old", busy = true).isPending(""))
    }
    @Test fun emptyLibraryAndNoMatchesHaveDifferentMessages() {
        assertEquals("还没有收藏", favoriteEmptyMessage(""))
        assertEquals("没有匹配的收藏", favoriteEmptyMessage("new"))
    }
}
