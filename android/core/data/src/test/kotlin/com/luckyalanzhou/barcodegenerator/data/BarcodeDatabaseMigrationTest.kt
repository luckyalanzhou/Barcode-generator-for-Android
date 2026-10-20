package com.luckyalanzhou.barcodegenerator.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
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
