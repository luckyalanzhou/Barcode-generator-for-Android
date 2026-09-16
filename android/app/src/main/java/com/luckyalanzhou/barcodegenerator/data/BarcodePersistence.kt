package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.view.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

internal suspend fun MainActivity.saveFavoriteFoldersOnIo(folders: List<FavoriteFolderEntity>) {
    barcodeRepository.saveFavoriteFolders(folders)
}

internal suspend fun MainActivity.saveAllFavoritesOnIo(
    itemSnapshot: List<CodeItemEntity>,
    groupSnapshot: List<FavoriteGroupEntity>,
    groupItemSnapshot: List<FavoriteGroupItemEntity>,
    folderSnapshot: List<FavoriteFolderEntity>
) {
    barcodeRepository.saveAllFavorites(itemSnapshot, groupSnapshot, groupItemSnapshot, folderSnapshot)
}

internal suspend fun MainActivity.loadFavoriteFoldersOnIo() {
    favoriteFolders.clear()
    favoriteFolders.addAll((barcodeRepository.loadFolders().map { it.name } + favoriteGroups.map { it.folder }).filter { it.isNotBlank() && it != "默认" }.distinct().sorted())
    viewModel.publishDataState()
}

internal suspend fun MainActivity.loadItemsOnIo() {
    items.clear()
    items.addAll(barcodeRepository.loadItems().map { CodeItem(it.id, it.text, it.format, it.createdAt, it.favorite, it.folder.takeUnless { folder -> folder == "默认" } ?: "", it.inHistory) })
    viewModel.publishDataState()
}

internal suspend fun MainActivity.loadFavoriteGroupsOnIo() {
    favoriteGroups.clear()
    val groups = barcodeRepository.loadGroups()
    val itemIds = barcodeRepository.loadGroupItems().groupBy { it.groupId }
    favoriteGroups.addAll(groups.map { group ->
        FavoriteGroup(group.id, group.folder.takeUnless { it == "默认" } ?: "", group.name, group.savedAt, itemIds[group.id].orEmpty().map { it.itemId }.toMutableList())
    })
    viewModel.publishDataState()
}

private fun MainActivity.itemSnapshot() = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(MainActivity.MAX_HISTORY_ITEMS)).map { CodeItemEntity(it.id, it.text, it.format, it.createdAt, it.favorite, it.folder, it.inHistory) }
private fun MainActivity.groupSnapshot() = favoriteGroups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) }
private fun MainActivity.groupItemSnapshot() = favoriteGroups.flatMap { group -> group.itemIds.map { FavoriteGroupItemEntity(group.id, it) } }
private fun MainActivity.folderSnapshot() = favoriteFolders.filter { it.isNotBlank() }.distinct().map(::FavoriteFolderEntity)

internal fun MainActivity.saveItems() {
    val snapshot = itemSnapshot()
    viewModel.publishDataState()
    enqueuePersistenceWrite { barcodeRepository.saveItems(snapshot) }
}

internal fun MainActivity.saveFavoriteGroups() {
    val groups = groupSnapshot()
    val links = groupItemSnapshot()
    viewModel.publishDataState()
    enqueuePersistenceWrite { barcodeRepository.saveFavoriteGroups(groups, links) }
}

internal fun MainActivity.saveFavoriteFolders() {
    val folders = folderSnapshot()
    viewModel.publishDataState()
    enqueuePersistenceWrite { saveFavoriteFoldersOnIo(folders) }
}

internal fun MainActivity.saveAllFavorites() {
    val items = itemSnapshot()
    val groups = groupSnapshot()
    val links = groupItemSnapshot()
    val folders = folderSnapshot()
    viewModel.publishDataState()
    enqueuePersistenceWrite { saveAllFavoritesOnIo(items, groups, links, folders) }
}

/** 将所有非阻塞保存操作串成一条队列；快照仍在 UI 线程立即取得，写入顺序不会互相覆盖。 */
private fun MainActivity.enqueuePersistenceWrite(write: suspend () -> Unit) {
    val next: Job
    synchronized(persistenceQueueLock) {
        val previous = persistenceWriteTail
        next = persistenceScope.launch {
            previous?.join()
            databaseMutex.withLock { write() }
        }
        persistenceWriteTail = next
    }
    next.invokeOnCompletion {
        synchronized(persistenceQueueLock) {
            if (persistenceWriteTail === next) persistenceWriteTail = null
        }
    }
}
internal fun MainActivity.loadItems() = lifecycleScope.launch(Dispatchers.IO) { databaseMutex.withLock { loadItemsOnIo() } }
internal fun MainActivity.loadFavoriteGroups() = lifecycleScope.launch(Dispatchers.IO) { databaseMutex.withLock { loadFavoriteGroupsOnIo() } }
internal fun MainActivity.loadFavoriteFolders() = lifecycleScope.launch(Dispatchers.IO) { databaseMutex.withLock { loadFavoriteFoldersOnIo() } }

