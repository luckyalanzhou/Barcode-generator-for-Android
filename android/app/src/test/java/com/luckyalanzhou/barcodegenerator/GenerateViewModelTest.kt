package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateViewModelTest {
    @Test
    fun generationUsesCurrentDraftAndFormatAndKeepsEditorStateInSync() {
        val editor = GenerateEditorStateHolder()
        val viewModel = GenerateViewModel(editor, GenerateBarcodesUseCase())

        val result = viewModel.generate(listOf(" A B "), "Code 128-B", emptyList())

        assertTrue(result.isValid)
        assertEquals(listOf(" A B "), result.items.map { it.text })
        assertEquals(listOf(" A B "), viewModel.uiState.value.inputDraft)
        assertEquals("Code 128-B", viewModel.uiState.value.formatName)
    }
}
