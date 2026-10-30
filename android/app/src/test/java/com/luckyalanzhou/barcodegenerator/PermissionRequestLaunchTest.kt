package com.luckyalanzhou.barcodegenerator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequestLaunchTest {
    @Test
    fun launcherFailureRunsRecoveryCallbackAndReturnsFailure() {
        val launchError = IllegalStateException("Activity is no longer active")
        var recovered: Exception? = null

        val launched = launchPermissionRequestSafely(
            launch = { throw launchError },
            onFailure = { recovered = it },
        )

        assertFalse(launched)
        assertSame(launchError, recovered)
    }

    @Test
    fun successfulLaunchDoesNotRunRecoveryCallback() {
        var recovered = false

        val launched = launchPermissionRequestSafely(
            launch = {},
            onFailure = { recovered = true },
        )

        assertTrue(launched)
        assertFalse(recovered)
    }

    @Test
    fun fatalLauncherErrorPropagates() {
        val fatal = AssertionError("fatal")

        val caught = runCatching {
            launchPermissionRequestSafely(
                launch = { throw fatal },
                onFailure = { throw AssertionError("must not recover fatal errors") },
            )
        }.exceptionOrNull()

        assertSame(fatal, caught)
    }
}
