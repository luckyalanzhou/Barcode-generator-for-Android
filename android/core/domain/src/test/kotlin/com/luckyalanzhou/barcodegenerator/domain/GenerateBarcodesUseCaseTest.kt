package com.luckyalanzhou.barcodegenerator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateBarcodesUseCaseTest {
    @Test
    fun eachGenerationGetsItsOwnStableBatchId() {
        val useCase = GenerateBarcodesUseCase()
        val first = useCase.execute(listOf("A", "B"), "Code 128-B", emptyList(), now = 42L).items
        val second = useCase.execute(listOf("C"), "Code 128-B", first, now = 42L).items

        assertEquals(1, first.map { it.historyBatchId }.distinct().size)
        assertTrue(first.first().historyBatchId.isNotBlank())
        assertTrue(second.single().historyBatchId.isNotBlank())
        assertTrue(first.first().historyBatchId != second.single().historyBatchId)
    }

    @Test
    fun generateKeepsBarcodeTextSpaces() {
        val result = GenerateBarcodesUseCase().execute(
            input = listOf(" A B ", "   "),
            format = "Code 128-B",
            existingItems = emptyList(),
            now = 1L,
        )

        assertEquals(true, result.isValid)
        assertEquals(listOf(" A B "), result.items.map { it.text })
    }

    @Test
    fun validationErrorPointsToOriginalInputLineAfterBlankLines() {
        val result = GenerateBarcodesUseCase().execute(
            input = listOf("", "  ", "invalid"),
            format = "EAN-13",
            existingItems = emptyList(),
            now = 1L,
        )

        assertEquals(2, result.errorIndex)
        assertEquals(false, result.isValid)
    }

    @Test
    fun code128RejectsControlCharactersInsteadOfCreatingUnrenderableResult() {
        val result = GenerateBarcodesUseCase().execute(
            input = listOf("ABC\u0001XYZ"),
            format = "Code 128-B",
            existingItems = emptyList(),
            now = 1L,
        )

        assertEquals(false, result.isValid)
        assertEquals(0, result.errorIndex)
    }

    @Test
    fun generateRejectsLegacyDraftsAboveCurrentInputRowLimit() {
        val result = GenerateBarcodesUseCase().execute(
            input = List(MAX_GENERATE_INPUT_ROWS + 1) { "A" },
            format = "Code 128-B",
            existingItems = emptyList(),
            now = 1L,
        )

        assertEquals(false, result.isValid)
        assertEquals(MAX_GENERATE_INPUT_ROWS, result.errorIndex)
        assertEquals("最多支持 $MAX_GENERATE_INPUT_ROWS 行输入", result.errorMessage)
    }
}
