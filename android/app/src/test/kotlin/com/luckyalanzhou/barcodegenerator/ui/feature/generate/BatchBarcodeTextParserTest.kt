package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchBarcodeTextParserTest {
    @Test
    fun preservesOrderDuplicatesLeadingZerosWhitespaceAndUnicode() {
        val result = BatchBarcodeTextParser.parse("00123\r\n 零件-A \n00123\r\n")

        assertEquals(
            BatchBarcodeTextResult.Success(listOf("00123", " 零件-A ", "00123")),
            result,
        )
    }

    @Test
    fun parsesSingleColumnCsvWithQuotedCommasAndEscapedQuotes() {
        val result = BatchBarcodeTextParser.parse("001\n\"A,B\"\n\"say \"\"hello\"\"\"\n")

        assertEquals(BatchBarcodeTextResult.Success(listOf("001", "A,B", "say \"hello\"")), result)
    }

    @Test
    fun ignoresBlankRowsAndUtf8BomWithoutTrimmingValues() {
        val result = BatchBarcodeTextParser.parse("\uFEFF\r\n 001 \r\n\r\n")

        assertEquals(BatchBarcodeTextResult.Success(listOf(" 001 ")), result)
    }

    @Test
    fun rejectsMultipleColumnsWithoutDroppingAnyField() {
        val result = BatchBarcodeTextParser.parse("first\nsecond,third\nfourth")

        assertEquals(
            BatchBarcodeTextResult.Failure(2, "每行只能有一个条码值；含逗号或双引号的值请用双引号括起"),
            result,
        )
    }

    @Test
    fun rejectsMalformedCsvAndReportsSourceLine() {
        val result = BatchBarcodeTextParser.parse("first\n\"unfinished")

        assertTrue(result is BatchBarcodeTextResult.Failure && result.line == 2)
    }

    @Test
    fun rejectsTooManyRowsAtomically() {
        val result = BatchBarcodeTextParser.parse("one\ntwo\nthree", maxRows = 2)

        assertEquals(BatchBarcodeTextResult.Failure(3, "最多导入 2 行条码"), result)
    }

    @Test
    fun rejectsEmptyInputAndDoesNotSilentlyCreateAnEmptyRow() {
        assertTrue(BatchBarcodeTextParser.parse("\r\n \n") is BatchBarcodeTextResult.Failure)
    }

    @Test
    fun appendReplacesOnlyTheInitialPlaceholderAndOtherwiseKeepsEveryExistingRow() {
        assertEquals(
            BatchBarcodeAppendResult.Success(listOf("001", "001")),
            BatchBarcodeTextParser.append(listOf(""), listOf("001", "001")),
        )
        assertEquals(
            BatchBarcodeAppendResult.Success(listOf("old", "", "new")),
            BatchBarcodeTextParser.append(listOf("old", ""), listOf("new")),
        )
    }

    @Test
    fun appendRefusesCapacityOverflowWithoutReturningPartialData() {
        assertEquals(
            BatchBarcodeAppendResult.CapacityExceeded,
            BatchBarcodeTextParser.append(listOf("old"), listOf("new", "extra"), maxRows = 2),
        )
    }
}
