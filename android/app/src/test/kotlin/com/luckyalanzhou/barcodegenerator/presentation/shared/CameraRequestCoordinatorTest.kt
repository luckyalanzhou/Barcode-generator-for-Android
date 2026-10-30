package com.luckyalanzhou.barcodegenerator.presentation.shared

import androidx.lifecycle.SavedStateHandle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraRequestCoordinatorTest {
    @Test
    fun beginningAnotherExternalActivityDoesNotReplacePendingRequest() {
        val handle = SavedStateHandle()
        val coordinator = CameraRequestCoordinator(handle)

        assertTrue(coordinator.beginExternalActivity(44))
        assertFalse(coordinator.beginExternalActivity(46))
        assertTrue(coordinator.hasPendingExternalActivity())

        val restored = CameraRequestCoordinator(SavedStateHandle(
            handle.keys().associateWith { handle.get<Any>(it) },
        ))
        assertEquals(44, restored.consumeExternalActivity())

        assertEquals(44, coordinator.consumeExternalActivity())
        assertFalse(coordinator.hasPendingExternalActivity())
        assertEquals(0, coordinator.consumeExternalActivity())
        assertTrue(coordinator.beginExternalActivity(46))
        assertEquals(46, coordinator.consumeExternalActivity())
    }

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

    @Test
    fun rejectedCameraPermissionRequestDoesNotReplacePendingCameraAction() {
        val coordinator = CameraRequestCoordinator(SavedStateHandle())

        assertTrue(coordinator.beginPermission(42, arrayOf("android.permission.CAMERA"), cameraRequestCode = 43))
        assertFalse(coordinator.beginPermission(42, arrayOf("android.permission.CAMERA"), cameraRequestCode = 45))

        assertEquals("The first permission result must resume its camera action", 43, coordinator.state.value.requestCode)
    }

    @Test
    fun failedPermissionLaunchCanBeAbortedAndRetried() {
        val coordinator = CameraRequestCoordinator(SavedStateHandle())

        assertTrue(coordinator.beginPermission(42, arrayOf("android.permission.CAMERA"), cameraRequestCode = 43))
        coordinator.cancelPermission()

        assertTrue(coordinator.beginPermission(42, arrayOf("android.permission.CAMERA"), cameraRequestCode = 45))
        assertEquals(45, coordinator.state.value.requestCode)
        assertTrue(coordinator.consumePermission(mapOf("android.permission.CAMERA" to true)).granted)
    }
}
