package com.luckyalanzhou.barcodegenerator.ui.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDocumentLaunchTest {
    @Test
    fun failedLaunchClearsPendingStateAndDeletesTemporaryFile() {
        val file = File.createTempFile("result-export-test-", ".png")
        var pendingFile: File? = null

        val launched = launchPendingDocumentFile(file, { pendingFile = it }) { false }

        assertFalse(launched)
        assertNull(pendingFile)
        assertFalse(file.exists())
    }

    @Test
    fun successfulLaunchKeepsPendingFileUntilResultArrives() {
        val file = File.createTempFile("result-export-test-", ".png")
        var pendingFile: File? = null
        try {
            val launched = launchPendingDocumentFile(file, { pendingFile = it }) { true }

            assertTrue(launched)
            assertSame(file, pendingFile)
            assertTrue(file.exists())
        } finally {
            file.delete()
        }
    }
}
