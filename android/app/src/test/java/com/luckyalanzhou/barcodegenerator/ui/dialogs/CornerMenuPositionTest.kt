package com.luckyalanzhou.barcodegenerator.ui.dialogs

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test

class CornerMenuPositionTest {
    @Test fun alignsToButtonRightAndOpensBelow() {
        val provider = CornerMenuPositionProvider()
        assertEquals(IntOffset(140, 60), provider.calculatePosition(IntRect(260, 60, 340, 100),
            IntSize(400, 800), LayoutDirection.Ltr, IntSize(200, 200)))
        assertFalse(provider.above)
    }
    @Test fun fallsBackAboveAndKeepsPanelInWindow() {
        val provider = CornerMenuPositionProvider()
        assertEquals(IntOffset(0, 580), provider.calculatePosition(IntRect(20, 740, 100, 780),
            IntSize(400, 800), LayoutDirection.Ltr, IntSize(200, 200)))
        assertTrue(provider.above)
    }
}