internal suspend fun MainActivity.migrateLegacySettingsIfNeeded() {
        if (settingsStore.get(SettingsStore.SETTINGS_MIGRATED, false)) return
        val migratedStyle = StyleSettings(
            barColor = legacyPrefs.getInt("style_bar_color", Color.BLACK), bgColor = legacyPrefs.getInt("style_bg_color", Color.WHITE),
            showText = legacyPrefs.getBoolean("style_show_text", true), textPosition = legacyPrefs.getString("style_text_position", "bottom") ?: "bottom",
            textSize = legacyPrefs.getFloat("style_text_size", 14f), barHeight = legacyPrefs.getInt("style_bar_height", 55), barWidth = legacyPrefs.getFloat("style_bar_width", 220f),
            margin = legacyPrefs.getInt("style_margin", 4), showFormat = legacyPrefs.getBoolean("style_show_format", false), colorScheme = legacyPrefs.getString("style_color_scheme", "system") ?: "system"
        )
        settingsStore.saveStyle(migratedStyle).join()
        legacyPrefs.getString("last_update_error", "")?.takeIf { it.isNotBlank() }?.let(settingsStore::setUpdateError)
        settingsStore.markMigrated().join()
        legacyPrefs.edit().remove("style_bar_color").remove("style_bg_color").remove("style_show_text").remove("style_text_position").remove("style_text_size").remove("style_bar_height").remove("style_bar_width").remove("style_margin").remove("style_show_format").remove("style_transparent_background").remove("style_color_scheme").remove("last_update_error").apply()
    }


internal suspend fun MainActivity.migrateLegacyDataIfNeeded() = databaseMutex.withLock {
        if (legacyPrefs.getBoolean("room_data_migrated", false)) return
        val legacyItems = runCatching { JSONArray(legacyPrefs.getString("items", "[]")) }.getOrDefault(JSONArray())
        val legacyGroups = runCatching { JSONArray(legacyPrefs.getString("favorite_groups", "[]")) }.getOrDefault(JSONArray())
        val legacyFolders = legacyPrefs.getStringSet("favorite_folders", emptySet()).orEmpty()
        if (dao.loadItems().isEmpty()) {
            val migratedItems = (0 until legacyItems.length()).mapNotNull { index -> runCatching { legacyItems.getJSONObject(index) }.getOrNull()?.let { item ->
                CodeItemEntity(item.getLong("id"), item.getString("text"), item.getString("format"), item.optLong("createdAt", item.getLong("id")), item.optBoolean("favorite"), item.optString("folder", "默认"), item.optBoolean("inHistory", true))
            } }
            dao.saveItems(migratedItems)
        }
        if (dao.loadGroups().isEmpty()) {
            val groups = mutableListOf<FavoriteGroupEntity>()
            val links = mutableListOf<FavoriteGroupItemEntity>()
            for (index in 0 until legacyGroups.length()) runCatching { legacyGroups.getJSONObject(index) }.getOrNull()?.let { group ->
                val groupId = group.getLong("id")
                groups.add(FavoriteGroupEntity(groupId, group.optString("folder", "默认"), group.optString("name", "未命名收藏"), group.optLong("savedAt", groupId)))
                val itemIds = group.optJSONArray("itemIds") ?: JSONArray()
                for (itemIndex in 0 until itemIds.length()) links.add(FavoriteGroupItemEntity(groupId, itemIds.getLong(itemIndex)))
            }
            dao.saveGroups(groups)
            dao.saveGroupItems(links)
        }
        if (dao.loadFolders().isEmpty()) dao.saveFolders(legacyFolders.filter { it.isNotBlank() && it != "默认" }.map(::FavoriteFolderEntity))
        legacyPrefs.edit().putBoolean("room_data_migrated", true).remove("items").remove("favorite_groups").remove("favorite_folders").remove("next_item_id").remove("next_group_id").apply()
    }
