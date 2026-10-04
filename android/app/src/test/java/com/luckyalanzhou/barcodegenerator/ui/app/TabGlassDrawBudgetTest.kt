package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class TabGlassDrawBudgetTest {
    @Test fun movingCropContainsLensAtEveryTabAndReducesInputArea() {
        val size = IntSize(1440, 200)
        for (step in 0..30) {
            val frame = tabGlassFrame(1440f, 200f, 3f, 4, step / 10f, 1f, .03f, 1f)
            val region = requireNotNull(tabForegroundRegion(frame, size))
            assertTrue(region.left >= 0 && region.right <= size.width)
            assertTrue(region.left <= frame.centerX - frame.halfWidth)
            assertTrue(region.right >= frame.centerX + frame.halfWidth)
            assertTrue(region.size.width * 3 < size.width)
            val local = region.localFrame(frame)
            assertEquals(frame.centerX, local.centerX + region.left, .0001f)
            assertEquals(frame.touchX, local.touchX + region.left, .0001f)
        }
    }

    @Test fun invalidCropUsesOriginalForeground() {
        val frame = tabGlassFrame(1440f, 200f, 3f, 4, 0f, 1f, 0f, 1f)
        assertNull(tabForegroundRegion(frame.copy(centerX = Float.NaN), IntSize(1440, 200)))
        assertNull(tabForegroundRegion(frame.copy(density = Float.POSITIVE_INFINITY), IntSize(1440, 200)))
        assertNull(tabForegroundRegion(frame, IntSize.Zero))
    }
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
