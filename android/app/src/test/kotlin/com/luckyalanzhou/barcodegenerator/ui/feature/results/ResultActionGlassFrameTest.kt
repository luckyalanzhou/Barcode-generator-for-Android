package com.luckyalanzhou.barcodegenerator.ui.feature.results

import androidx.compose.ui.geometry.Offset
import com.luckyalanzhou.barcodegenerator.ui.component.glass.roundActionGlassFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultActionGlassFrameTest {
    @Test fun opticsRemainContinuousAndBoundedAcrossDensityAndActivity() {
        for (density in listOf(1f, 2f, 3f, 4f)) {
            var previous = 0f
            for (step in 0..100) {
                val frame = roundActionGlassFrame(48f * density, 48f * density, density, step / 100f, null)
                val refractionDp = frame.refractionPx / density
                assertTrue(refractionDp in .549f..1.201f)
                assertTrue(refractionDp >= previous)
                previous = refractionDp
            }
        }
    }
    @Test
    fun idleGlassStaysQuietAndCentered() {
        val frame = roundActionGlassFrame(144f, 144f, 3f, 0f, null)

        assertEquals(72f, frame.centerX, .001f)
        assertEquals(72f, frame.centerY, .001f)
        assertEquals(0f, frame.motion, .001f)
        assertEquals(0f, frame.travelStrength, .001f)
    }

    @Test
    fun pressedGlassTracksTouchAndIncreasesBoundedOptics() {
        val idle = roundActionGlassFrame(144f, 144f, 3f, 0f, null)
        val pressed = roundActionGlassFrame(144f, 144f, 3f, 1.4f, Offset(20f, 130f))

        assertEquals(1f, pressed.motion, .001f)
        assertEquals(1f, pressed.travelStrength, .001f)
        assertEquals(20f, pressed.touchX, .001f)
        assertEquals(130f, pressed.touchY, .001f)
        assertTrue(pressed.refractionPx > idle.refractionPx)
        assertTrue(pressed.refractionPx <= 7f)
    }

    @Test
    fun invalidAndOutOfBoundsValuesAreClamped() {
        val frame = roundActionGlassFrame(Float.NaN, -1f, 0f, Float.NaN, Offset(-5f, 500f))

        assertEquals(1f, frame.width, .001f)
        assertEquals(1f, frame.height, .001f)
        assertEquals(0f, frame.motion, .001f)
        assertEquals(0f, frame.touchX, .001f)
        assertEquals(1f, frame.touchY, .001f)
        assertEquals(.1f, frame.density, .001f)
    }
}
