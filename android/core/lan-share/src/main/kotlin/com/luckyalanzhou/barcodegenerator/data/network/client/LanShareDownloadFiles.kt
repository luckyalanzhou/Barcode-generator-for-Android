package com.luckyalanzhou.barcodegenerator.data.network.client

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** 在目标文件所在目录创建暂存文件，保证下载完成后可在同一文件系统内替换。 */
internal fun createDownloadTempFile(destination: File): File {
    val target = destination.absoluteFile
    val parent = target.parentFile ?: throw IOException("无法确定下载目录")
    if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
        throw IOException("无法创建下载目录：${parent.path}")
    }
    return File.createTempFile(".${target.name}.", ".part", parent)
}

/** 下载完整后才替换目标文件；移动失败时保留原文件和暂存文件。 */
internal fun replaceDownloadedFile(
    temporary: File,
    destination: File,
    move: (Path, Path) -> Path = { source, target ->
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    },
) {
    val target = destination.absoluteFile
    if (temporary.absoluteFile.parentFile != target.parentFile) {
        throw IOException("下载暂存文件必须与目标文件位于同一目录")
    }
    move(temporary.toPath(), target.toPath())
}
