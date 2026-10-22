package com.luckyalanzhou.barcodegenerator.data.platform

import com.luckyalanzhou.barcodegenerator.domain.UpdateSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateApkAssetNameTest {
    @Test
    fun officialReleaseAssetNameIsRecognized() {
        assertTrue(isUpdateApkAssetName("BarcodeGeneratorOfficial0.0.1.apk", "android-v", "BarcodeGeneratorOfficial"))
    }

    @Test
    fun legacyOfficialReleaseAssetNameRemainsRecognized() {
        assertTrue(isUpdateApkAssetName("BarcodeGenerator0.0.1.apk", "android-v", "BarcodeGeneratorOfficial"))
    }

    @Test
    fun betaReleaseAssetNameUsesConfiguredPrefix() {
        assertTrue(isUpdateApkAssetName("BarcodeGeneratorBeta9.9.9.apk", "android-test-v", "BarcodeGeneratorBeta"))
    }

    @Test
    fun unrelatedAssetNameIsRejected() {
        assertFalse(isUpdateApkAssetName("notes-0.0.1.apk", "android-v", "BarcodeGeneratorOfficial"))
    }

    @Test
    fun officialUpdateTagSupportsUnboundedSemanticVersionParts() {
        assertEquals("1000000000.0.0", parseUpdateAppVersionTag("android-v1000000000.0.0", "android-v"))
        val lower = parseUpdateAppVersionTag("android-v10000000000000000000.0.0", "android-v")
        val higher = parseUpdateAppVersionTag("android-v10000000000000000001.0.0", "android-v")
        assertEquals(
            "100000000000000000000.0.0",
            parseUpdateAppVersionTag("android-v100000000000000000000.0.0", "android-v"),
        )
        assertTrue(UpdateSecurity.compareVersions(higher!!, lower!!) > 0)
        assertNull(parseUpdateAppVersionTag("android-v01.0.0", "android-v"))
    }

    @Test
    fun betaBuildSuffixRemainsSupportedButIsNotPartOfSemanticVersion() {
        assertEquals("9.9.9", parseUpdateAppVersionTag("android-test-v9.9.9.2100000000", "android-test-v"))
    }
}
