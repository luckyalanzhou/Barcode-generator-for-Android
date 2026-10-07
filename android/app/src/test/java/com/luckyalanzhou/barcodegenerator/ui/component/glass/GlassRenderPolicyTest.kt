package com.luckyalanzhou.barcodegenerator.ui.component.glass

import org.junit.Assert.*
import org.junit.Test

class GlassRenderPolicyTest {
    @Test fun anyMissingPrerequisiteDisablesGpuOptics() {
        for (api in listOf(26, 31, 32, 33, 36)) {
            for (bits in 0 until 16) {
                val hardware = bits and 1 != 0
                val source = bits and 2 != 0
                val shader = bits and 4 != 0
                val opaque = bits and 8 != 0
                assertEquals(api >= 33 && hardware && source && shader && !opaque,
                    glassGpuAvailable(api, hardware, source, shader, opaque))
            }
        }
    }

    @Test fun highContrastAlwaysWinsOverAvailableHardware() {
        assertFalse(glassGpuAvailable(36, true, true, true, true))
        assertTrue(glassGpuAvailable(33, true, true, true, false))
    }
}
