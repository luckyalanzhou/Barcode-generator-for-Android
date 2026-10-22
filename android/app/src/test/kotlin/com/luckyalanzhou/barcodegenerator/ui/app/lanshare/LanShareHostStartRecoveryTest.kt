package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

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
}
