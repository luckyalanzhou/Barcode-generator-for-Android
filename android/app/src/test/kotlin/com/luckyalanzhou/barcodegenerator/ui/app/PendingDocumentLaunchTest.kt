package com.luckyalanzhou.barcodegenerator.ui.app

import java.io.File
import com.luckyalanzhou.barcodegenerator.ui.feature.results.ResultExportAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDocumentLaunchTest {
    @Test
    fun exportRemainsBusyWhileDocumentPickerOwnsTheTemporaryFile() {
        val file = File.createTempFile("result-export-test-", ".png")
        var activeAction: ResultExportAction? = ResultExportAction.Save
        try {
            finishResultExportPreparation(file) { activeAction = null }

            assertEquals(ResultExportAction.Save, activeAction)

            finishResultExportPreparation(null) { activeAction = null }
            assertNull(activeAction)
        } finally {
            file.delete()
        }
    }

    @Test
    fun completingDocumentRequestDeletesTemporaryFileAndReleasesExport() {
        val file = File.createTempFile("result-export-test-", ".png")
        var activeAction: ResultExportAction? = ResultExportAction.Save

        finishPendingResultImageDocument(file) { activeAction = null }

        assertFalse(file.exists())
        assertNull(activeAction)
    }

    @Test
    fun restoringPendingDocumentPickerRestoresBusyAction() {
        var activeAction: ResultExportAction? = null

        restorePendingResultExportAction(hasPendingDocumentFile = true) { activeAction = it }

        assertEquals(ResultExportAction.Save, activeAction)
    }

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
