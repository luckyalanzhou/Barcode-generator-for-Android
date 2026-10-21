package com.luckyalanzhou.barcodegenerator.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BarcodeDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun migrationFromVersion3PreservesRowsAndBackfillsOrderedLinks() {
        createLegacyDatabase(3)
        assertRoomCanOpenAndReadMigratedFixture()
    }

    @Test
    fun migrationFromVersion4PreservesRowsAndBackfillsOrderedLinks() {
        createLegacyDatabase(4)
        assertRoomCanOpenAndReadMigratedFixture()
    }

    @Test
    fun migrationFromVersion5BackfillsOrderedLinks() {
        createLegacyDatabase(5)
        assertRoomCanOpenAndReadMigratedFixture()
    }

    @Test
    fun clearHistoryIncludesRowsBeyondStartupLimitAndPreservesFavoriteLinks() = runBlocking {
        val database = Room.databaseBuilder(context, BarcodeDatabase::class.java, TEST_DATABASE)
            .allowMainThreadQueries()
            .addMigrations(
                BarcodeDatabase.MIGRATION_1_2,
                BarcodeDatabase.MIGRATION_2_3,
                BarcodeDatabase.MIGRATION_3_4,
                BarcodeDatabase.MIGRATION_4_5,
                BarcodeDatabase.MIGRATION_5_6,
            )
            .build()
        try {
            val repository = RoomBarcodeRepository(database)
            val items = (1L..600L).map { id ->
                val favorite = id > 500L
                com.luckyalanzhou.barcodegenerator.domain.CodeItem(
                    id = id,
                    text = "item-$id",
                    format = "Code 128-B",
                    createdAt = id,
                    favorite = favorite,
                    folder = if (favorite) "Saved" else "",
                    inHistory = true,
                )
            }
            val group = com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup(1L, "", "Saved", 1L, (501L..600L).toMutableList())
            val links = group.itemIds.mapIndexed { position, itemId ->
                com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem(group.id, itemId, position)
            }
            repository.saveAll(
                com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot(items, listOf(group), links, emptyList()),
            )

            assertEquals(500, repository.loadStartupSnapshot().items.size)
            repository.clearHistory()

            assertEquals(emptyList<com.luckyalanzhou.barcodegenerator.domain.CodeItem>(), repository.loadStartupSnapshot().items)
            assertEquals((501L..600L).toList(), repository.loadGroupItemIds(group.id))
            assertEquals((501L..600L).toList().reversed(), repository.loadItems().map { it.id })
            assertEquals(false, repository.loadItems().any { it.inHistory })
        } finally {
            database.close()
        }
    }

    @Test
    fun invalidLegacyRowKeepsOriginalPreferencesAndDoesNotPartiallyWriteRoom() = runBlocking {
        val preferences = context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE)
        val legacyItems = """[{"id":1,"text":"valid","format":"Code 128-B"},{"id":2,"text":"invalid"}]"""
        preferences.edit().clear().putString("items", legacyItems).commit()
        val database = currentRoomDatabase()
        try {
            LegacyBarcodeDataMigrator(context, RoomBarcodeRepository(database)).migrateIfNeeded()

            assertTrue(preferences.contains("items"))
            assertEquals(legacyItems, preferences.getString("items", null))
            assertFalse(preferences.getBoolean("room_data_migrated", false))
            assertTrue(RoomBarcodeRepository(database).loadItems().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun successfulLegacyMigrationCommitsRoomDataBeforeRemovingPreferences() = runBlocking {
        val preferences = context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putString("items", """[{"id":1,"text":"legacy","format":"Code 128-B","inHistory":true}]""")
            .putString("favorite_groups", "[]")
            .commit()
        val database = currentRoomDatabase()
        try {
            LegacyBarcodeDataMigrator(context, RoomBarcodeRepository(database)).migrateIfNeeded()

            assertEquals(true, preferences.getBoolean("room_data_migrated", false))
            assertFalse(preferences.contains("items"))
            assertEquals("legacy", RoomBarcodeRepository(database).loadItems().single().text)
        } finally {
            database.close()
        }
    }

    private fun currentRoomDatabase() = Room.databaseBuilder(context, BarcodeDatabase::class.java, TEST_DATABASE)
        .allowMainThreadQueries()
        .addMigrations(
            BarcodeDatabase.MIGRATION_1_2,
            BarcodeDatabase.MIGRATION_2_3,
            BarcodeDatabase.MIGRATION_3_4,
            BarcodeDatabase.MIGRATION_4_5,
            BarcodeDatabase.MIGRATION_5_6,
        )
        .build()

    private fun createLegacyDatabase(version: Int) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DATABASE)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createSchemaFromExportedSnapshot(db, version)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        error("Unexpected upgrade $oldVersion->$newVersion while creating fixture")
                })
                .build(),
        )

        try {
            helper.writableDatabase.use { database ->
                insertFavoriteFixture(database)
            }
        } finally {
            helper.close()
        }
    }

    private fun createSchemaFromExportedSnapshot(database: SupportSQLiteDatabase, version: Int) {
        val schemaFile = File(
            "schemas/com.luckyalanzhou.barcodegenerator.data.BarcodeDatabase/$version.json",
        )
        check(schemaFile.isFile) { "Missing Room schema snapshot: ${schemaFile.absolutePath}" }
        val entities = JSONObject(schemaFile.readText())
            .getJSONObject("database")
            .getJSONArray("entities")

        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val tableName = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            val indices = entity.optJSONArray("indices") ?: continue
            for (indexInEntity in 0 until indices.length()) {
                val createIndexSql = indices.getJSONObject(indexInEntity).getString("createSql")
                database.execSQL(createIndexSql.replace("\${TABLE_NAME}", tableName))
            }
        }
    }

    private fun insertFavoriteFixture(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO code_items(id, text, format, createdAt, favorite, folder, inHistory) VALUES " +
                "(1, 'first', 'Code 128', 100, 1, 'Root', 1), " +
                "(2, 'second', 'Code 128', 200, 1, 'Root', 0)",
        )
        database.execSQL(
            "INSERT INTO favorite_groups(id, folder, name, savedAt) VALUES " +
                "(10, 'Root', 'Group A', 300), (20, 'Root/Sub', 'Group B', 400)",
        )
        // Insert out of order; the migration's documented fallback order is itemId ascending.
        database.execSQL(
            "INSERT INTO favorite_group_items(groupId, itemId) VALUES " +
                "(10, 2), (10, 1), (20, 2)",
        )
        database.execSQL("INSERT INTO favorite_folders(name) VALUES ('Root'), ('Root/Sub')")
    }

    private fun assertRoomCanOpenAndReadMigratedFixture() {
        val database = Room.databaseBuilder(context, BarcodeDatabase::class.java, TEST_DATABASE)
            .allowMainThreadQueries()
            .addMigrations(
                BarcodeDatabase.MIGRATION_1_2,
                BarcodeDatabase.MIGRATION_2_3,
                BarcodeDatabase.MIGRATION_3_4,
                BarcodeDatabase.MIGRATION_4_5,
                BarcodeDatabase.MIGRATION_5_6,
            )
            .build()
        try {
            runBlocking {
                val dao = database.barcodeDao()
                assertEquals(
                    listOf(
                        FavoriteGroupItemEntity(groupId = 10, itemId = 1, position = 0),
                        FavoriteGroupItemEntity(groupId = 10, itemId = 2, position = 1),
                        FavoriteGroupItemEntity(groupId = 20, itemId = 2, position = 0),
                    ),
                    dao.loadGroupItems(),
                )
                assertEquals(
                    listOf(
                        CodeItemEntity(2, "second", "Code 128", 200, true, "Root", false),
                        CodeItemEntity(1, "first", "Code 128", 100, true, "Root", true),
                    ),
                    dao.loadItems(),
                )
                assertEquals(
                    listOf(
                        FavoriteGroupEntity(20, "Root/Sub", "Group B", 400),
                        FavoriteGroupEntity(10, "Root", "Group A", 300),
                    ),
                    dao.loadGroups(),
                )
                assertEquals(
                    listOf(FavoriteFolderEntity("Root"), FavoriteFolderEntity("Root/Sub")),
                    dao.loadFolders(),
                )
            }
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
                assertEquals(0, it.count)
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "barcode-migration-test"
    }
}
