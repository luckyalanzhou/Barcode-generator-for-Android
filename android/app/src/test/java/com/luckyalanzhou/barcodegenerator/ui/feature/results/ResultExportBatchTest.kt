package com.luckyalanzhou.barcodegenerator.ui.feature.results

import org.junit.Assert.*
import org.junit.Test

class ResultExportBatchTest {
    @Test fun everyItemIsConsumedInOriginalOrderAndReleased() {
        val consumed = mutableListOf<String>()
        val released = mutableListOf<String>()

        val completed = consumeCompleteExportBatch(
            items = listOf(3, 1, 2),
            render = { "image$it" },
            consume = consumed::add,
            release = released::add,
        )

        assertTrue(completed)
        assertEquals(listOf("image3", "image1", "image2"), consumed)
        assertEquals(consumed, released)
    }

    @Test fun aFailedImageStopsTheBatchAfterReleasingCompletedResources() {
        val consumed = mutableListOf<String>()
        val released = mutableListOf<String>()

        val completed = consumeCompleteExportBatch(
            items = listOf(1, 2, 3),
            render = { if (it == 2) null else "image$it" },
            consume = consumed::add,
            release = released::add,
        )

        assertFalse(completed)
        assertEquals(listOf("image1"), consumed)
        assertEquals(listOf("image1"), released)
    }

    @Test fun aConsumerFailureStillReleasesItsCurrentResource() {
        val released = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            consumeCompleteExportBatch(
                items = listOf(1, 2),
                render = { "image$it" },
                consume = { error("draw failed") },
                release = released::add,
            )
        }

        assertEquals(listOf("image1"), released)
    }

    @Test fun emptyResultsDoNotProduceAnEmptyExport() {
        assertFalse(consumeCompleteExportBatch(emptyList<Int>(), { "image$it" }, {}, {}))
    }

    @Test fun imageDimensionsAreRejectedBeforeOverflowOrUnboundedAllocation() {
        assertEquals(
            ResultExportDimensions(width = 100, height = 230),
            resultExportDimensions(listOf(100, 80), listOf(100, 120), spacing = 2, outerPadding = 4),
        )
        assertNull(resultExportDimensions(listOf(Int.MAX_VALUE, 1), listOf(1, Int.MAX_VALUE), 0, 0))
        assertNull(resultExportDimensions(listOf(5_000, 5_000), listOf(5_000, 5_000), 0, 0))
    }
}
