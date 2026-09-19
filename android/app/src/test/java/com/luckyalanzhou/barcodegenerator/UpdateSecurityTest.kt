package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.UpdateSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSecurityTest {
    @Test
    fun versionsCompareNumericallyAndTreatMissingPatchAsZero() {
        assertTrue(UpdateSecurity.compareVersions("1.10.0", "1.9.99") > 0)
        assertEquals(0, UpdateSecurity.compareVersions("1.2", "1.2.0"))
        assertTrue(UpdateSecurity.compareVersions("1.2.1", "1.2.0") > 0)
    }

    @Test
    fun malformedVersionsAreRejectedWithoutThrowing() {
        assertEquals(null, UpdateSecurity.parseVersion("1"))
        assertEquals(null, UpdateSecurity.parseVersion("1.x.0"))
        assertEquals(null, UpdateSecurity.parseVersion("1.2.3.4"))
    }

    @Test
    fun sha256AndStandardCheckDigitsRemainStable() {
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            UpdateSecurity.sha256("test"),
        )
        assertTrue(UpdateSecurity.isValidEan13("4006381333931"))
        assertFalse(UpdateSecurity.isValidEan13("4006381333932"))
    }
}
