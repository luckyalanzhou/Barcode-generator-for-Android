package com.luckyalanzhou.barcodegenerator.ui.feature.results

import com.luckyalanzhou.barcodegenerator.ui.component.glass.roundActionGlassFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultActionGlassFrameTest {
    @Test fun staticOpticsKeepTheSameDpAcrossDensities() {
        for (density in listOf(1f, 2f, 3f, 4f)) {
            val frame = roundActionGlassFrame(48f * density, 48f * density, density)
            assertEquals(.55f, frame.refractionPx / density, .001f)
            assertEquals(0f, frame.motion, 0f)
            assertEquals(0f, frame.travelStrength, 0f)
        }
    }
    @Test
    fun idleGlassStaysQuietAndCentered() {
        val frame = roundActionGlassFrame(144f, 144f, 3f)

        assertEquals(72f, frame.centerX, .001f)
        assertEquals(72f, frame.centerY, .001f)
        assertEquals(0f, frame.motion, .001f)
        assertEquals(0f, frame.travelStrength, .001f)
    }

    @Test
    fun repeatedFramesDoNotIntroduceMovement() {
        val idle = roundActionGlassFrame(144f, 144f, 3f)
        repeat(100) {
            assertEquals(idle, roundActionGlassFrame(144f, 144f, 3f))
        }
        assertEquals(idle.centerX, idle.touchX, 0f)
        assertEquals(idle.centerY, idle.touchY, 0f)
    }

    @Test
    fun invalidAndOutOfBoundsValuesAreClamped() {
        val frame = roundActionGlassFrame(Float.NaN, -1f, 0f)

        assertEquals(1f, frame.width, .001f)
        assertEquals(1f, frame.height, .001f)
        assertEquals(0f, frame.motion, .001f)
        assertEquals(.5f, frame.touchX, .001f)
        assertEquals(.5f, frame.touchY, .001f)
        assertEquals(.1f, frame.density, .001f)
    }
}
