package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritesMutationCoordinatorOrderTest {
    private val items = listOf(
        CodeItem(10L, "ten", "Code 128-B", 1L),
        CodeItem(20L, "twenty", "Code 128-B", 2L),
        CodeItem(30L, "thirty", "Code 128-B", 3L),
    )

    @Test
    fun selectedFavoriteItemsFollowGeneratedResultOrder() {
        val selected = orderedFavoriteItems(items, listOf(30L, 10L, 20L))

        assertEquals(listOf(30L, 10L, 20L), selected?.map { it.id })
    }

    @Test
    fun missingResultItemRejectsPartialFavoriteInsteadOfSilentlyChangingItsContents() {
        assertNull(orderedFavoriteItems(items, listOf(30L, 999L)))
    }
}
