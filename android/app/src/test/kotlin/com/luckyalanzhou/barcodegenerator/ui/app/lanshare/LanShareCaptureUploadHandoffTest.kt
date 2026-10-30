package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareCaptureUploadHandoffTest {
    @Test
    fun missingSessionDeletesCaptureAndReportsThatUploadCannotStart() {
        val temporaryPhoto = File.createTempFile("lan-capture-test-", ".jpg")
        var uploadSession: LanShareSession? = null
        var unavailableReported = false
        var cleanupFailedReported = false

        try {
            handoffCapturedLanPhotoUpload(
                session = null,
                temporaryFile = temporaryPhoto,
                upload = { uploadSession = it },
                sessionUnavailable = { unavailableReported = true },
                temporaryFileCleanupFailed = { cleanupFailedReported = true },
            )

            assertEquals(null, uploadSession)
            assertTrue(unavailableReported)
            assertFalse(cleanupFailedReported)
            assertFalse("consumed camera temp file must be removed when upload cannot start", temporaryPhoto.exists())
        } finally {
            temporaryPhoto.delete()
        }
    }

    @Test
    fun availableSessionStartsUploadAndLeavesCleanupToUploadLifecycle() {
        val temporaryPhoto = File.createTempFile("lan-capture-test-", ".jpg")
        val session = LanShareSession("http://192.168.1.2:1234")
        var uploadSession: LanShareSession? = null
        var unavailableReported = false
        var cleanupFailedReported = false

        try {
            handoffCapturedLanPhotoUpload(
                session = session,
                temporaryFile = temporaryPhoto,
                upload = { uploadSession = it },
                sessionUnavailable = { unavailableReported = true },
                temporaryFileCleanupFailed = { cleanupFailedReported = true },
            )

            assertEquals(session, uploadSession)
            assertFalse(unavailableReported)
            assertFalse(cleanupFailedReported)
            assertTrue("active upload owns temp-file cleanup", temporaryPhoto.exists())
        } finally {
            temporaryPhoto.delete()
        }
    }
}
