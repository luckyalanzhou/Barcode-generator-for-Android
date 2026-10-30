package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanShareDownloadRollbackTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removesNewDocumentWhenWritingDownloadedFileFails() {
        val temporaryApk = temporaryFolder.newFile("download.part")
        temporaryApk.writeBytes(byteArrayOf(1, 2, 3))
        val destination = temporaryFolder.newFile("created-document.bin")
        val diskFull = IOException("disk full")

        val thrown = try {
            withLanShareDownloadRollback(rollback = { destination.delete() }) {
                try {
                    destination.outputStream().use { output ->
                        output.write(byteArrayOf(1, 2))
                        throw diskFull
                    }
                } finally {
                    temporaryApk.delete()
                }
            }
            error("Expected the destination write to fail")
        } catch (error: IOException) {
            error
        }

        assertSame(diskFull, thrown)
        assertFalse(destination.exists())
        assertFalse(temporaryApk.exists())
    }

    @Test
    fun keepsOriginalTransferErrorWhenDestinationCleanupFails() {
        val transferFailure = IOException("destination write failed")
        val cleanupFailure = SecurityException("provider denied delete")

        val thrown = try {
            withLanShareDownloadRollback(rollback = { throw cleanupFailure }) {
                throw transferFailure
            }
            error("Expected the transfer to fail")
        } catch (error: IOException) {
            error
        }

        assertSame(transferFailure, thrown)
        assertEquals(listOf(cleanupFailure), thrown.suppressed.toList())
    }

    @Test
    fun propagatesFatalRollbackErrorAndPreservesTransferFailure() {
        val transferFailure = IOException("destination write failed")
        val rollbackFailure = OutOfMemoryError("rollback failed")

        val thrown = try {
            withLanShareDownloadRollback(rollback = { throw rollbackFailure }) {
                throw transferFailure
            }
            error("Expected the transfer to fail")
        } catch (error: OutOfMemoryError) {
            error
        }

        assertSame(rollbackFailure, thrown)
        assertEquals(listOf(transferFailure), thrown.suppressed.toList())
    }

    @Test
    fun preservesPrimaryFatalErrorWhenRollbackAlsoFails() {
        val transferFailure = OutOfMemoryError("transfer failed")
        val rollbackFailure = AssertionError("rollback failed")

        val thrown = try {
            withLanShareDownloadRollback(rollback = { throw rollbackFailure }) {
                throw transferFailure
            }
            error("Expected the transfer to fail")
        } catch (error: OutOfMemoryError) {
            error
        }

        assertSame(transferFailure, thrown)
        assertEquals(listOf(rollbackFailure), thrown.suppressed.toList())
    }

    @Test
    fun doesNotRollbackDestinationAfterSuccessfulTransfer() {
        var rollbackCalled = false

        val result = withLanShareDownloadRollback(
            rollback = { rollbackCalled = true },
            action = { "complete" },
        )

        assertEquals("complete", result)
        assertFalse(rollbackCalled)
    }
}
