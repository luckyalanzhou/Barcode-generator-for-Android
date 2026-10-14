package com.luckyalanzhou.barcodegenerator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSharePreviewKeyTest {
    @Test
    fun mapsUntrustedIdsToStableSingleSegmentFileNames() {
        val traversalId = "../../databases/barcode_generator.db"
        val key = lanSharePreviewCacheKey(traversalId)

        assertEquals(64, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(key, lanSharePreviewCacheKey(traversalId))
        assertNotEquals(key, lanSharePreviewCacheKey("../databases/barcode_generator.db"))
    }
}
