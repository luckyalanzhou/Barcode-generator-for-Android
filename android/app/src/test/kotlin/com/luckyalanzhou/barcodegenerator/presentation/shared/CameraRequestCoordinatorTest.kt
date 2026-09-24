package com.luckyalanzhou.barcodegenerator.presentation.shared

import androidx.lifecycle.SavedStateHandle
import java.io.File
import org.junit.Assert.assertEquals
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
        original.beginPermission(42)

        val recreated = CameraRequestCoordinator(SavedStateHandle(
            handle.keys().associateWith { handle.get<Any>(it) },
        ))
        assertEquals(45, recreated.state.value.requestCode)
        assertEquals(File("capture.jpg").absolutePath, recreated.state.value.outputFile?.absolutePath)
        assertEquals(1234L, recreated.state.value.startedAtMillis)
        assertEquals(50, recreated.consumeExternalActivity())
        assertEquals(0, recreated.consumeExternalActivity())
        assertEquals(42, recreated.consumePermission())
        assertEquals(0, recreated.consumePermission())
    }
}
