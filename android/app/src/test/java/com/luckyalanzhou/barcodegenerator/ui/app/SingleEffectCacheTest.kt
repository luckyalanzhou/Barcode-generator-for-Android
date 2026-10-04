package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.*
import org.junit.Test

class SingleEffectCacheTest {
    @Test fun stableParametersReuseExactlyOneEffect() {
        val cache = SingleEffectCache<Float, Any>()
        var creations = 0
        val effect = cache.get(4.5f) { creations++; Any() }
        repeat(120) { assertSame(effect, cache.get(4.5f) { creations++; Any() }) }
        assertEquals(1, creations)
    }

    @Test fun changedBlurOrDensityReplacesEffectWithoutRetainingAllPastValues() {
        val cache = SingleEffectCache<Float, Any>()
        val first = cache.get(4.5f) { Any() }
        val second = cache.get(6f) { Any() }
        assertNotSame(first, second)
        assertSame(second, cache.get(6f) { fail("Unexpected recreation"); Any() })
        assertNotSame(first, cache.get(4.5f) { Any() })
    }
}
