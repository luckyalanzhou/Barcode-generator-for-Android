package com.luckyalanzhou.barcodegenerator.data.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidUpdateVersionTagTest {
    @Test
    fun parsesUnboundedSemVerComponentsForOfficialAndBetaTags() {
        val version = "123456789012345678901234567890.100.1000"

        assertEquals(version, parseAppVersionTag("android-v$version", "android-v"))
        assertEquals(version, parseAppVersionTag("android-test-v$version", "android-test-v"))
    }

    @Test
    fun rejectsMismatchedPrefixAndNonCanonicalNumericParts() {
        assertNull(parseAppVersionTag("android-v01.2.3", "android-v"))
        assertNull(parseAppVersionTag("android-test-v1.2.3", "android-v"))
        assertNull(parseAppVersionTag("android-v1.2.3-rc.1", "android-v"))
    }
}
