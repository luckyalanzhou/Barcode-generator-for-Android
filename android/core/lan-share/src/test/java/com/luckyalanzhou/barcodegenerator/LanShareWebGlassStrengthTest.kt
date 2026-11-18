package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebTemplates
import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebGlassProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareWebGlassStrengthTest {
    @Test
    fun existingFunInterfaceImplementationsKeepTheDefaultMidpointStrength() {
        val provider = LanShareWebGlassProvider { true }

        assertTrue(provider.isLiquidGlassEnabled())
        assertEquals(50, provider.getLiquidGlassStrengthPercent())
    }

    @Test
    fun zeroStrengthMakesLightAndDarkGlassSurfacesMoreTransparent() {
        val page = LanShareWebTemplates.page(glassStrengthPercent = 0)

        assertTrue(page.contains("--bubble-peer:rgba(255,255,255,.54)"))
        assertTrue(page.contains("--bubble-mine:rgba(10,132,255,.66)"))
        assertTrue(page.contains("--bubble-peer:rgba(44,44,46,.705)"))
        assertTrue(page.contains("--bubble-mine:rgba(10,132,255,.69)"))
    }

    @Test
    fun midpointStrengthPreservesExistingBrowserGlassOpacity() {
        val page = LanShareWebTemplates.page(glassStrengthPercent = 50)

        assertTrue(page.contains("--bubble-peer:rgba(255,255,255,.72)"))
        assertTrue(page.contains("--bubble-mine:rgba(10,132,255,.88)"))
        assertTrue(page.contains("--bubble-peer:rgba(44,44,46,.94)"))
        assertTrue(page.contains("--bubble-mine:rgba(10,132,255,.92)"))
        assertTrue(page.contains("background:rgba(255,255,255,.18)"))
    }

    @Test
    fun fullStrengthAddsTintToLightAndDarkGlassSurfaces() {
        val page = LanShareWebTemplates.page(glassStrengthPercent = 100)

        assertTrue(page.contains("--bubble-peer:rgba(255,255,255,.818)"))
        assertTrue(page.contains("--bubble-mine:rgba(10,132,255,.922)"))
        assertTrue(page.contains("--bubble-peer:rgba(44,44,46,.961)"))
        assertTrue(page.contains("--bubble-mine:rgba(10,132,255,.948)"))
    }

    @Test
    fun disabledGlassStillUsesSolidOverridesAtEveryStrength() {
        val page = LanShareWebTemplates.page(liquidGlassEnabled = false, glassStrengthPercent = 100)

        assertTrue(page.contains("li{backdrop-filter:none!important"))
        assertTrue(page.contains("li:not(.upload-progress){background:var(--bubble-peer-solid)!important}"))
        assertTrue(page.contains(".mine .download{background:#006fe0!important}"))
        assertTrue(page.indexOf("--bubble-peer:rgba(255,255,255,.818)") < page.indexOf("li:not(.upload-progress){background:var(--bubble-peer-solid)!important}"))
    }

    @Test
    fun strengthIsClampedAndEachTenPercentStepChangesTheMappedGlass() {
        val outputs = (0..100 step 10).map { strength ->
            LanShareWebTemplates.page(glassStrengthPercent = strength)
                .substringAfter("--bubble-peer:rgba(255,255,255,")
                .substringBefore(")")
        }

        assertTrue(outputs.zipWithNext().all { (previous, next) -> previous != next })
        assertTrue(LanShareWebTemplates.page(glassStrengthPercent = -10)
            .contains("--bubble-peer:rgba(255,255,255,.54)"))
        assertTrue(LanShareWebTemplates.page(glassStrengthPercent = 110)
            .contains("--bubble-peer:rgba(255,255,255,.818)"))
    }
}
