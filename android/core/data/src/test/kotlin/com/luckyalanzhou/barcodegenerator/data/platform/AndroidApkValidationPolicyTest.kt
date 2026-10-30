package com.luckyalanzhou.barcodegenerator.data.platform

import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidApkValidationPolicyTest {
    private fun validate(
        packageName: String? = "com.example.app",
        versionCode: Long = 12L,
        downloadedSigners: List<String>? = listOf("current-cert"),
        installedSigners: List<String>? = listOf("current-cert"),
    ) = validateApkUpdateIdentity(
        expectedPackageName = "com.example.app",
        currentVersionCode = 11L,
        apkPackageName = packageName,
        apkVersionCode = versionCode,
        apkSigners = downloadedSigners,
        installedSigners = installedSigners,
    )

    @Test
    fun acceptsNewerApkWithMatchingSignerSetRegardlessOfOrder() {
        validate(
            downloadedSigners = listOf("signer-a", "signer-b"),
            installedSigners = listOf("signer-b", "signer-a"),
        )
    }

    @Test
    fun rejectsDifferentPackage() {
        assertThrows(IllegalStateException::class.java) {
            validate(packageName = "com.example.other")
        }
    }

    @Test
    fun rejectsSameOrOlderVersion() {
        assertThrows(IllegalStateException::class.java) { validate(versionCode = 11L) }
        assertThrows(IllegalStateException::class.java) { validate(versionCode = 10L) }
    }

    @Test
    fun rejectsMissingSignersOrDifferentSignerSet() {
        assertThrows(IllegalStateException::class.java) { validate(downloadedSigners = null) }
        assertThrows(IllegalStateException::class.java) { validate(downloadedSigners = emptyList()) }
        assertThrows(IllegalStateException::class.java) { validate(installedSigners = null) }
        assertThrows(IllegalStateException::class.java) { validate(installedSigners = emptyList()) }
        assertThrows(IllegalStateException::class.java) {
            validate(downloadedSigners = listOf("other-cert"))
        }
    }
}
