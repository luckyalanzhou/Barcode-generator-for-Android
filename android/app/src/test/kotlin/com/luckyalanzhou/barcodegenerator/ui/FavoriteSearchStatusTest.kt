package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.R
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
        assertEquals(R.string.favorites_empty, favoriteEmptyMessage(""))
        assertEquals(R.string.favorites_no_matches, favoriteEmptyMessage("new"))
    }
}
