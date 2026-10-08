package com.luckyalanzhou.barcodegenerator.domain

/** Stable identity of a favorite file across UI, persistence, and backup import. */
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
