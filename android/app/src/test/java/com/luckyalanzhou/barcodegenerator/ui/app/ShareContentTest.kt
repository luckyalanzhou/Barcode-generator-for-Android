package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.*
import org.junit.Test

class ShareContentTest {
    @Test fun readableChineseTitleIsPreserved() {
        assertEquals("本页生成的 20 个条码.png", shareImageFileName("本页生成的 20 个条码"))
    }

    @Test fun pathAndControlCharactersCannotEscapeTheCacheDirectory() {
        val name = shareImageFileName("../../folder\\name:\n?*")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains('\n'))
        assertFalse(name.startsWith('.'))
        assertTrue(name.endsWith(".png"))
    }

    @Test fun blankAndLongNamesHaveBoundedFallbacks() {
        assertEquals("barcode.png", shareImageFileName("  ...  "))
        assertEquals(68, shareImageFileName("a".repeat(1000)).length)
    }
}
