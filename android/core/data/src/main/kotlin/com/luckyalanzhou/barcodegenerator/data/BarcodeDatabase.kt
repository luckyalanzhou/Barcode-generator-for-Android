package com.luckyalanzhou.barcodegenerator.data


import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update

@Entity(
    tableName = "code_items",
    indices = [
        Index(value = ["inHistory", "createdAt"]),
        Index(value = ["favorite"]),
        Index(value = ["favorite", "createdAt", "id"]),
    ],
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
    indices = [
        Index(value = ["folder", "savedAt"]),
        Index(value = ["name"]),
        Index(value = ["savedAt", "id"]),
    ],
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
    indices = [Index(value = ["groupId", "position"], unique = true), Index(value = ["itemId"])],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CodeItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class FavoriteGroupItemEntity(val groupId: Long, val itemId: Long, val position: Int)

@Entity(tableName = "favorite_folders")
data class FavoriteFolderEntity(@PrimaryKey val name: String)

@Dao
interface BarcodeDao {
    @Query("SELECT COALESCE(MAX(id), 0) FROM code_items") suspend fun maxItemId(): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNewItems(items: List<CodeItemEntity>)
    @Query("SELECT COALESCE(MAX(id), 0) FROM favorite_groups") suspend fun maxGroupId(): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNewGroups(groups: List<FavoriteGroupEntity>)
    @Query("UPDATE code_items SET favorite = EXISTS (SELECT 1 FROM favorite_group_items gi WHERE gi.itemId = code_items.id), folder = CASE WHEN EXISTS (SELECT 1 FROM favorite_group_items gi WHERE gi.itemId = code_items.id) THEN folder ELSE '' END")
    suspend fun reconcileFavoriteFlags()
    @Query("DELETE FROM favorite_group_items WHERE itemId = :itemId") suspend fun deleteItemLinks(itemId: Long)
    @Query("DELETE FROM code_items WHERE id = :itemId") suspend fun deleteItem(itemId: Long)
    @Query("UPDATE favorite_groups SET savedAt = :savedAt WHERE id IN (SELECT groupId FROM favorite_group_items WHERE itemId = :itemId)")
    suspend fun touchGroupsForItem(itemId: Long, savedAt: Long)
    @Query("UPDATE favorite_groups SET name = :name, folder = :folder, savedAt = :savedAt WHERE id = :groupId")
    suspend fun updateGroupMetadata(groupId: Long, name: String, folder: String, savedAt: Long)
    @Query("SELECT * FROM code_items ORDER BY createdAt DESC, id DESC") suspend fun loadItems(): List<CodeItemEntity>
    @Query("SELECT id FROM code_items") suspend fun loadItemIds(): List<Long>
    @Query("SELECT DISTINCT itemId FROM favorite_group_items") suspend fun loadLinkedItemIds(): List<Long>
    @Query("SELECT * FROM code_items WHERE inHistory = 1 ORDER BY createdAt DESC, id DESC LIMIT 500") suspend fun loadStartupItems(): List<CodeItemEntity>
    @Query("SELECT * FROM code_items WHERE id IN (:ids)") suspend fun loadItemsByIds(ids: List<Long>): List<CodeItemEntity>
    @Query("SELECT DISTINCT ci.* FROM code_items ci INNER JOIN favorite_group_items gi ON gi.itemId = ci.id WHERE ci.favorite = 1 AND lower(ci.text) LIKE '%' || lower(:query) || '%' AND (:cursorCreatedAt IS NULL OR ci.createdAt < :cursorCreatedAt OR (ci.createdAt = :cursorCreatedAt AND ci.id < :cursorId)) ORDER BY ci.createdAt DESC, ci.id DESC LIMIT :limit") suspend fun searchFavoriteItems(query: String, limit: Int, cursorCreatedAt: Long?, cursorId: Long?): List<CodeItemEntity>
    // 不要对被 `favorite_group_items` 引用的记录使用 REPLACE：SQLite 会先删除再插入，导致收藏关联被级联删除。
    @Update(onConflict = OnConflictStrategy.IGNORE) suspend fun updateItems(items: List<CodeItemEntity>): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItemsIfMissing(items: List<CodeItemEntity>)
    @Transaction
    suspend fun upsertItems(items: List<CodeItemEntity>) {
        if (items.isEmpty()) return
        updateItems(items)
        insertItemsIfMissing(items)
    }
    @Query("DELETE FROM code_items") suspend fun clearItems()
    @Query("DELETE FROM code_items WHERE favorite = 0 AND NOT EXISTS (SELECT 1 FROM favorite_group_items gi WHERE gi.itemId = code_items.id)") suspend fun clearNonFavoriteItems()
    // 不在历史中且不再关联任何收藏文件的条码已不可从应用内访问，避免删除收藏后残留正文数据。
    @Query("DELETE FROM code_items WHERE inHistory = 0 AND NOT EXISTS (SELECT 1 FROM favorite_group_items gi WHERE gi.itemId = code_items.id)") suspend fun deleteUnreferencedItemsNotInHistory()
    @Query("DELETE FROM code_items WHERE id IN (:ids)") suspend fun deleteItemsByIds(ids: List<Long>)
    @Query("DELETE FROM favorite_group_items WHERE itemId IN (:itemIds)") suspend fun deleteGroupItemsByItemIds(itemIds: List<Long>)
    @Query("UPDATE code_items SET favorite = 0, folder = '' WHERE id IN (:ids)") suspend fun clearFavoriteFlags(ids: List<Long>)
    @Query("UPDATE code_items SET favorite = 0, folder = '' WHERE favorite = 1") suspend fun clearAllFavoriteFlags()
    @Query("UPDATE code_items SET inHistory = 0") suspend fun clearAllHistoryFlags()
    @Query("UPDATE code_items SET favorite = 0, folder = '' WHERE id IN (SELECT itemId FROM favorite_group_items WHERE groupId IN (:groupIds)) AND NOT EXISTS (SELECT 1 FROM favorite_group_items remaining WHERE remaining.itemId = code_items.id AND remaining.groupId NOT IN (:groupIds))") suspend fun clearFavoriteFlagsForGroups(groupIds: List<Long>)

    @Query("SELECT * FROM favorite_groups ORDER BY savedAt DESC, id DESC") suspend fun loadGroups(): List<FavoriteGroupEntity>
    @Query("SELECT id FROM favorite_groups") suspend fun loadGroupIds(): List<Long>
    @Query("SELECT DISTINCT groupId FROM favorite_group_items") suspend fun loadLinkedGroupIds(): List<Long>
    @Query("SELECT * FROM favorite_groups WHERE :cursorSavedAt IS NULL OR savedAt < :cursorSavedAt OR (savedAt = :cursorSavedAt AND id < :cursorId) ORDER BY savedAt DESC, id DESC LIMIT :limit")
    suspend fun loadGroupsPage(limit: Int, cursorSavedAt: Long?, cursorId: Long?): List<FavoriteGroupEntity>
    @Query("SELECT * FROM favorite_groups WHERE id IN (:ids) ORDER BY savedAt DESC, id DESC") suspend fun loadGroupsByIds(ids: List<Long>): List<FavoriteGroupEntity>
    @Query("SELECT * FROM favorite_groups WHERE id = :id LIMIT 1") suspend fun loadGroupById(id: Long): FavoriteGroupEntity?
    @Query("SELECT DISTINCT fg.* FROM favorite_groups AS fg LEFT JOIN favorite_group_items AS links ON links.groupId = fg.id LEFT JOIN code_items AS items ON items.id = links.itemId WHERE (lower(fg.name) LIKE '%' || lower(:query) || '%' OR lower(fg.folder) LIKE '%' || lower(:query) || '%' OR lower(items.text) LIKE '%' || lower(:query) || '%') AND (:cursorSavedAt IS NULL OR fg.savedAt < :cursorSavedAt OR (fg.savedAt = :cursorSavedAt AND fg.id < :cursorId)) ORDER BY fg.savedAt DESC, fg.id DESC LIMIT :limit") suspend fun searchFavoriteGroups(query: String, limit: Int, cursorSavedAt: Long?, cursorId: Long?): List<FavoriteGroupEntity>
    // 收藏分组同样是 `favorite_group_items` 的外键父项；应原位更新。
    @Update(onConflict = OnConflictStrategy.IGNORE) suspend fun updateGroups(groups: List<FavoriteGroupEntity>): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGroupsIfMissing(groups: List<FavoriteGroupEntity>)
    @Transaction
    suspend fun upsertGroups(groups: List<FavoriteGroupEntity>) {
        if (groups.isEmpty()) return
        updateGroups(groups)
        insertGroupsIfMissing(groups)
    }
    @Query("DELETE FROM favorite_groups") suspend fun clearGroups()
    @Query("DELETE FROM favorite_groups WHERE id IN (:ids)") suspend fun deleteGroups(ids: List<Long>)
    @Query("SELECT id FROM favorite_groups WHERE folder = :path OR substr(folder, 1, length(:path) + 1) = (:path || '/') COLLATE BINARY")
    suspend fun loadGroupIdsByFolder(path: String): List<Long>
    @Query("UPDATE favorite_groups SET folder = CASE WHEN folder = :path THEN :renamedPath ELSE :renamedPath || substr(folder, length(:path) + 1) END, savedAt = :savedAt WHERE folder = :path OR substr(folder, 1, length(:path) + 1) = (:path || '/') COLLATE BINARY")
    suspend fun renameGroupsFolder(path: String, renamedPath: String, savedAt: Long)
    @Query("DELETE FROM favorite_groups WHERE folder = :path OR substr(folder, 1, length(:path) + 1) = (:path || '/') COLLATE BINARY")
    suspend fun deleteGroupsByFolder(path: String)
    @Query("SELECT * FROM favorite_group_items ORDER BY groupId, position, itemId") suspend fun loadGroupItems(): List<FavoriteGroupItemEntity>
    @Query("SELECT * FROM favorite_group_items WHERE groupId IN (:groupIds) ORDER BY groupId, position, itemId") suspend fun loadGroupItemsByGroupIds(groupIds: List<Long>): List<FavoriteGroupItemEntity>
    @Query("SELECT itemId FROM favorite_group_items WHERE groupId = :groupId ORDER BY position, itemId") suspend fun loadGroupItemIds(groupId: Long): List<Long>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun saveGroupItems(items: List<FavoriteGroupItemEntity>)
    @Query("DELETE FROM favorite_group_items") suspend fun clearGroupItems()
    @Query("DELETE FROM favorite_group_items WHERE groupId IN (:groupIds)") suspend fun clearGroupItemsForGroups(groupIds: List<Long>)
    @Query("DELETE FROM favorite_group_items WHERE groupId IN (:groupIds)") suspend fun deleteGroupItems(groupIds: List<Long>)

    @Query("SELECT * FROM favorite_folders ORDER BY name") suspend fun loadFolders(): List<FavoriteFolderEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFolders(folders: List<FavoriteFolderEntity>)
    @Query("DELETE FROM favorite_folders") suspend fun clearFolders()
    @Query("UPDATE favorite_folders SET name = CASE WHEN name = :path THEN :renamedPath ELSE :renamedPath || substr(name, length(:path) + 1) END WHERE name = :path OR substr(name, 1, length(:path) + 1) = (:path || '/') COLLATE BINARY")
    suspend fun renameFolders(path: String, renamedPath: String)
    @Query("DELETE FROM favorite_folders WHERE name = :path OR substr(name, 1, length(:path) + 1) = (:path || '/') COLLATE BINARY")
    suspend fun deleteFoldersByPath(path: String)
}

@Database(
    entities = [CodeItemEntity::class, FavoriteGroupEntity::class, FavoriteGroupItemEntity::class, FavoriteFolderEntity::class],
    version = 6,
    exportSchema = true
)
abstract class BarcodeDatabase : RoomDatabase() {
    abstract fun barcodeDao(): BarcodeDao

    companion object {
        fun create(context: Context): BarcodeDatabase = Room.databaseBuilder(
            context.applicationContext,
            BarcodeDatabase::class.java,
            "barcode_generator.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()

        internal val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_code_items_inHistory_createdAt ON code_items(inHistory, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_code_items_favorite ON code_items(favorite)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_groups_folder_savedAt ON favorite_groups(folder, savedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_groups_name ON favorite_groups(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_groupId ON favorite_group_items(groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_itemId ON favorite_group_items(itemId)")
            }
        }

        internal val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_group_items_new (
                        groupId INTEGER NOT NULL,
                        itemId INTEGER NOT NULL,
                        PRIMARY KEY(groupId, itemId),
                        FOREIGN KEY(groupId) REFERENCES favorite_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(itemId) REFERENCES code_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO favorite_group_items_new(groupId, itemId)
                    SELECT links.groupId, links.itemId
                    FROM favorite_group_items links
                    INNER JOIN favorite_groups groups ON groups.id = links.groupId
                    INNER JOIN code_items items ON items.id = links.itemId
                """.trimIndent())
                db.execSQL("DROP TABLE favorite_group_items")
                db.execSQL("ALTER TABLE favorite_group_items_new RENAME TO favorite_group_items")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_groupId ON favorite_group_items(groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_itemId ON favorite_group_items(itemId)")
            }
        }

        internal val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_code_items_favorite_createdAt_id ON code_items(favorite, createdAt, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_groups_savedAt_id ON favorite_groups(savedAt, id)")
            }
        }

        /**
         * 防止意外删除父记录时静默断开收藏关联。
         * 仓库操作必须先显式删除关联，再删除父项。
         */
        internal val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE favorite_group_items_new (
                        groupId INTEGER NOT NULL,
                        itemId INTEGER NOT NULL,
                        PRIMARY KEY(groupId, itemId),
                        FOREIGN KEY(groupId) REFERENCES favorite_groups(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(itemId) REFERENCES code_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO favorite_group_items_new(groupId, itemId)
                    SELECT links.groupId, links.itemId
                    FROM favorite_group_items links
                    INNER JOIN favorite_groups groups ON groups.id = links.groupId
                    INNER JOIN code_items items ON items.id = links.itemId
                """.trimIndent())
                db.execSQL("DROP TABLE favorite_group_items")
                db.execSQL("ALTER TABLE favorite_group_items_new RENAME TO favorite_group_items")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_groupId ON favorite_group_items(groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_itemId ON favorite_group_items(itemId)")
            }
        }

        /**
         * 版本 5 未保存条码顺序；迁移按 itemId 确定性编号，使后续读取稳定。
         * 仅凭数据库结构无法还原旧版界面的展示顺序。
         */
        internal val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE favorite_group_items_new (
                        groupId INTEGER NOT NULL,
                        itemId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(groupId, itemId),
                        FOREIGN KEY(groupId) REFERENCES favorite_groups(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(itemId) REFERENCES code_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())

                db.compileStatement(
                    "INSERT INTO favorite_group_items_new(groupId, itemId, position) VALUES (?, ?, ?)"
                ).use { insert ->
                    db.query("SELECT groupId, itemId FROM favorite_group_items ORDER BY groupId, itemId").use { cursor ->
                        var currentGroupId: Long? = null
                        var position = 0
                        while (cursor.moveToNext()) {
                            val groupId = cursor.getLong(0)
                            if (currentGroupId != groupId) {
                                currentGroupId = groupId
                                position = 0
                            }
                            insert.clearBindings()
                            insert.bindLong(1, groupId)
                            insert.bindLong(2, cursor.getLong(1))
                            insert.bindLong(3, position.toLong())
                            insert.executeInsert()
                            position++
                        }
                    }
                }

                db.execSQL("DROP TABLE favorite_group_items")
                db.execSQL("ALTER TABLE favorite_group_items_new RENAME TO favorite_group_items")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_group_items_groupId_position ON favorite_group_items(groupId, position)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_items_itemId ON favorite_group_items(itemId)")
            }
        }
    }
}
