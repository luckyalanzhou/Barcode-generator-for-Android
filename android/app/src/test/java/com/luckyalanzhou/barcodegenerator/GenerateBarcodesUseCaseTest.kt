package com.luckyalanzhou.barcodegenerator

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateBarcodesUseCaseTest {
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
}
