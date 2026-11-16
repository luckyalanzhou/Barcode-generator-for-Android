package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanShareFormattingTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources

    @Test
    @Config(qualifiers = "en")
    fun formatsSmallAndEmptyFiles() {
        assertEquals("33 B", resources.formatLanShareFileSize(33))
        assertEquals("0 B", resources.formatLanShareFileSize(0))
    }

    @Test
    @Config(qualifiers = "es")
    fun formatsDecimalSizeAndUnknownSizeUsingTheAppLocale() {
        assertEquals("1,5 MB", resources.formatLanShareFileSize(1_572_864))
        assertEquals("Tamaño desconocido", resources.formatLanShareFileSize(-1))
    }
}
