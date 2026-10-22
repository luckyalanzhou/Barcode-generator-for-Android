package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.domain.UPDATE_FAILURE_SUMMARY
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkInstallFailureReportingTest {
    @Test
    fun persistsGenericFailureWhileKeepingDetailedUserMessage() {
        val sensitiveDetail = "Provider rejected content://private/path/update.apk"
        val persistedErrors = mutableListOf<String>()
        val shownMessages = mutableListOf<String>()

        reportApkInstallFailure(
            error = IllegalStateException(sensitiveDetail),
            recordUpdateError = persistedErrors::add,
            showToast = shownMessages::add,
        )

        assertEquals(listOf(UPDATE_FAILURE_SUMMARY), persistedErrors)
        assertEquals(listOf("安装失败：$sensitiveDetail"), shownMessages)
    }
}
