package com.luckyalanzhou.barcodegenerator.data.platform

import org.junit.Assert.assertFalse
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
}
