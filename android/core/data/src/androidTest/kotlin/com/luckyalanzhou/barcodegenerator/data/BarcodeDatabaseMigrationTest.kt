package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarcodeDatabaseMigrationTest {
    @Test
    fun migrationChainAddsForeignKeysAndPreservesValidLinks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(V2SchemaCallback())
                .build(),
        )
        val database = helper.writableDatabase
        try {
            database.execSQL("INSERT INTO code_items VALUES (1, 'A', 'Code 128-B', 1, 1, '默认', 1)")
            database.execSQL("INSERT INTO favorite_groups VALUES (10, '一级', '收藏', 1)")
            database.execSQL("INSERT INTO favorite_group_items VALUES (10, 1)")
            database.execSQL("INSERT INTO favorite_group_items VALUES (999, 999)")

            BarcodeDatabase.MIGRATION_1_2.migrate(database)
            BarcodeDatabase.MIGRATION_2_3.migrate(database)
            BarcodeDatabase.MIGRATION_3_4.migrate(database)

            database.query("SELECT COUNT(*) FROM favorite_group_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            database.execSQL("PRAGMA foreign_keys = ON")
            database.execSQL("DELETE FROM favorite_groups WHERE id = 10")
            database.query("SELECT COUNT(*) FROM favorite_group_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("PRAGMA index_list('favorite_groups')").use { cursor ->
                val indexes = buildList {
                    val nameColumn = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
                assertTrue(indexes.contains("index_favorite_groups_savedAt_id"))
            }
        } finally {
            database.close()
            helper.close()
        }
    }

    private class V2SchemaCallback : SupportSQLiteOpenHelper.Callback(2) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE code_items (id INTEGER NOT NULL PRIMARY KEY, text TEXT NOT NULL, format TEXT NOT NULL, createdAt INTEGER NOT NULL, favorite INTEGER NOT NULL, folder TEXT NOT NULL, inHistory INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE favorite_groups (id INTEGER NOT NULL PRIMARY KEY, folder TEXT NOT NULL, name TEXT NOT NULL, savedAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE favorite_group_items (groupId INTEGER NOT NULL, itemId INTEGER NOT NULL, PRIMARY KEY(groupId, itemId))")
            db.execSQL("CREATE TABLE favorite_folders (name TEXT NOT NULL PRIMARY KEY)")
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
