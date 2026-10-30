package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import android.Manifest

internal enum class LanSharePickerType {
    GALLERY,
    DOCUMENTS,
}

/** 判断打开文件选择器前是否需要申请旧版广泛存储权限。 */
internal object LanSharePickerPermissionPolicy {
    fun requiredReadPermission(picker: LanSharePickerType, sdkInt: Int): String? = when (picker) {
        LanSharePickerType.GALLERY ->
            if (sdkInt < 33) Manifest.permission.READ_EXTERNAL_STORAGE else null
        // ACTION_OPEN_DOCUMENT 会授权访问用户选中的 URI，无需申请广泛存储权限。
        LanSharePickerType.DOCUMENTS -> null
    }
}
