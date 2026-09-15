package com.luckyalanzhou.barcodegenerator

import java.io.File

internal fun multipartFileName(value: String) = value.replace(Regex("[\r\n\"]"), "_")

internal fun safeFileName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|\r\n]"), "_").take(100).ifBlank { "附件" }

internal fun safeBrowserClientId(value: String) = value.takeIf { it.matches(Regex("c[a-zA-Z0-9_-]{8,63}")) } ?: "clegacy"

/** 共享目录只允许直接子文件，拒绝编码后的 ../ 等路径穿越。 */
internal fun sharedFile(folder: File, id: String): File? {
    val root = folder.canonicalFile
    val candidate = File(root, id).canonicalFile
    return candidate.takeIf { it.isFile && it.parentFile == root }
}

internal fun mimeTypeForName(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "mp4" -> "video/mp4"
    else -> "application/octet-stream"
}

internal fun listFiles(folder: File, sender: String) = folder.listFiles().orEmpty()
    .filter { it.isFile }
    .sortedBy { it.lastModified() }
    .map { file -> toLanShareFile(file, sender) }

internal fun toLanShareFile(file: File, sender: String): LanShareFile {
    val browserMatch = Regex("^web_-?\\d+_(c[a-zA-Z0-9_-]{8,63})_(.*)$").matchEntire(file.name)
    val fromBrowser = file.name.startsWith("web_")
    val fromApp = file.name.startsWith("app_")
    val displayName = browserMatch?.groupValues?.get(2)
        ?: if (fromBrowser || fromApp) file.name.substringAfter('_').substringAfter('_', file.name) else file.name.substringAfter('_', file.name)
    val source = browserMatch?.groupValues?.get(1)?.let { "browser:$it" }
        ?: if (fromBrowser) "browser" else if (fromApp) "app" else sender
    return LanShareFile(file.name, displayName, file.length(), file.lastModified(), source)
}
