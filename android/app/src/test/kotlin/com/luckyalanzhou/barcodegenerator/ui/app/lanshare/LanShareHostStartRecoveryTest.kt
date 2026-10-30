package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import com.luckyalanzhou.barcodegenerator.presentation.lanshare.captureRecoverableLanShareHostStart
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LanShareHostStartRecoveryTest {
    @Test
    fun successfulStartSkipsRollbackAndFailureFeedback() {
        var rollbackCount = 0
        var failureCount = 0

        captureRecoverableLanShareHostStart(
            startHost = {},
            rollback = { rollbackCount++ },
            onFailure = { failureCount++ },
        )

        assertEquals(0, rollbackCount)
        assertEquals(0, failureCount)
    }

    @Test
    fun ordinaryExceptionRollsBackAndKeepsRecoverableFeedback() {
        val startError = IllegalStateException("房间启动失败")
        var rollbackCount = 0
        var reported: Exception? = null

        captureRecoverableLanShareHostStart(
            startHost = { throw startError },
            rollback = { rollbackCount++ },
            onFailure = { reported = it },
        )

        assertEquals(1, rollbackCount)
        assertSame(startError, reported)
    }

    @Test
    fun rollbackReusingStartFailureDoesNotMaskOriginalFailure() {
        val startError = IllegalStateException("房间启动失败")
        var reported: Exception? = null

        captureRecoverableLanShareHostStart(
            startHost = { throw startError },
            rollback = { throw startError },
            onFailure = { reported = it },
        )

        assertSame(startError, reported)
        assertEquals(0, startError.suppressed.size)
    }

    @Test
    fun cancellationRollsBackAndPropagatesWithoutFailureFeedback() {
        val cancellation = CancellationException("启动被取消")
        var rollbackCount = 0
        var failureCount = 0
        var caught: CancellationException? = null

        try {
            captureRecoverableLanShareHostStart(
                startHost = { throw cancellation },
                rollback = { rollbackCount++ },
                onFailure = { failureCount++ },
            )
        } catch (error: CancellationException) {
            caught = error
        }

        assertSame(cancellation, caught)
        assertEquals(1, rollbackCount)
        assertEquals(0, failureCount)
    }

    @Test
    fun fatalErrorRollsBackAndPropagatesWithoutFailureFeedback() {
        val fatalError = AssertionError("启动状态异常")
        var rollbackCount = 0
        var failureCount = 0
        var caught: AssertionError? = null

        try {
            captureRecoverableLanShareHostStart(
                startHost = { throw fatalError },
                rollback = { rollbackCount++ },
                onFailure = { failureCount++ },
            )
        } catch (error: AssertionError) {
            caught = error
        }

        assertSame(fatalError, caught)
        assertEquals(1, rollbackCount)
        assertEquals(0, failureCount)
    }

    @Test
    fun rollbackCancellationTakesPrecedenceAndRetainsStartFailure() {
        val startError = IllegalStateException("房间启动失败")
        val rollbackCancellation = CancellationException("回滚被取消")
        var caught: Throwable? = null

        try {
            captureRecoverableLanShareHostStart(
                startHost = { throw startError },
                rollback = { throw rollbackCancellation },
                onFailure = {},
            )
        } catch (error: Throwable) {
            caught = error
        }

        assertSame(rollbackCancellation, caught)
        assertEquals(1, rollbackCancellation.suppressed.size)
        assertSame(startError, rollbackCancellation.suppressed.single())
    }

    @Test
    fun rollbackFatalErrorTakesPrecedenceAndRetainsStartFailure() {
        val startError = IllegalStateException("房间启动失败")
        val rollbackError = AssertionError("回滚时出现致命错误")
        var caught: Throwable? = null

        try {
            captureRecoverableLanShareHostStart(
                startHost = { throw startError },
                rollback = { throw rollbackError },
                onFailure = {},
            )
        } catch (error: Throwable) {
            caught = error
        }

        assertSame(rollbackError, caught)
        assertEquals(1, rollbackError.suppressed.size)
        assertSame(startError, rollbackError.suppressed.single())
    }

    @Test
    fun originalFatalErrorRemainsPrimaryWhenRollbackAlsoFailsFatally() {
        val startError = AssertionError("启动时出现致命错误")
        val rollbackError = LinkageError("回滚时出现致命错误")
        var caught: Throwable? = null

        try {
            captureRecoverableLanShareHostStart(
                startHost = { throw startError },
                rollback = { throw rollbackError },
                onFailure = {},
            )
        } catch (error: Throwable) {
            caught = error
        }

        assertSame(startError, caught)
        assertEquals(1, startError.suppressed.size)
        assertSame(rollbackError, startError.suppressed.single())
    }
}
