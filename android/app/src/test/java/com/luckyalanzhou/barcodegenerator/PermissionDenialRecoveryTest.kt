package com.luckyalanzhou.barcodegenerator

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionDenialRecoveryTest {
    @Test
    fun identifiesThePermissionThatNeedsAnExplanationBeforeRetry() {
        assertEquals(
            "android.permission.CAMERA",
            permissionRequiringRationale(
                arrayOf("android.permission.CAMERA", "android.permission.READ_EXTERNAL_STORAGE"),
                shouldShowRationale = { it == "android.permission.CAMERA" },
            ),
        )
    }

    @Test
    fun findsDeniedPermissionWhenTheSystemWillNotShowItsPromptAgain() {
        assertEquals(
            "android.permission.CAMERA",
            findPermanentlyDeniedPermission(
                mapOf("android.permission.CAMERA" to false),
                shouldShowRationale = { false },
            ),
        )
    }

    @Test
    fun doesNotTreatGrantedOrRationaleEligiblePermissionAsPermanentlyDenied() {
        assertEquals(
            null,
            findPermanentlyDeniedPermission(
                mapOf(
                    "android.permission.CAMERA" to true,
                    "android.permission.READ_EXTERNAL_STORAGE" to false,
                ),
                shouldShowRationale = { it == "android.permission.READ_EXTERNAL_STORAGE" },
            ),
        )
    }
}
