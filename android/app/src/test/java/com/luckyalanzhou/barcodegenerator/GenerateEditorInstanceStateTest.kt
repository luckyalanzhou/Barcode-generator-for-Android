package com.luckyalanzhou.barcodegenerator

import android.os.Bundle
import android.app.Application
import com.luckyalanzhou.barcodegenerator.presentation.GenerateEditorState
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.ui.app.GenerateEditorInstanceState
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GenerateEditorInstanceStateTest {
    @Test
    fun restoresInputExactlyIncludingWhitespaceBlankRowsAndLeadingZeros() {
        val saved = Bundle()
        val expected = listOf(" 00123 ", "", "零件-04") to "QR Code"
        assertTrue(GenerateEditorInstanceState.save(GenerateEditorState(expected.first, formatName = expected.second), saved))

        var restored: Pair<List<String>, String>? = null
        assertTrue(GenerateEditorInstanceState.restore(saved) { inputs, format -> restored = inputs to format })
        assertEquals(expected, restored)
    }

    @Test
    fun refusesOversizedDraftInsteadOfAddingItToActivityState() {
        val saved = Bundle()
        val tooLarge = listOf("x".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES + 1))

        assertFalse(GenerateEditorInstanceState.save(GenerateEditorState(tooLarge), saved))
        assertFalse(GenerateEditorInstanceState.restore(saved) { _, _ -> error("must not restore") })
    }

    @Test
    fun missingOrIncompleteInstanceStateDoesNotReplaceEditorState() {
        assertFalse(GenerateEditorInstanceState.restore(null) { _, _ -> error("must not restore") })
        assertFalse(GenerateEditorInstanceState.restore(Bundle().apply { putString("generate.formatName", "QR Code") }) { _, _ ->
            error("must not restore")
        })
    }

    @Test
    fun configurationRecreationRestoresDraftAndSelectedFormatIntoANewEditor() {
        val beforeRecreation = GenerateEditorStateHolder()
        beforeRecreation.updateDraft(listOf("00123", " second "))
        beforeRecreation.updateFormat("QR Code")
        val savedState = Bundle()
        assertTrue(GenerateEditorInstanceState.save(beforeRecreation.state.value, savedState))

        val afterRecreation = GenerateEditorStateHolder()
        assertTrue(GenerateEditorInstanceState.restore(savedState, afterRecreation::restoreDraft))

        assertEquals(listOf("00123", " second "), afterRecreation.state.value.inputDraft)
        assertEquals("QR Code", afterRecreation.state.value.formatName)
        assertEquals(null, afterRecreation.state.value.pendingFormat)
    }

    @Test
    fun resultEditDraftAndFormatSurviveRecreationWithoutRestoringTransientEditSignal() {
        val beforeRecreation = GenerateEditorStateHolder()
        beforeRecreation.beginEditing(listOf("edit-01", "edit-02"), "Code 39")
        val savedState = Bundle()
        assertTrue(GenerateEditorInstanceState.save(beforeRecreation.state.value, savedState))

        val afterRecreation = GenerateEditorStateHolder()
        assertTrue(GenerateEditorInstanceState.restore(savedState, afterRecreation::restoreDraft))

        assertEquals(listOf("edit-01", "edit-02"), afterRecreation.state.value.inputDraft)
        assertEquals("Code 39", afterRecreation.state.value.formatName)
        assertEquals(null, afterRecreation.state.value.pendingFormat)
    }

    @Test
    fun explicitlyClearedDraftRestoresAsOneEmptyInputRow() {
        val saved = Bundle()
        assertTrue(GenerateEditorInstanceState.save(GenerateEditorState(listOf(""), formatName = "EAN-13"), saved))

        var restored: GenerateEditorState? = null
        assertTrue(GenerateEditorInstanceState.restore(saved) { inputs, format ->
            restored = GenerateEditorState(inputs, formatName = format)
        })

        assertEquals(listOf(""), restored?.inputDraft)
        assertEquals("EAN-13", restored?.formatName)
    }
}
