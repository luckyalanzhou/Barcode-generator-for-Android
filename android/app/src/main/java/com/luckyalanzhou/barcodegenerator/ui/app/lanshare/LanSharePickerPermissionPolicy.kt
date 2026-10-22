package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import android.Manifest

internal enum class LanSharePickerType {
    GALLERY,
    DOCUMENTS,
}

/** Determines whether a picker needs legacy broad storage permission before it can be opened. */
internal object LanSharePickerPermissionPolicy {
    fun requiredReadPermission(picker: LanSharePickerType, sdkInt: Int): String? = when (picker) {
        LanSharePickerType.GALLERY ->
            if (sdkInt < 33) Manifest.permission.READ_EXTERNAL_STORAGE else null
        // ACTION_OPEN_DOCUMENT grants access to the selected URI; it does not need broad storage access.
        LanSharePickerType.DOCUMENTS -> null
    }
}
