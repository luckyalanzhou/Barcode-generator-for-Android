package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanSharePickerPermissionPolicyTest {
    @Test
    fun documentsPickerDoesNotRequireBroadStoragePermission() {
        for (sdkInt in 26..37) {
            assertNull(
                "ACTION_OPEN_DOCUMENT must rely on the selected URI grant on API $sdkInt",
                LanSharePickerPermissionPolicy.requiredReadPermission(LanSharePickerType.DOCUMENTS, sdkInt),
            )
        }
    }

    @Test
    fun legacyGalleryPickerKeepsItsReadPermissionRequirement() {
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            LanSharePickerPermissionPolicy.requiredReadPermission(LanSharePickerType.GALLERY, 26),
        )
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            LanSharePickerPermissionPolicy.requiredReadPermission(LanSharePickerType.GALLERY, 32),
        )
        assertNull(LanSharePickerPermissionPolicy.requiredReadPermission(LanSharePickerType.GALLERY, 33))
    }
}
