package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkInstallValidationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validApkRemainsAvailableForSystemInstaller() {
        val file = createApkFile()

        validateDownloadedApkForInstall(file) { assertSame(file, it) }

        assertTrue(file.exists())
    }

    @Test
    fun rejectedApkIsDeletedAndValidationErrorIsPreserved() {
        val file = createApkFile()
        val validationError = IllegalArgumentException("签名不匹配")
        var caught: Exception? = null

        try {
            validateDownloadedApkForInstall(file) { throw validationError }
        } catch (error: Exception) {
            caught = error
        }

        assertSame(validationError, caught)
        assertFalse(file.exists())
    }

    @Test
    fun cancellationDeletesRejectedApkAndPropagates() {
        val file = createApkFile()
        val cancellation = CancellationException("更新界面已关闭")
        var caught: CancellationException? = null

        try {
            validateDownloadedApkForInstall(file) { throw cancellation }
        } catch (error: CancellationException) {
            caught = error
        }

        assertSame(cancellation, caught)
        assertFalse(file.exists())
    }

    @Test
    fun fatalErrorDeletesRejectedApkAndPropagates() {
        val file = createApkFile()
        val fatalError = AssertionError("校验状态异常")
        var caught: AssertionError? = null

        try {
            validateDownloadedApkForInstall(file) { throw fatalError }
        } catch (error: AssertionError) {
            caught = error
        }

        assertSame(fatalError, caught)
        assertFalse(file.exists())
    }

    private fun createApkFile(): File = temporaryFolder.newFile("update.apk")
}
