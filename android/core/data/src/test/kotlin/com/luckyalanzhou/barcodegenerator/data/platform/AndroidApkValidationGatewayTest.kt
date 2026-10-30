package com.luckyalanzhou.barcodegenerator.data.platform

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.shadow.api.Shadow
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSigningInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class AndroidApkValidationGatewayLegacyApiTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validatesArchiveAndInstalledSignaturesUsingLegacyPackageInfoFields() {
        val context: Context = RuntimeEnvironment.getApplication()
        val signer = Signature(byteArrayOf(1, 2, 3))
        val installed = PackageInfo().apply {
            packageName = context.packageName
            versionCode = 11
            signatures = arrayOf(signer)
        }
        val archive = PackageInfo().apply {
            packageName = context.packageName
            versionCode = 12
            signatures = arrayOf(signer)
        }
        val apk = temporaryFolder.newFile("update.apk")
        val packageManager = Shadows.shadowOf(context.packageManager)
        packageManager.addPackage(installed)
        packageManager.setPackageArchiveInfo(apk.absolutePath, archive)

        AndroidApkValidationGateway(context, currentVersionCode = 11L).validate(apk)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidApkValidationGatewayModernApiTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validatesArchiveAndInstalledSignersUsingSigningInfo() {
        val context: Context = RuntimeEnvironment.getApplication()
        val signer = Signature(byteArrayOf(4, 5, 6))
        val installed = PackageInfo().apply {
            packageName = context.packageName
            longVersionCode = 21L
            signingInfo = signingInfo(signer)
        }
        val archive = PackageInfo().apply {
            packageName = context.packageName
            longVersionCode = 22L
            signingInfo = signingInfo(signer)
        }
        val apk = temporaryFolder.newFile("update.apk")
        val packageManager = Shadows.shadowOf(context.packageManager)
        packageManager.addPackage(installed)
        packageManager.setPackageArchiveInfo(apk.absolutePath, archive)

        assertEquals(
            signer.toCharsString(),
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?.signingInfo?.apkContentsSigners?.singleOrNull()?.toCharsString(),
        )
        assertEquals(
            signer.toCharsString(),
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.singleOrNull()?.toCharsString(),
        )

        AndroidApkValidationGateway(context, currentVersionCode = 21L).validate(apk)
    }

    @Test
    fun rejectsArchiveWhenSigningInfoIsMissing() {
        val context: Context = RuntimeEnvironment.getApplication()
        val signer = Signature(byteArrayOf(7, 8, 9))
        val installed = PackageInfo().apply {
            packageName = context.packageName
            longVersionCode = 31L
            signingInfo = signingInfo(signer)
        }
        val archive = PackageInfo().apply {
            packageName = context.packageName
            longVersionCode = 32L
        }
        val apk = temporaryFolder.newFile("unsigned-metadata.apk")
        val packageManager = Shadows.shadowOf(context.packageManager)
        packageManager.addPackage(installed)
        packageManager.setPackageArchiveInfo(apk.absolutePath, archive)

        assertThrows(IllegalStateException::class.java) {
            AndroidApkValidationGateway(context, currentVersionCode = 31L).validate(apk)
        }
    }
}

private fun signingInfo(signer: Signature): SigningInfo = SigningInfo().apply {
    Shadow.extract<ShadowSigningInfo>(this).apply {
        setSignatures(arrayOf(signer))
        setPastSigningCertificates(arrayOf(signer))
    }
}
