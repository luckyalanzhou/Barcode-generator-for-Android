package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoriteSearchQueryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSearchQueryStateTest {
    @Test
    fun preservesInputTextAndSearchesByNormalizedValue() {
        val state = FavoriteSearchQueryState()

        assertTrue(state.update(" AbC "))
        assertEquals(" AbC ", state.query.value)
        assertEquals("abc", state.normalizedQuery)

        assertFalse(state.update("abc "))
        assertEquals("abc ", state.query.value)
        assertEquals("abc", state.normalizedQuery)

        assertTrue(state.update("   "))
        assertEquals("", state.normalizedQuery)
    }
}
