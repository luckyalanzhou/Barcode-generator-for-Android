package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class GeneratedItemInsertTest {
    @Test
    fun allocationUsesPersistedMaximumInsteadOfDraftOrHistoryIds() = runBlocking {
        val stored = mutableListOf(CodeItemEntity(900, "imported", "QR_CODE", 1, true, "folder", false))
        val dao = fakeDao(stored)
        val inserted = insertGeneratedItemsWithAllocatedIds(dao, listOf(
            CodeItem(1, "first", "QR_CODE", 2), CodeItem(2, "second", "QR_CODE", 2),
        ))
        assertEquals(listOf(901L, 902L), inserted.map { it.id })
        assertEquals("imported", stored.first().text)
        assertEquals(false, stored.first().inHistory)
        assertEquals(listOf("first", "second"), inserted.map { it.text })
    }

    @Test
    fun consecutiveInsertsDoNotReuseIdsWhenTheCallerStillHasOldDrafts() = runBlocking {
        val stored = mutableListOf<CodeItemEntity>()
        val dao = fakeDao(stored)
        val draft = listOf(CodeItem(1, "same draft", "QR_CODE", 2))
        assertEquals(1L, insertGeneratedItemsWithAllocatedIds(dao, draft).single().id)
        assertEquals(2L, insertGeneratedItemsWithAllocatedIds(dao, draft).single().id)
        assertEquals(1L, draft.single().id)
    }

    @Test
    fun exhaustedIdsFailBeforeAnyInsert() = runBlocking {
        val stored = mutableListOf(CodeItemEntity(Long.MAX_VALUE, "keep", "QR_CODE", 1, true, "", false))
        try {
            insertGeneratedItemsWithAllocatedIds(fakeDao(stored), listOf(CodeItem(1, "new", "QR_CODE")))
            fail("Expected exhausted ID space to reject the insert")
        } catch (_: IllegalArgumentException) {
            assertEquals(1, stored.size)
            assertEquals("keep", stored.single().text)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeDao(stored: MutableList<CodeItemEntity>): BarcodeDao = Proxy.newProxyInstance(
        BarcodeDao::class.java.classLoader, arrayOf(BarcodeDao::class.java),
    ) { _, method, arguments ->
        when (method.name) {
            "maxItemId" -> stored.maxOfOrNull { it.id } ?: 0L
            "insertNewItems" -> {
                val newItems = arguments[0] as List<CodeItemEntity>
                check(newItems.none { candidate -> stored.any { it.id == candidate.id } })
                stored.addAll(newItems)
                Unit
            }
            else -> error("Unexpected DAO operation: ${method.name}")
        }
    } as BarcodeDao
}
