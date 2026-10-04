package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoriteSearchStatus
import com.luckyalanzhou.barcodegenerator.presentation.favorites.favoriteEmptyMessage
import org.junit.Assert.*
import org.junit.Test

class FavoriteSearchStatusTest {
    @Test fun oldQueryCannotBePresentedAsCompletedSearch() {
        assertTrue(FavoriteSearchStatus("old").isPending("new"))
    }
    @Test fun debounceAndDatabaseWorkRemainPending() {
        assertTrue(FavoriteSearchStatus("new", busy = true).isPending("new"))
    }
    @Test fun completedQueryAndClearedQueryAreNotPending() {
        assertFalse(FavoriteSearchStatus("new").isPending("new"))
        assertFalse(FavoriteSearchStatus("old", busy = true).isPending(""))
    }
    @Test fun emptyLibraryAndNoMatchesHaveDifferentMessages() {
        assertEquals("还没有收藏", favoriteEmptyMessage(""))
        assertEquals("没有匹配的收藏", favoriteEmptyMessage("new"))
    }
}
