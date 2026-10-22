package com.luckyalanzhou.barcodegenerator.ui.app.platform

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FavoriteBackupChooserLaunchTest {
    @Test
    fun successfulLaunchRetainsPreparedArtifact() {
        var cleanupCount = 0
        var failureCount = 0

        launchFavoriteBackupChooser(
            launch = {},
            cleanupArtifact = { cleanupCount++ },
            onFailure = { failureCount++ },
        )

        assertEquals(0, cleanupCount)
        assertEquals(0, failureCount)
    }

    @Test
    fun ordinaryLaunchExceptionCleansArtifactAndReportsFailure() {
        val launchError = IllegalStateException("没有可用的分享目标")
        var cleanupCount = 0
        var reported: Exception? = null

        launchFavoriteBackupChooser(
            launch = { throw launchError },
            cleanupArtifact = { cleanupCount++ },
            onFailure = { reported = it },
        )

        assertEquals(1, cleanupCount)
        assertSame(launchError, reported)
    }

    @Test
    fun cancellationCleansArtifactAndPropagates() {
        val cancellation = CancellationException("分享流程已取消")
        var cleanupCount = 0
        var caught: CancellationException? = null

        try {
            launchFavoriteBackupChooser(
                launch = { throw cancellation },
                cleanupArtifact = { cleanupCount++ },
                onFailure = {},
            )
        } catch (error: CancellationException) {
            caught = error
        }

        assertSame(cancellation, caught)
        assertEquals(1, cleanupCount)
    }

    @Test
    fun fatalErrorCleansArtifactAndPropagates() {
        val fatalError = AssertionError("分享面板启动错误")
        var cleanupCount = 0
        var caught: AssertionError? = null

        try {
            launchFavoriteBackupChooser(
                launch = { throw fatalError },
                cleanupArtifact = { cleanupCount++ },
                onFailure = {},
            )
        } catch (error: AssertionError) {
            caught = error
        }

        assertSame(fatalError, caught)
        assertEquals(1, cleanupCount)
    }
}
