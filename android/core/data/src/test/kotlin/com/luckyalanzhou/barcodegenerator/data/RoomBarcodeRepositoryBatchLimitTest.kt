package com.luckyalanzhou.barcodegenerator.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RoomBarcodeRepositoryBatchLimitTest {
    @Test
    fun largeFavoriteRepositoryOperationsStayWithinSqliteVariableLimit() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val statements = Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
        val database = Room.inMemoryDatabaseBuilder(context, BarcodeDatabase::class.java)
            .setQueryCallback({ sql, bindArgs -> statements += sql to bindArgs.size }, Executor { it.run() })
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.barcodeDao()
            val items = (1L..1_000L).map { id ->
                CodeItemEntity(
                    id = id,
                    text = "item-$id",
                    format = "Code 128-B",
                    createdAt = id,
                    favorite = true,
                    folder = "Root",
                    inHistory = false,
                )
            }
            val groups = items.map { item ->
                FavoriteGroupEntity(item.id, "Root", "file-${item.id}", item.id)
            }
            val links = items.map { item -> FavoriteGroupItemEntity(item.id, item.id, 0) }
            dao.upsertItems(items)
            dao.upsertGroups(groups)
            dao.saveGroupItems(links)
            dao.saveFolders(listOf(FavoriteFolderEntity("Root")))
            val repository = RoomBarcodeRepository(database)
            val snapshot = repository.loadSnapshot()

            fun assertQueriesWithinSqliteLimit(operation: String) {
                val oversizedStatements = synchronized(statements) {
                    statements.filter { (_, bindCount) -> bindCount > 999 }
                }
                assertTrue("$operation generated SQL above 999 parameters", oversizedStatements.isEmpty())
                statements.clear()
            }

            statements.clear()
            repository.saveAll(snapshot)
            assertQueriesWithinSqliteLimit("saveAll")
            assertEquals(1_000, dao.loadItems().size)
            assertEquals(1_000, dao.loadGroups().size)

            statements.clear()
            repository.saveFavoriteGroups(snapshot.groups, snapshot.links)
            assertQueriesWithinSqliteLimit("saveFavoriteGroups")
            assertEquals(1_000, dao.loadGroups().size)
            assertEquals(1_000, dao.loadGroupItems().size)

            statements.clear()
            val loadedGroups = repository.loadFavoriteGroupsByIds(items.map { it.id })
            assertEquals(1_000, loadedGroups.size)
            assertEquals(1_000L, loadedGroups.first().id)
            assertQueriesWithinSqliteLimit("loadFavoriteGroupsByIds")

            statements.clear()
            repository.deleteFavoriteFolder("Root")
            assertQueriesWithinSqliteLimit("deleteFavoriteFolder")
            assertTrue(dao.loadGroups().isEmpty())
            assertTrue(dao.loadGroupItems().isEmpty())
            assertTrue(dao.loadItems().isEmpty())
            assertTrue(dao.loadFolders().isEmpty())

            val reducedSnapshot = snapshot.copy(
                items = snapshot.items.filter { it.id == 1_000L },
                groups = snapshot.groups.filter { it.id == 1_000L },
                links = snapshot.links.filter { it.groupId == 1_000L },
            )
            statements.clear()
            repository.saveAll(snapshot)
            assertQueriesWithinSqliteLimit("saveAll restore")
            statements.clear()
            repository.saveAll(reducedSnapshot)
            assertQueriesWithinSqliteLimit("saveAll prune")
            assertEquals(listOf(1_000L), dao.loadItems().map { it.id })
            assertEquals(listOf(1_000L), dao.loadGroups().map { it.id })
            assertEquals(1, dao.loadGroupItems().size)

            statements.clear()
            repository.saveAll(snapshot)
            assertQueriesWithinSqliteLimit("saveAll restore before group save")
            statements.clear()
            repository.saveFavoriteGroups(reducedSnapshot.groups, reducedSnapshot.links)
            assertQueriesWithinSqliteLimit("saveFavoriteGroups prune")
            assertEquals(listOf(1_000L), dao.loadItems().map { it.id })
            assertEquals(listOf(1_000L), dao.loadGroups().map { it.id })
            assertEquals(1, dao.loadGroupItems().size)

            statements.clear()
            repository.saveAll(snapshot.copy(groups = emptyList(), links = emptyList()))
            assertQueriesWithinSqliteLimit("saveAll without favorite groups")
            assertEquals(1_000, dao.loadItems().size)
            assertTrue(dao.loadGroups().isEmpty())
            assertTrue(dao.loadGroupItems().isEmpty())
        } finally {
            database.close()
        }
    }
}
