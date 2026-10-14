package com.luckyalanzhou.barcodegenerator.ui.component.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassRenderDiagnosticsTest {
    @Test fun allFallbackReasonsAndReadyPathAreExplicit() {
        fun reason(api: Int = 33, hardware: Boolean = true, ready: Boolean = true,
            frame: Boolean = true, shader: Boolean = true, opaque: Boolean = false,
            cross: Boolean = false, width: Int = 144) =
            glassRenderReason(api, hardware, ready, frame, shader, opaque, cross, width, 144)
        assertEquals("gpu_selected", reason())
        assertEquals("opaque_accessibility", reason(opaque = true))
        assertEquals("fallback_api", reason(api = 32))
        assertEquals("fallback_hardware", reason(hardware = false))
        assertEquals("fallback_shader", reason(shader = false))
        assertEquals("fallback_source", reason(ready = false))
        assertEquals("fallback_first_frame", reason(frame = false, cross = true))
        assertEquals("waiting_layout", reason(width = 0))
        assertEquals("gpu_selected", reason(frame = false))
    }
}
