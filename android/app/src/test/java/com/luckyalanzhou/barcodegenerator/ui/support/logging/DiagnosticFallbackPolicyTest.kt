package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticFallbackPolicyTest {
    @Test
    fun ordinaryDiagnosticFailureUsesFallback() {
        val failure = IllegalStateException("backend unavailable")
        var fallbackCalled = false

        val result = runWithDiagnosticFallback(
            operation = { throw failure },
            fallback = {
                assertSame(failure, it)
                fallbackCalled = true
                "fallback snapshot"
            },
        )

        assertEquals("fallback snapshot", result)
        assertTrue(fallbackCalled)
    }

    @Test
    fun fatalErrorPropagatesWithoutFallback() {
        val fatal = AssertionError("fatal backend failure")
        var fallbackCalled = false

        val actual = try {
            runWithDiagnosticFallback(
                operation = { throw fatal },
                fallback = {
                    fallbackCalled = true
                    "fallback snapshot"
                },
            )
            null
        } catch (error: AssertionError) {
            error
        }

        assertSame(fatal, actual)
        assertFalse(fallbackCalled)
    }

    @Test
    fun cancellationPropagatesWithoutFallback() {
        val cancellation = CancellationException("snapshot cancelled")
        var fallbackCalled = false

        val actual = try {
            runWithDiagnosticFallback(
                operation = { throw cancellation },
                fallback = {
                    fallbackCalled = true
                    "fallback snapshot"
                },
            )
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, actual)
        assertFalse(fallbackCalled)
    }

    @Test
    fun interruptedOperationRestoresThreadInterruptAndPropagates() {
        Thread.interrupted()
        val interruption = InterruptedException("log flush interrupted")
        var fallbackCalled = false
        var interruptRestored = false

        val actual = try {
            runWithDiagnosticFallback(
                operation = { throw interruption },
                fallback = {
                    fallbackCalled = true
                    "fallback snapshot"
                },
            )
            null
        } catch (error: InterruptedException) {
            interruptRestored = Thread.currentThread().isInterrupted
            error
        } finally {
            Thread.interrupted()
        }

        assertSame(interruption, actual)
        assertFalse(fallbackCalled)
        assertTrue(interruptRestored)
    }

    @Test
    fun interruptedFallbackRestoresThreadInterruptAndPropagates() {
        Thread.interrupted()
        val interruption = InterruptedException("fallback write interrupted")
        var interruptRestored = false

        val actual = try {
            runWithDiagnosticFallback(
                operation = { throw IllegalStateException("backend unavailable") },
                fallback = { throw interruption },
            )
            null
        } catch (error: InterruptedException) {
            interruptRestored = Thread.currentThread().isInterrupted
            error
        } finally {
            Thread.interrupted()
        }

        assertSame(interruption, actual)
        assertTrue(interruptRestored)
    }

    @Test
    fun fallbackFailurePropagatesWithoutBeingReclassified() {
        val fallbackFailure = AssertionError("fallback failed")
        val actual = try {
            runWithDiagnosticFallback(
                operation = { throw IllegalStateException("backend unavailable") },
                fallback = { throw fallbackFailure },
            )
            null
        } catch (error: AssertionError) {
            error
        }

        assertSame(fallbackFailure, actual)
    }
}
