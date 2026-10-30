package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File
import java.util.UUID

internal const val SHAREABLE_CACHE_ROOT = "share"
internal const val SHAREABLE_CAMERA_DIRECTORY = "camera"
internal const val SHAREABLE_IMAGES_DIRECTORY = "images"
internal const val SHAREABLE_FAVORITES_DIRECTORY = "favorites"
internal const val SHAREABLE_DEBUG_LOGS_DIRECTORY = "debug-logs"
internal const val SHAREABLE_UPDATE_APKS_DIRECTORY = "updates"

/** 返回 FileProvider 唯一映射的缓存子树下、用途独立的临时文件目录。 */
internal fun shareableCacheDirectory(cacheDirectory: File, category: String): File {
    require(category.isNotBlank() && category != "." && category != ".." &&
        File(category).name == category && '/' !in category && '\\' !in category
    ) { "分享缓存类别无效" }
    return File(File(cacheDirectory, SHAREABLE_CACHE_ROOT), category)
}

/** 为待分享文件创建专用目录，失败时不回退到宽泛的缓存根目录。 */
internal fun ensureShareableCacheDirectory(cacheDirectory: File, category: String): File =
    shareableCacheDirectory(cacheDirectory, category).apply {
        check(isDirectory || mkdirs()) { "无法创建分享缓存目录" }
    }

/** 将旧版本保存在缓存根目录的待安装更新包移入 FileProvider 映射范围。 */
internal fun moveLegacyPendingApkToShareableCache(cacheDirectory: File, apkFile: File): File {
    val canonicalCacheDirectory = cacheDirectory.canonicalFile
    val canonicalApkFile = apkFile.canonicalFile
    if (canonicalApkFile.parentFile != canonicalCacheDirectory ||
        !canonicalApkFile.name.startsWith(UPDATE_APK_FILE_PREFIX) ||
        !canonicalApkFile.name.endsWith(".apk")
    ) {
        return apkFile
    }

    val destinationDirectory = ensureShareableCacheDirectory(cacheDirectory, SHAREABLE_UPDATE_APKS_DIRECTORY)
    val destinationName = canonicalApkFile.name.removeSuffix(".apk") + "-legacy-${UUID.randomUUID()}.apk"
    val destination = File(destinationDirectory, destinationName)
    check(canonicalApkFile.renameTo(destination)) { "无法迁移旧版更新安装文件" }
    return destination
}

private const val UPDATE_APK_FILE_PREFIX = "barcode-generator-update-"
