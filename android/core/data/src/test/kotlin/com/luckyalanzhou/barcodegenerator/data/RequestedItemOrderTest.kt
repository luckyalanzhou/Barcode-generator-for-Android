package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestedItemOrderTest {
    private val first = item(10L, "first")
    private val second = item(20L, "second")
    private val third = item(30L, "third")

    @Test
    fun databaseOrderCannotChangeRequestedOrder() {
        val restored = restoreRequestedItemOrder(
            requestedIds = listOf(30L, 10L, 20L),
            loadedItems = listOf(first, second, third),
        )

        assertEquals(listOf(30L, 10L, 20L), restored.map(CodeItem::id))
    }

    @Test
    fun duplicateIdsKeepOnlyTheirFirstDeclaredPosition() {
        val restored = restoreRequestedItemOrder(
            requestedIds = listOf(20L, 10L, 20L, 30L),
            loadedItems = listOf(third, second, first),
        )

        assertEquals(listOf(20L, 10L, 30L), restored.map(CodeItem::id))
    }

    @Test
    fun missingRowsAreOmittedWithoutReorderingRemainingRows() {
        val restored = restoreRequestedItemOrder(
            requestedIds = listOf(30L, 999L, 10L),
            loadedItems = listOf(first, third),
        )

        assertEquals(listOf(30L, 10L), restored.map(CodeItem::id))
    }

    private fun item(id: Long, text: String) = CodeItem(
        id = id,
        text = text,
        format = "Code 128-B",
        createdAt = id,
        favorite = true,
        folder = "",
        inHistory = false,
    )
}
