package com.luckyalanzhou.barcodegenerator.presentation.generate

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.presentation.*

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Generate-screen state/actions; business generation remains coordinated by BarcodeViewModel. */
@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val editor: GenerateEditorStateHolder,
) : ViewModel() {
    val uiState: StateFlow<GenerateEditorState> = editor.state
    fun updateDraft(values: List<String>) = editor.updateDraft(values)
    fun updateFormat(format: String) = editor.updateFormat(format)
    fun clearPendingFormat() = editor.clearPendingFormat()
}
