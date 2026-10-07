package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareUploadingContentTest {
    @Test
    fun progressIsBoundedToTheFileSize() {
        assertEquals(0, LanShareUploadingContent("a", "a.bin", 100, -1, 0).progressPercent)
        assertEquals(100, LanShareUploadingContent("b", "b.bin", 100, 101, 0).progressPercent)
    }

    @Test
    fun unknownOrEmptySizeHasZeroProgress() {
        assertEquals(0, LanShareUploadingContent("a", "a.bin", 0, 0, 0).progressPercent)
        assertEquals(0, LanShareUploadingContent("b", "b.bin", -1, 0, 0).progressPercent)
    }
}
