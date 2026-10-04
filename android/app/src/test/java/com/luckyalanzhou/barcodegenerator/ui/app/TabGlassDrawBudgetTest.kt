package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class TabGlassDrawBudgetTest {
    @Test fun fullInputSizeDoesNotChangeWhileLensMovesOrReverses() {
        val size = IntSize(1440, 200)
        for (step in (0..30) + (30 downTo 0)) {
            val frame = tabGlassFrame(1440f, 200f, 3f, 4, step / 10f, 1f, .03f, 1f)
            assertEquals(IntSize(2880, 200), tabForegroundAtlasSize(size))
            assertEquals(size.width.toFloat(), frame.width, 0f)
            assertTrue(frame.centerX - frame.halfWidth >= 0f)
            assertTrue(frame.centerX + frame.halfWidth <= frame.width)
        }
    }

    @Test fun normalPhoneNavigationFitsBoundedFullWidthSurface() {
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
