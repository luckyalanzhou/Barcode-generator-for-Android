package com.luckyalanzhou.barcodegenerator.ui.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeItemEditorDialogTest {
    @Test
    fun initialSelectionPreservesSupportedLegacyFormatAliases() {
        assertEquals("QR Code", itemEditorInitialFormat("QR_CODE"))
        assertEquals("Code 128-B", itemEditorInitialFormat("unsupported-format"))
    }
}
