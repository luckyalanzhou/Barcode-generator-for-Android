package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.LanSharePreviewSize
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.fitLanSharePreviewSize
import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareImageSizingTest {
    @Test
    fun landscapePreviewFitsWithinBoundsWithoutCroppingItsRatio() {
        assertEquals(LanSharePreviewSize(220, 124), fitLanSharePreviewSize(1920, 1080))
    }

    @Test
    fun portraitPreviewFitsWithinBoundsWithoutCroppingItsRatio() {
        assertEquals(LanSharePreviewSize(101, 180), fitLanSharePreviewSize(1080, 1920))
    }

    @Test
    fun smallImageIsNotUpscaled() {
        assertEquals(LanSharePreviewSize(80, 40), fitLanSharePreviewSize(80, 40))
    }
}
