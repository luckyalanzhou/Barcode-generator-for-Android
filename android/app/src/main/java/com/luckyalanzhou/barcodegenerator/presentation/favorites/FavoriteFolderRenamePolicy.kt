package com.luckyalanzhou.barcodegenerator.presentation.favorites

internal fun isValidFavoriteFolderName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name

/** 重命名输入框只编辑路径末段，父文件夹路径不属于名称本身。 */
internal fun favoriteFolderEditorInitialValue(path: String): String = path.substringAfterLast('/')

/** 将新名称放回原父级路径；空路径用于创建一级文件夹。 */
internal fun favoriteFolderRenameTargetPath(path: String, newName: String): String {
    val parent = parentPath(path)
    return if (parent.isBlank()) newName else "$parent/$newName"
}

internal fun isSafeFavoriteFolderRename(
    path: String,
    renamedPath: String,
    knownFolderPaths: Collection<String>,
): Boolean {
    if (!isValidPath(path) || !isValidPath(renamedPath)) return false
    if (parentPath(path) != parentPath(renamedPath)) return false

    val affectedPaths = (knownFolderPaths + path)
        .filter { it == path || it.startsWith("$path/") }
    return affectedPaths.all { currentPath ->
        val nextPath = if (currentPath == path) renamedPath else renamedPath + currentPath.removePrefix(path)
        isValidPath(nextPath)
    }
}

private fun isValidPath(path: String): Boolean {
    val parts = path.split('/')
    return parts.size in 1..2 && parts.all(::isValidFavoriteFolderName)
}

private fun parentPath(path: String): String = path.substringBeforeLast('/', "")
