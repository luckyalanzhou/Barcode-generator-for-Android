package com.luckyalanzhou.barcodegenerator.data


import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(
    tableName = "code_items",
    indices = [Index(value = ["inHistory", "createdAt"]), Index(value = ["favorite"])],
)
data class CodeItemEntity(
    @PrimaryKey val id: Long,
    val text: String,
    val format: String,
    val createdAt: Long,
    val favorite: Boolean,
    val folder: String,
    val inHistory: Boolean
)

@Entity(
    tableName = "favorite_groups",
    indices = [Index(value = ["folder", "savedAt"]), Index(value = ["name"])],
)
data class FavoriteGroupEntity(
    @PrimaryKey val id: Long,
    val folder: String,
    val name: String,
    val savedAt: Long
)

@Entity(
    tableName = "favorite_group_items",
    primaryKeys = ["groupId", "itemId"],
    indices = [Index(value = ["groupId"]), Index(value = ["itemId"])],
)
data class FavoriteGroupItemEntity(val groupId: Long, val itemId: Long)

@Entity(tableName = "favorite_folders")
data class FavoriteFolderEntity(@PrimaryKey val name: String)

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM code_items ORDER BY createdAt DESC, id DESC") suspend fun loadItems(): List<CodeItemEntity>
    @Query("SELECT * FROM code_items WHERE inHistory = 1 ORDER BY createdAt DESC, id DESC LIMIT 500") suspend fun loadStartupItems(): List<CodeItemEntity>
    @Query("SELECT * FROM code_items WHERE id IN (:ids)") suspend fun loadItemsByIds(ids: List<Long>): List<CodeItemEntity>
    @Query("SELECT DISTINCT ci.* FROM code_items ci INNER JOIN favorite_group_items gi ON gi.itemId = ci.id WHERE ci.favorite = 1 AND lower(ci.text) LIKE '%' || lower(:query) || '%' ORDER BY ci.createdAt DESC, ci.id DESC") suspend fun searchFavoriteItems(query: String): List<CodeItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveItems(items: List<CodeItemEntity>)
    @Query("DELETE FROM code_items") suspend fun clearItems()
    @Query("DELETE FROM code_items WHERE favorite = 0") suspend fun clearNonFavoriteItems()
    @Query("DELETE FROM code_items WHERE favorite = 0 AND id NOT IN (:retainedIds)") suspend fun deleteNonFavoriteItemsExcept(retainedIds: List<Long>)
    @Query("DELETE FROM code_items WHERE id NOT IN (:retainedIds)") suspend fun deleteItemsExcept(retainedIds: List<Long>)
    @Query("UPDATE code_items SET favorite = 0, folder = '' WHERE id IN (:ids)") suspend fun clearFavoriteFlags(ids: List<Long>)
    @Query("UPDATE code_items SET favorite = 0, folder = '' WHERE favorite = 1") suspend fun clearAllFavoriteFlags()
    @Query("UPDATE code_items SET favorite = 0, folder = '' WHERE id IN (SELECT itemId FROM favorite_group_items WHERE groupId IN (:groupIds)) AND NOT EXISTS (SELECT 1 FROM favorite_group_items remaining WHERE remaining.itemId = code_items.id AND remaining.groupId NOT IN (:groupIds))") suspend fun clearFavoriteFlagsForGroups(groupIds: List<Long>)

    @Query("SELECT * FROM favorite_groups ORDER BY savedAt DESC, id DESC") suspend fun loadGroups(): List<FavoriteGroupEntity>
    @Query("SELECT * FROM favorite_groups WHERE :cursorSavedAt IS NULL OR savedAt < :cursorSavedAt OR (savedAt = :cursorSavedAt AND id < :cursorId) ORDER BY savedAt DESC, id DESC LIMIT :limit")
    suspend fun loadGroupsPage(limit: Int, cursorSavedAt: Long?, cursorId: Long?): List<FavoriteGroupEntity>
    @Query("SELECT * FROM favorite_groups WHERE id IN (:ids) ORDER BY savedAt DESC, id DESC") suspend fun loadGroupsByIds(ids: List<Long>): List<FavoriteGroupEntity>
    @Query("SELECT id FROM favorite_groups WHERE lower(name) LIKE '%' || lower(:query) || '%' OR lower(folder) LIKE '%' || lower(:query) || '%'") suspend fun searchFavoriteGroupIdsByMetadata(query: String): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGroups(groups: List<FavoriteGroupEntity>)
    @Query("DELETE FROM favorite_groups") suspend fun clearGroups()
    @Query("DELETE FROM favorite_groups WHERE id NOT IN (:retainedIds)") suspend fun deleteGroupsExcept(retainedIds: List<Long>)
    @Query("DELETE FROM favorite_groups WHERE id IN (:ids)") suspend fun deleteGroups(ids: List<Long>)
    @Query("SELECT id FROM favorite_groups WHERE folder = :path OR folder LIKE :prefix") suspend fun loadGroupIdsByFolder(path: String, prefix: String): List<Long>
    @Query("UPDATE favorite_groups SET folder = CASE WHEN folder = :path THEN :renamedPath ELSE :renamedPath || substr(folder, length(:path) + 1) END WHERE folder = :path OR folder LIKE :prefix") suspend fun renameGroupsFolder(path: String, prefix: String, renamedPath: String)
    @Query("DELETE FROM favorite_groups WHERE folder = :path OR folder LIKE :prefix") suspend fun deleteGroupsByFolder(path: String, prefix: String)
    @Query("SELECT * FROM favorite_group_items") suspend fun loadGroupItems(): List<FavoriteGroupItemEntity>
    @Query("SELECT itemId FROM favorite_group_items WHERE groupId = :groupId ORDER BY itemId") suspend fun loadGroupItemIds(groupId: Long): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGroupItems(items: List<FavoriteGroupItemEntity>)
    @Query("DELETE FROM favorite_group_items") suspend fun clearGroupItems()
    @Query("DELETE FROM favorite_group_items WHERE groupId NOT IN (:retainedGroupIds)") suspend fun deleteGroupItemsExcept(retainedGroupIds: List<Long>)
    @Query("DELETE FROM favorite_group_items WHERE groupId IN (:groupIds)") suspend fun clearGroupItemsForGroups(groupIds: List<Long>)
    @Query("DELETE FROM favorite_group_items WHERE groupId IN (:groupIds)") suspend fun deleteGroupItems(groupIds: List<Long>)
    @Query("SELECT DISTINCT groupId FROM favorite_group_items links INNER JOIN code_items items ON items.id = links.itemId WHERE lower(items.text) LIKE '%' || lower(:query) || '%'") suspend fun searchFavoriteGroupIdsByContent(query: String): List<Long>

    @Query("SELECT * FROM favorite_folders ORDER BY name") suspend fun loadFolders(): List<FavoriteFolderEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFolders(folders: List<FavoriteFolderEntity>)
    @Query("DELETE FROM favorite_folders") suspend fun clearFolders()
    @Query("UPDATE favorite_folders SET name = CASE WHEN name = :path THEN :renamedPath ELSE :renamedPath || substr(name, length(:path) + 1) END WHERE name = :path OR name LIKE :prefix") suspend fun renameFolders(path: String, prefix: String, renamedPath: String)
    @Query("DELETE FROM favorite_folders WHERE name = :path OR name LIKE :prefix") suspend fun deleteFoldersByPath(path: String, prefix: String)
}

@Database(
    entities = [CodeItemEntity::class, FavoriteGroupEntity::class, FavoriteGroupItemEntity::class, FavoriteFolderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BarcodeDatabase : RoomDatabase() {
    abstract fun barcodeDao(): BarcodeDao

    companion object {
        fun create(context: Context): BarcodeDatabase = Room.databaseBuilder(
            context.applicationContext,
            BarcodeDatabase::class.java,
            "barcode_generator.db"
        ).addMigrations(MIGRATION_1_2).build()

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_code_items_inHistory_createdAt ON code_items(inHistory, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_code_items_favorite ON code_items(favorite)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_groups_folder_savedAt ON favorite_groups(folder, savedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_groups_name ON favorite_groups(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_groupId ON favorite_group_items(groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_itemId ON favorite_group_items(itemId)")
            }
        }
    }
}
