package com.luckyalanzhou.barcodegenerator.presentation.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeDecodeGenerationTest {
    @Test
    fun newerScanInvalidatesAnOlderDecodeBeforeItCanCommit() {
        val generation = BarcodeDecodeGeneration()
        val olderRequest = generation.begin()
        val newerRequest = generation.begin()

        assertFalse(generation.isCurrent(olderRequest))
        assertTrue(generation.isCurrent(newerRequest))
    }
}
