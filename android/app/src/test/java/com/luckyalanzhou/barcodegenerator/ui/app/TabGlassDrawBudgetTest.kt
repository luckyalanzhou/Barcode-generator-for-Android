package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class TabGlassDrawBudgetTest {
    @Test fun normalPhoneNavigationUsesSmallTwoInputSurface() {
        assertEquals(IntSize(2160, 200), tabForegroundAtlasSize(IntSize(1080, 200)))
        assertEquals(IntSize(2880, 300), tabForegroundAtlasSize(IntSize(1440, 300)))
    }

    @Test fun invalidOrOverflowingDimensionsUseCrispFallback() {
        for (size in listOf(IntSize.Zero, IntSize(-1, 200), IntSize(200, -1),
            IntSize(Int.MAX_VALUE, Int.MAX_VALUE), IntSize(4097, 1), IntSize(1, 8193))) {
            assertNull(tabForegroundAtlasSize(size))
        }
    }

    @Test fun pixelBudgetBoundaryIsInclusive() {
        assertEquals(IntSize(8192, 512), tabForegroundAtlasSize(IntSize(4096, 512)))
        assertNull(tabForegroundAtlasSize(IntSize(4096, 513)))
    }
}
