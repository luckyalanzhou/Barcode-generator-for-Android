package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

/** 将编辑框里的新名称放回原父级路径；空路径用于创建一级文件夹。 */
internal fun favoriteFolderRenameTargetPath(path: String, newName: String): String {
    val parent = path.substringBeforeLast('/', "")
    return if (parent.isBlank()) newName else "$parent/$newName"
}

internal fun isValidFavoriteFolderPath(value: String): Boolean {
    val parts = value.split('/')
    return parts.size in 1..2 && parts.all { part ->
        part.isNotBlank() && part != "." && part != ".." && !part.contains('\\')
    }
}
