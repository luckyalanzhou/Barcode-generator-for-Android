package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class MenuRevealContourTest {
    @Test fun tabRevealKeepsEveryCommandInsideAStableContour() {
        val size = Size(200f, 80f)
        for (p in listOf(0f, .1f, .5f, 1f)) {
            assertEquals(Rect(0f, 0f, 200f, 80f),
                menuRevealBounds(size, .3f, true, menuGlassReveal(p, true, false), preserveContour = true))
        }
    }

    @Test fun genericTabTitleIsOmittedButMeaningfulAndEditingTitlesRemain() {
        assertFalse(menuShowsTitle(true, "操作"))
        assertFalse(menuShowsTitle(true, ""))
        assertTrue(menuShowsTitle(true, "收藏备份"))
        assertTrue(menuShowsTitle(false, "编辑"))
    }
    @Test fun contourGrowsFromEitherAnchorWithoutChangingFinalBounds() {
        val size = Size(200f, 198f)
        for (above in listOf(false, true)) for (tab in listOf(false, true)) {
            var previous = Rect.Zero
            for (step in 0..100) {
                val bounds = menuRevealBounds(size, .3f, above, menuGlassReveal(step / 100f, tab, false))
                assertTrue(bounds.width >= previous.width)
                assertTrue(bounds.height >= previous.height)
                assertTrue(bounds.left >= 0f && bounds.right <= size.width)
                assertTrue(bounds.top >= 0f && bounds.bottom <= size.height)
                assertEquals(if (above) size.height else 0f, if (above) bounds.bottom else bounds.top, .0001f)
                previous = bounds
            }
            assertEquals(Rect(0f, 0f, 200f, 198f), previous)
        }
    }

    @Test fun reducedMotionImmediatelyUsesFinalContour() {
        val size = Size(200f, 198f)
        assertEquals(Rect(0f, 0f, 200f, 198f),
            menuRevealBounds(size, 1f, true, menuGlassReveal(.01f, true, true)))
    }
}
