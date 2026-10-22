package com.luckyalanzhou.barcodegenerator

import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.presentation.shared.CameraRequestCoordinator
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionGrantRegressionTest {
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
}
