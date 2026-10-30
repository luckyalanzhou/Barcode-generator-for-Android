package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File

/** 只回收应用创建且已不属于待恢复或活动识别流程的相机临时照片。 */
internal fun cleanupInterruptedCameraCaptureFiles(
    cacheDirectory: File,
    modifiedBeforeMillis: Long,
    protectedPaths: Set<String>,
): Int {
    val entries = try {
        cacheDirectory.listFiles().orEmpty()
    } catch (_: Exception) {
        return 0
    }
    var deletedCount = 0
    entries.forEach { file ->
        val ownedCameraCapture =
            (file.name.startsWith("lan_share_photo_") || file.name.startsWith("barcode_camera_")) &&
                file.name.endsWith(".jpg")
        if (!file.isFile || !ownedCameraCapture || file.absolutePath in protectedPaths ||
            file.lastModified() > modifiedBeforeMillis
        ) {
            return@forEach
        }
        try {
            if (file.delete()) deletedCount++
        } catch (_: Exception) {
            // 临时缓存清理失败不应影响页面恢复或相机结果处理。
        }
    }
    return deletedCount
}
