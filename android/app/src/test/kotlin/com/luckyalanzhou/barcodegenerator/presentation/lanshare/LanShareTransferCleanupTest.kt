package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import org.junit.Assert.*
import org.junit.Test

class LanShareTransferCleanupTest {
    @Test
    fun roomChangeClearsAllPendingTransferState() {
        val state = LanShareUiState(
            uploadingFiles = listOf(LanShareUploadingFile("old", "file", 100, 50, 1)),
            pendingUploadTempFile = java.io.File("old-camera.jpg"), pendingUploadName = "old-camera.jpg",
            pendingDownloadId = "old-download", browserConnected = true,
        ).withoutPendingTransfers()
        assertTrue(state.uploadingFiles.isEmpty())
        assertNull(state.pendingUploadTempFile)
        assertNull(state.pendingUploadName)
        assertNull(state.pendingDownloadId)
        assertTrue(state.browserConnected)
    }
}
