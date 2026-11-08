package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.luckyalanzhou.barcodegenerator.ui.theme.appColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSharePreviewTransformTest {
    @Test fun zoomKeepsTheTappedImagePointStationary() {
        val result = transformLanSharePreview(LanSharePreviewTransform(), IntSize(1000, 1000),
            IntSize(1000, 1000), Offset(750f, 500f), zoom = 2f)
        assertEquals(2f, result.scale, 0f)
        assertEquals(Offset(-250f, 0f), result.offset)
    }

    @Test fun landscapeCannotPanVerticallyWhileItIsShorterThanViewport() {
        val result = transformLanSharePreview(LanSharePreviewTransform(), IntSize(1600, 1000),
            IntSize(1000, 1500), Offset(500f, 750f), Offset(10000f, 10000f), 2f)
        assertEquals(Offset(500f, 0f), result.offset)
    }

    @Test fun portraitPanningStopsAtImageEdges() {
        val result = transformLanSharePreview(LanSharePreviewTransform(), IntSize(900, 1800),
            IntSize(1000, 1500), Offset(500f, 750f), Offset(-10000f, -10000f), 2f)
        assertEquals(Offset(-250f, -750f), result.offset)
    }

    @Test fun zoomIsBoundedAndReturningToFitRecenters() {
        val max = transformLanSharePreview(LanSharePreviewTransform(), IntSize(1000, 1000),
            IntSize(1000, 1000), Offset.Zero, zoom = 100f)
        assertEquals(5f, max.scale, 0f)
        val fit = transformLanSharePreview(max, IntSize(1000, 1000),
            IntSize(1000, 1000), Offset.Zero, zoom = .01f)
        assertEquals(LanSharePreviewTransform(), fit)
    }

    @Test fun higherResolutionOfSameImageKeepsGeometry() {
        val current = LanSharePreviewTransform(2f, Offset(100f, 0f))
        fun next(size: IntSize) = transformLanSharePreview(current, size, IntSize(1000, 1500),
            Offset(600f, 600f), Offset(20f, 30f), 1.2f)
        assertEquals(next(IntSize(400, 250)), next(IntSize(1600, 1000)))
    }

    @Test fun invalidViewportDoesNotCorruptGestureState() {
        val current = LanSharePreviewTransform(2f, Offset(20f, 30f))
        assertEquals(current, transformLanSharePreview(current, IntSize(10, 10), IntSize.Zero,
            Offset.Zero, zoom = 2f))
    }

    @Test fun sentAttachmentTextHasReadableContrastInBothThemes() {
        for (dark in listOf(false, true)) {
            val background = lanShareSentBubbleColor(appColorScheme(dark).controls.primaryAction, dark)
            for (alpha in listOf(1f, .9f)) {
                val foreground = Color.White.copy(alpha = alpha).compositeOver(background)
                val ratio = (foreground.luminance() + .05f) / (background.luminance() + .05f)
                assertTrue("dark=$dark alpha=$alpha contrast=$ratio", ratio >= 4.5f)
            }
        }
    }
}
