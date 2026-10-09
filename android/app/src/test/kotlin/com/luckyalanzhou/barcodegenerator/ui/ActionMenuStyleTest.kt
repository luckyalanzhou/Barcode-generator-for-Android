package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ActionMenuStyleTest {
    @Test fun normalWindowsKeepRequestedWidthAndSmallWindowsStayInsideEdges() {
        assertEquals(200f, actionMenuWidthDp(360f), .001f)
        assertEquals(200f, actionMenuWidthDp(800f), .001f)
        assertEquals(156f, actionMenuWidthDp(180f), .001f)
        assertEquals(1f, actionMenuWidthDp(20f), .001f)
    }

    @Test fun existingMenuGeometryIsPreserved() {
        assertEquals(12f, ActionMenuMetrics.corner.value, .001f)
        assertEquals(18f, ActionMenuMetrics.iconSize.value, .001f)
    }

    @Test fun titleKeepsPlaceholderColorAndHighContrastRemainsReadable() {
        for (dark in listOf(false, true)) {
            val colors = appColorScheme(dark)
            val normal = actionMenuColors(colors, dark, false)
            val accessible = actionMenuColors(colors, dark, true)
            assertEquals(colors.text.placeholder, normal.title)
            assertEquals(colors.text.primary, accessible.title)
            assertTrue(accessible.separator.alpha > normal.separator.alpha)
            assertTrue(accessible.outline.alpha > normal.outline.alpha)
            assertTrue(accessible.selection.alpha > normal.selection.alpha)
        }
    }
}
