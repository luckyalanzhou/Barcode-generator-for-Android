package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.generate.OcrDraftResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateViewModelTest {
    @Test
    fun delayedOcrDoesNotReplaceDraftChangedAfterCaptureStarted() {
        val editor = GenerateEditorStateHolder()
        val viewModel = GenerateViewModel(editor, GenerateBarcodesUseCase())
        val capturedDraft = listOf("BEFORE-CAPTURE")
        viewModel.updateDraft(capturedDraft)

        viewModel.updateDraft(listOf("MANUAL-EDIT"))
        val applied = viewModel.applyOcrResultIfDraftUnchanged(
            expectedDraft = capturedDraft,
            recognizedLines = listOf("OCR-RESULT"),
        )

        assertEquals(OcrDraftResult.DraftChanged, applied)
        assertEquals(listOf("MANUAL-EDIT"), viewModel.uiState.value.inputDraft)
    }

    @Test
    fun ocrAppendsToTheDraftAndKeepsItWhenTheCombinedResultExceedsCapacity() {
        val viewModel = GenerateViewModel(GenerateEditorStateHolder(), GenerateBarcodesUseCase())
        val draft = List(99) { "manual-$it" }
        viewModel.updateDraft(draft)

        assertEquals(OcrDraftResult.CapacityExceeded, viewModel.applyOcrResultIfDraftUnchanged(draft, listOf("A", "B")))
        assertEquals(draft, viewModel.uiState.value.inputDraft)
        assertEquals(OcrDraftResult.Appended, viewModel.applyOcrResultIfDraftUnchanged(draft, listOf("A")))
        assertEquals(draft + "A", viewModel.uiState.value.inputDraft)
    }

    @Test
    fun generationErrorIdentifiesOriginalInputRowAndIsClearedByEditing() {
        val viewModel = GenerateViewModel(GenerateEditorStateHolder(), GenerateBarcodesUseCase())
        val invalid = viewModel.generate(listOf("4006381333931", "1234"), "EAN-13", emptyList())
        assertTrue(!invalid.isValid)
        assertEquals(1, viewModel.validationError.value?.rowIndex)
        assertEquals(invalid.error, viewModel.validationError.value?.reason)
        viewModel.updateDraft(listOf("4006381333931", "5901234123457"))
        assertEquals(null, viewModel.validationError.value)
        assertTrue(viewModel.generate(viewModel.uiState.value.inputDraft, "EAN-13", emptyList()).isValid)
    }

    @Test
    fun changingFormatClearsStaleValidationFeedback() {
        val viewModel = GenerateViewModel(GenerateEditorStateHolder(), GenerateBarcodesUseCase())
        viewModel.generate(listOf("ABC"), "EAN-13", emptyList())
        assertEquals(0, viewModel.validationError.value?.rowIndex)
        viewModel.updateFormat("Code 128-B")
        assertEquals(null, viewModel.validationError.value)
    }

    @Test
    fun resultEditingPublishesDraftAndFormatAsOneEditorSnapshot() {
        val editor = GenerateEditorStateHolder()

        editor.beginEditing(listOf("first", "second"), "QR Code")

        assertEquals(listOf("first", "second"), editor.state.value.inputDraft)
        assertEquals("QR Code", editor.state.value.pendingFormat)
        assertEquals("QR Code", editor.state.value.formatName)
    }

    @Test
    fun legacyFormatNamesAreSafeForTheGenerateEditor() {
        assertEquals("Code 128-B", canonicalBarcodeFormatName("CODE_128"))
        assertEquals("QR Code", canonicalBarcodeFormatName("qr_code"))
        assertEquals("Code 128-B", canonicalBarcodeFormatName(null))
        assertEquals(BarcodeFormatIds.CODE_128, barcodeFormatId("CODE_128"))
        assertEquals(BarcodeFormatIds.QR_CODE, barcodeFormatId("QR Code"))
        assertEquals(BarcodeFormatIds.QR_CODE, barcodeFormatOption("QR_CODE")?.id)
        assertEquals(null, barcodeFormatOption("unsupported-format"))
    }

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
