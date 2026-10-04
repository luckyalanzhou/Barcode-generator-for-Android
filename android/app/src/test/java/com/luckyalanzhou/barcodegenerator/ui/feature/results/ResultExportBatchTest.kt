package com.luckyalanzhou.barcodegenerator.ui.feature.results

import org.junit.Assert.*
import org.junit.Test

class ResultExportBatchTest {
    @Test fun everyItemIsExportedInOriginalOrder() {
        assertEquals(listOf("image3", "image1", "image2"),
            completeExportBatch(listOf(3, 1, 2)) { "image$it" })
    }

    @Test fun oneFailedImageRejectsTheWholeBatch() {
        assertNull(completeExportBatch(listOf(1, 2, 3)) { if (it == 2) null else "image$it" })
    }

    @Test fun emptyResultsDoNotProduceAnEmptyExport() {
        assertNull(completeExportBatch(emptyList<Int>()) { "image$it" })
    }
}
