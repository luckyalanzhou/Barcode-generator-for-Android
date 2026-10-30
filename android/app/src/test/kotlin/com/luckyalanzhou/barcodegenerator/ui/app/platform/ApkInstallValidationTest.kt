package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
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
    fun cleanupReusingValidationFailureDoesNotMaskOriginalFailure() {
        val validationError = SecurityException("无法校验 APK")
        val file = object : File(createApkFile().absolutePath) {
            override fun delete(): Boolean = throw validationError
        }
        var caught: SecurityException? = null

        try {
            validateDownloadedApkForInstall(file) { throw validationError }
        } catch (error: SecurityException) {
            caught = error
        }

        assertSame(validationError, caught)
        assertEquals(0, validationError.suppressed.size)
    }

    @Test
    fun fatalCleanupErrorTakesPriorityOverOrdinaryValidationFailure() {
        val validationError = IllegalArgumentException("APK 签名不匹配")
        val cleanupError = OutOfMemoryError("删除拒绝的 APK 时内存不足")
        val file = object : File(createApkFile().absolutePath) {
            override fun delete(): Boolean = throw cleanupError
        }

        val caught = runCatching {
            validateDownloadedApkForInstall(file) { throw validationError }
        }.exceptionOrNull()

        assertSame(cleanupError, caught)
        assertEquals(listOf(validationError), cleanupError.suppressed.toList())
    }

    @Test
    fun fatalValidationErrorRemainsPrimaryWhenCleanupAlsoFailsFatally() {
        val validationError = AssertionError("APK 校验状态异常")
        val cleanupError = OutOfMemoryError("清理 APK 时内存不足")
        val file = object : File(createApkFile().absolutePath) {
            override fun delete(): Boolean = throw cleanupError
        }

        val caught = runCatching {
            validateDownloadedApkForInstall(file) { throw validationError }
        }.exceptionOrNull()

        assertSame(validationError, caught)
        assertEquals(listOf(cleanupError), validationError.suppressed.toList())
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
