package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatedDocumentWriteTest {
    @Test
    fun successfulWriteKeepsTheCreatedDocument() {
        var deleteCalls = 0

        val result = writeCreatedDocumentWithRollback(
            write = { "saved" },
            deleteCreatedDocument = { deleteCalls++; true },
        )

        assertEquals("saved", result)
        assertEquals(0, deleteCalls)
    }

    @Test
    fun failedWriteDeletesTheNewDocumentAndPreservesTheFailure() {
        val writeFailure = IllegalStateException("partial write")
        var deleteCalls = 0

        val thrown = runCatching {
            writeCreatedDocumentWithRollback(
                write = { throw writeFailure },
                deleteCreatedDocument = { deleteCalls++; true },
            )
        }.exceptionOrNull()

        assertSame(writeFailure, thrown)
        assertEquals(1, deleteCalls)
    }

    @Test
    fun failedSuspendingWriteDeletesTheNewDocumentAndPreservesTheFailure() = runBlocking {
        val writeFailure = IllegalStateException("partial suspended write")
        var deleteCalls = 0

        val thrown = runCatching {
            writeCreatedDocumentWithRollbackSuspending(
                write = { throw writeFailure },
                deleteCreatedDocument = { deleteCalls++; true },
            )
        }.exceptionOrNull()

        assertSame(writeFailure, thrown)
        assertEquals(1, deleteCalls)
    }

    @Test
    fun cancelledSuspendingWriteDeletesTheNewDocumentAndPropagates() = runBlocking {
        val cancellation = CancellationException("suspended write cancelled")
        var deleteCalls = 0

        val thrown = runCatching {
            writeCreatedDocumentWithRollbackSuspending(
                write = { throw cancellation },
                deleteCreatedDocument = { deleteCalls++; true },
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(1, deleteCalls)
    }

    @Test
    fun cancellationDeletesTheNewDocumentAndPropagates() {
        val cancellation = CancellationException("activity cancelled")
        var deleteCalls = 0

        val thrown = runCatching {
            writeCreatedDocumentWithRollback(
                write = { throw cancellation },
                deleteCreatedDocument = { deleteCalls++; true },
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(1, deleteCalls)
    }

    @Test
    fun fatalErrorDeletesTheNewDocumentAndPropagates() {
        val fatalError = AssertionError("fatal write failure")
        var deleteCalls = 0

        val thrown = runCatching {
            writeCreatedDocumentWithRollback(
                write = { throw fatalError },
                deleteCreatedDocument = { deleteCalls++; true },
            )
        }.exceptionOrNull()

        assertSame(fatalError, thrown)
        assertEquals(1, deleteCalls)
    }

    @Test
    fun cleanupFailureIsSuppressedWithoutReplacingTheWriteFailure() {
        val writeFailure = IllegalStateException("partial write")
        val cleanupFailure = SecurityException("provider denied deletion")

        val thrown = runCatching {
            writeCreatedDocumentWithRollback(
                write = { throw writeFailure },
                deleteCreatedDocument = { throw cleanupFailure },
            )
        }.exceptionOrNull()

        assertSame(writeFailure, thrown)
        assertTrue(writeFailure.suppressed.single() === cleanupFailure)
    }

    @Test
    fun fatalCleanupErrorTakesPriorityOverOrdinaryWriteFailure() {
        val writeFailure = IllegalStateException("partial write")
        val cleanupFailure = OutOfMemoryError("provider cleanup exhausted memory")

        val thrown = runCatching {
            writeCreatedDocumentWithRollback(
                write = { throw writeFailure },
                deleteCreatedDocument = { throw cleanupFailure },
            )
        }.exceptionOrNull()

        assertSame(cleanupFailure, thrown)
        assertTrue(cleanupFailure.suppressed.single() === writeFailure)
    }

    @Test
    fun fatalWriteFailureRemainsPrimaryWhenCleanupAlsoFailsFatally() {
        val writeFailure = AssertionError("fatal partial write")
        val cleanupFailure = OutOfMemoryError("provider cleanup exhausted memory")

        val thrown = runCatching {
            writeCreatedDocumentWithRollback(
                write = { throw writeFailure },
                deleteCreatedDocument = { throw cleanupFailure },
            )
        }.exceptionOrNull()

        assertSame(writeFailure, thrown)
        assertTrue(writeFailure.suppressed.single() === cleanupFailure)
    }
}
