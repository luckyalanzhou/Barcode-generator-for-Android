package com.luckyalanzhou.barcodegenerator.ui.support.logging

import org.junit.Assert.*
import org.junit.Test

class LogRecordSelectionTest {
    @Test fun exportIncludesLatestFaultWholeStackAndExplicitOmissionsWithinByteLimit() {
        val failure = record(2, "最新错误😀\nException: broken\n\tat sample.Main.run(Main.kt:1)")
        val old = record(1, "older".repeat(100))
        val output = buildLogExport(listOf("debug-today.log" to old + failure, "debug-yesterday.log" to old), 500)
        assertTrue(output.contains(failure))
        assertFalse(output.contains("id=1"))
        assertTrue(output.contains("omittedRecords=2"))
        assertTrue(output.toByteArray(Charsets.UTF_8).size <= 500)
        assertFalse(output.contains('\uFFFD'))
    }

    @Test fun nonAsciiFileNamesAndEmptyArchivesKeepBoundedHeaders() {
        val line = record(3, "操作完成")
        val output = buildLogExport(listOf("中文日志.log" to line), 512)
        assertTrue(output.contains(line))
        assertTrue(output.contains("omittedRecords=0"))
        assertTrue(output.toByteArray(Charsets.UTF_8).size <= 512)
        assertTrue(buildLogExport(emptyList(), 256).toByteArray(Charsets.UTF_8).size <= 256)
    }
    private fun record(index: Int, body: String) = "2026-10-09 12:00:00.000 [test] id=$index $body\n"

    @Test fun latestFailureRetainsWholeChineseRecordAndStack() {
        val old = record(1, "旧操作")
        val latest = record(2, "最新失败\njava.lang.IllegalStateException: 中文😀\n\tat sample.Main.run(Main.kt:1)")
        val selected = selectRecentLogRecords(old + latest, latest.toByteArray(Charsets.UTF_8).size)
        assertEquals(latest, selected.text)
        assertEquals(1, selected.omittedRecords)
        assertFalse(selected.text.contains('\uFFFD'))
    }

    @Test fun exactByteBudgetDoesNotSplitMultibyteCharacters() {
        val line = record(1, "😀中文")
        assertEquals(line, selectRecentLogRecords(line, line.toByteArray(Charsets.UTF_8).size).text)
        val limited = selectRecentLogRecords(line, line.toByteArray(Charsets.UTF_8).size - 1)
        assertEquals("", limited.text)
        assertEquals(1, limited.omittedRecords)
    }

    @Test fun completeArchiveKeepsOrderAndNoOmissions() {
        val input = record(1, "开始") + record(2, "完成")
        assertEquals(LogRecordSelection(input, 0), selectRecentLogRecords(input, 4096))
        assertEquals(LogRecordSelection("", 0), selectRecentLogRecords("", 0))
    }

    @Test fun oversizedRecordIsExplicitlyCountedAndDoesNotOverflowBudget() {
        val input = record(1, "short") + record(2, "长".repeat(1000))
        val selected = selectRecentLogRecords(input, 200)
        assertEquals(record(1, "short"), selected.text)
        assertEquals(1, selected.omittedRecords)
        assertTrue(selected.text.toByteArray(Charsets.UTF_8).size <= 200)
    }
}
