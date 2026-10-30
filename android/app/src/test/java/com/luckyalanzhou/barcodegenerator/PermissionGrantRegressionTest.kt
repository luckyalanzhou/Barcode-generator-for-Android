package com.luckyalanzhou.barcodegenerator

import android.app.Activity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.presentation.shared.CameraRequestCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.PermissionRequestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionGrantRegressionTest {
    @Test
    fun firstPermissionResultMustRemainAssociatedWithItsRequestWhenLaunchesOverlap() {
        val savedState = SavedStateHandle()
        var coordinator = CameraRequestCoordinator(savedState)
        val registry = RecordingPermissionRegistry()
        val routedResults = mutableListOf<PermissionRequestResult>()
        val launcher = registry.register(
            "permissions",
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result -> routedResults += coordinator.consumePermission(result) }

        assertTrue(coordinator.beginPermission(42, arrayOf("android.permission.CAMERA")))
        launcher.launch(arrayOf("android.permission.CAMERA"))
        coordinator = CameraRequestCoordinator(savedState)
        assertFalse(coordinator.beginPermission(56, arrayOf("android.permission.READ_MEDIA_IMAGES")))

        assertEquals(1, registry.launchedRequestCodes.size)
        registry.dispatchResult(
            registry.launchedRequestCodes.first(),
            mapOf("android.permission.CAMERA" to true),
        )

        assertEquals("The first callback must retain the first request code", 42, routedResults.single().requestCode)
        assertTrue("The camera grant must be evaluated against the camera request", routedResults.single().granted)
    }

    @Test
    fun cancelledPermissionResultMustNotBeTreatedAsGranted() {
        val result = ActivityResultContracts.RequestMultiplePermissions()
            .parseResult(Activity.RESULT_CANCELED, null)

        val coordinator = CameraRequestCoordinator(SavedStateHandle()).apply {
            beginPermission(42, arrayOf("android.permission.CAMERA"))
        }

        assertFalse(coordinator.consumePermission(result).granted)
    }

    @Test
    fun onlyExplicitlyGrantedRequestedPermissionsAreAccepted() {
        val coordinator = CameraRequestCoordinator(SavedStateHandle()).apply {
            beginPermission(42, arrayOf("camera", "storage"))
        }

        assertFalse(coordinator.consumePermission(mapOf("camera" to true)).granted)
    }

    private class RecordingPermissionRegistry : ActivityResultRegistry() {
        val launchedRequestCodes = mutableListOf<Int>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launchedRequestCodes += requestCode
        }
    }
}
