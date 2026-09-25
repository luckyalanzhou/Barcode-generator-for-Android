package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareAccessControl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareAccessControlTest {
    private val policy = LanShareAccessControl("0123456789abcdefghijAB")

    @Test
    fun acceptsOnlyMatchingQueryOrHeader() {
        assertTrue(policy.allows("0123456789abcdefghijAB", null))
        assertTrue(policy.allows(null, "0123456789abcdefghijAB"))
        assertFalse(policy.allows(null, null))
        assertFalse(policy.allows("0123456789abcdefghijAC", null))
        assertFalse(policy.allows("0123456789abcdefghijABextra", null))
    }
}
