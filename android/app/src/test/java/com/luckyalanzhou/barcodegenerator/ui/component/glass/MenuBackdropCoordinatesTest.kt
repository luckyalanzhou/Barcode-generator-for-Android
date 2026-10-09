package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuBackdropCoordinatesTest {
    @Test fun popupSamplingUsesScreenOriginInsteadOfUnrelatedWindowOrigins() {
        assertEquals(Offset(-180f, -360f), backdropSampleOffset(
            Offset(0f, 24f), Offset(10f, 80f), Offset.Zero, Offset(190f, 440f), true))
    }

    @Test fun commonScreenTranslationDoesNotShiftSampling() {
        for (shift in listOf(Offset.Zero, Offset(100f, 200f), Offset(-10f, 30f))) {
            assertEquals(Offset(-180f, -360f), backdropSampleOffset(
                Offset.Zero, Offset(10f, 80f) + shift, Offset.Zero, Offset(190f, 440f) + shift, true))
        }
    }

    @Test fun existingControlWindowSamplingIsUnchanged() {
        assertEquals(Offset(10f, 30f), backdropSampleOffset(
            Offset(20f, 40f), Offset(200f, 400f), Offset(10f, 10f), Offset(500f, 800f), false))
    }
}
