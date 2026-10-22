package com.luckyalanzhou.barcodegenerator.presentation.shared

import androidx.lifecycle.SavedStateHandle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraRequestCoordinatorTest {
    @Test
    fun externalAndPermissionRequestsSurviveCoordinatorRecreationAndAreConsumedOnce() {
        val handle = SavedStateHandle()
        val original = CameraRequestCoordinator(handle)
        original.prepare(45)
        original.setOutput(null, File("capture.jpg"))
        original.markStarted(1234L)
        original.beginExternalActivity(50)
        original.beginPermission(42, arrayOf("android.permission.CAMERA"))

        val recreated = CameraRequestCoordinator(SavedStateHandle(
            handle.keys().associateWith { handle.get<Any>(it) },
        ))
        assertEquals(45, recreated.state.value.requestCode)
        assertEquals(File("capture.jpg").absolutePath, recreated.state.value.outputFile?.absolutePath)
        assertEquals(1234L, recreated.state.value.startedAtMillis)
        assertEquals(50, recreated.consumeExternalActivity())
        assertEquals(0, recreated.consumeExternalActivity())
        val permissionResult = recreated.consumePermission(mapOf("android.permission.CAMERA" to true))
        assertEquals(42, permissionResult.requestCode)
        assertTrue(permissionResult.granted)
        assertEquals(0, recreated.consumePermission(emptyMap()).requestCode)
    }

    @Test
    fun permissionResultRejectsCancelledAndIncompleteResults() {
        val cancelled = CameraRequestCoordinator(SavedStateHandle()).apply {
            beginPermission(42, arrayOf("camera", "storage"))
        }.consumePermission(emptyMap())
        assertEquals(42, cancelled.requestCode)
        assertFalse(cancelled.granted)

        val incomplete = CameraRequestCoordinator(SavedStateHandle()).apply {
            beginPermission(42, arrayOf("camera", "storage"))
        }.consumePermission(mapOf("camera" to true))
        assertFalse(incomplete.granted)

        val denied = CameraRequestCoordinator(SavedStateHandle()).apply {
            beginPermission(42, arrayOf("camera", "storage"))
        }.consumePermission(mapOf("camera" to true, "storage" to false))
        assertFalse(denied.granted)
    }
}
