package com.luckyalanzhou.barcodegenerator.presentation.favorites

internal fun isValidFavoriteFolderName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name

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
