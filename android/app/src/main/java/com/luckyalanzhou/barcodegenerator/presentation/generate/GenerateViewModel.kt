package com.luckyalanzhou.barcodegenerator.presentation.generate

import com.luckyalanzhou.barcodegenerator.presentation.*

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase

/** Owns generate-screen input and barcode validation/generation. */
@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val editor: GenerateEditorStateHolder,
    private val generateBarcodesUseCase: GenerateBarcodesUseCase,
) : ViewModel() {
    val uiState: StateFlow<GenerateEditorState> = editor.state
    fun updateDraft(values: List<String>) = editor.updateDraft(values)
    fun updateFormat(format: String) = editor.updateFormat(format)
    fun clearPendingFormat() = editor.clearPendingFormat()

    fun generate(values: List<String>, format: String, existingItems: List<CodeItem>): GenerateBarcodesUseCase.Output {
        editor.updateDraft(values)
        editor.updateFormat(format)
        return generateBarcodesUseCase.execute(values, format, existingItems)
    }
}
