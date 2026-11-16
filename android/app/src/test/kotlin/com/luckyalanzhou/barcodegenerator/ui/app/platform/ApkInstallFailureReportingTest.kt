package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.domain.UPDATE_FAILURE_SUMMARY
import com.luckyalanzhou.barcodegenerator.domain.UpdateFailure
import com.luckyalanzhou.barcodegenerator.domain.UpdateValidationException
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkInstallFailureReportingTest {
    @Test
    fun persistsGenericFailureWithoutLeakingProviderDetailsToPresentation() {
        val sensitiveDetail = "Provider rejected content://private/path/update.apk"
        val persistedErrors = mutableListOf<String>()
        val shownFailures = mutableListOf<UpdateFailure?>()

        reportApkInstallFailure(
            error = IllegalStateException(sensitiveDetail),
            recordUpdateError = persistedErrors::add,
            showToast = shownFailures::add,
        )

        assertEquals(listOf(UPDATE_FAILURE_SUMMARY), persistedErrors)
        assertEquals(listOf(null), shownFailures)
    }

    @Test
    fun typedFailureIsPreservedForLocalizedPresentationWithoutPassingDiagnosticText() {
        val shownFailures = mutableListOf<UpdateFailure?>()
        reportApkInstallFailure(
            error = UpdateValidationException(UpdateFailure.InvalidApk, "content://private/update.apk"),
            recordUpdateError = {},
            showToast = shownFailures::add,
        )

        assertEquals(listOf(UpdateFailure.InvalidApk), shownFailures)
    }
}
