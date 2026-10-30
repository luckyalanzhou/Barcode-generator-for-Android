package com.luckyalanzhou.barcodegenerator.domain

/** 收藏文件在界面、持久化和备份导入中的稳定身份。 */
data class FavoriteFileIdentity(
    val folder: String,
    val name: String,
) {
    companion object {
        fun of(folder: String, name: String): FavoriteFileIdentity = FavoriteFileIdentity(
            folder = folder.trim().trim('/').let { if (it == "默认") "" else it },
            name = name.trim(),
        )
    }
}
