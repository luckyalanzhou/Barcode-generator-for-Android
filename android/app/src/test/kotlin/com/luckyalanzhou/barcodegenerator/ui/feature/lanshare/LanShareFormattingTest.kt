package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareFormattingTest {
    @Test
    fun nonEmptySubKilobyteFilesShowTheirByteCount() {
        assertEquals("33 B", formatLanShareSize(33L))
    }

    @Test
    fun emptyFilesShowZeroBytes() {
        assertEquals("0 B", formatLanShareSize(0L))
    }

    @Test
    fun unknownFileSizeDoesNotShowANegativeByteCount() {
        assertEquals("大小未知", formatLanShareSize(-1L))
    }
}
