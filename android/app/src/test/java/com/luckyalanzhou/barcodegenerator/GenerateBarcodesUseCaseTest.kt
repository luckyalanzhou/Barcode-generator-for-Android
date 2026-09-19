package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
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
}
