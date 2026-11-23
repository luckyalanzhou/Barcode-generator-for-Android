package com.luckyalanzhou.barcodegenerator.ui.feature.history

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryBatchSearchTest {
    @Test
    fun matchesAnyItemWithoutDroppingOtherItemsInTheBatch() {
        val batch = HistoryBatchEntry("batch:1", 42, listOf(item(1, "FIRST"), item(2, "Qa-SECOND")))
        val unrelated = HistoryBatchEntry("batch:2", 43, listOf(item(3, "OTHER")))

        assertEquals(listOf(batch), filterHistoryBatches(listOf(unrelated, batch), " qa-second "))
        assertEquals("Qa-SECOND…", historyBatchSummary(batch.items, "qa-second"))
        assertEquals(listOf(unrelated, batch), filterHistoryBatches(listOf(unrelated, batch), " "))
        assertEquals(emptyList<HistoryBatchEntry>(), filterHistoryBatches(listOf(batch), "no match"))
    }

    private fun item(id: Long, text: String) = CodeItem(id, text, "Code 128-B")
}
