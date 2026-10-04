package com.luckyalanzhou.barcodegenerator.ui.feature.history

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import org.junit.Assert.*
import org.junit.Test

class HistoryBatchSummaryTest {
    @Test fun singleItemShowsItsTextNotItsCount() {
        assertEquals("FIRST", historyBatchSummary(listOf(CodeItem(1, "FIRST", "Code 128-B"))))
    }
    @Test fun batchShowsOnlyFirstTextAndEllipsis() {
        assertEquals("FIRST…", historyBatchSummary(listOf(
            CodeItem(1, "FIRST", "Code 128-B"), CodeItem(2, "SECOND", "Code 128-B"))))
    }
    @Test fun multilineInputCannotIncreaseTheCardHeight() {
        assertEquals("A B C", historyBatchSummary(listOf(CodeItem(1, "A\nB\rC", "QR Code"))))
    }
}
