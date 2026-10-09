package com.luckyalanzhou.barcodegenerator.ui.dialogs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class DropdownMorphTest {
    private val source = Rect(120f, 0f, 200f, 40f)
    private val target = Rect(0f, 40f, 200f, 200f)

    @Test fun startsAtSourceAndEndsAtFullMenuWithoutOvershoot() {
        assertEquals(source, dropdownMorphBounds(source, target, 0f))
        assertEquals(target, dropdownMorphBounds(source, target, 1f))
        assertEquals(target, dropdownMorphBounds(source, target, 1.2f))
        assertEquals(source, dropdownMorphBounds(source, target, Float.NaN))
    }

    @Test fun intermediateBoundsGrowContinuouslyAndKeepRightEdge() {
        assertEquals(Rect(60f, 20f, 200f, 120f), dropdownMorphBounds(source, target, .5f))
        var previousWidth = 0f
        for (step in 0..100) {
            val bounds = dropdownMorphBounds(source, target, step / 100f)
            assertTrue(bounds.width >= previousWidth)
            assertEquals(200f, bounds.right, 0f)
            previousWidth = bounds.width
        }
    }

    @Test fun contentDoesNotAppearBeforeSurfaceHasGrown() {
        assertEquals(0f, dropdownContentAlpha(.35f), 0f)
        assertEquals(1f, dropdownContentAlpha(1f), 0f)
        assertEquals(0f, dropdownContentAlpha(Float.NaN), 0f)
    }

    @Test fun visualSourceExcludesTouchPaddingButNeverExceedsAnchor() {
        assertEquals(Rect(120f, 3f, 200f, 37f), dropdownVisualSource(source, 34f))
        assertEquals(source, dropdownVisualSource(source, 48f))
        assertEquals(source, dropdownVisualSource(source, Float.NaN))
    }

    @Test fun sourceAndPanelShareWindowWhenOpeningAboveOrBelow() {
        val provider = CornerMenuPositionProvider()
        val window = IntSize(400, 800)
        val size = IntSize(200, 200)
        provider.calculatePosition(IntRect(260, 60, 340, 100), window, LayoutDirection.Ltr, size)
        assertEquals(source, provider.sourceInPopup)
        provider.calculatePosition(IntRect(20, 740, 100, 780), window, LayoutDirection.Ltr, size)
        assertTrue(provider.above)
        assertEquals(Rect(20f, 160f, 100f, 200f), provider.sourceInPopup)
        // 同样的布局重复测量不应漂移或反复改变方向。
        assertEquals(IntOffset(0, 580), provider.calculatePosition(
            IntRect(20, 740, 100, 780), window, LayoutDirection.Ltr, size))
    }
}
