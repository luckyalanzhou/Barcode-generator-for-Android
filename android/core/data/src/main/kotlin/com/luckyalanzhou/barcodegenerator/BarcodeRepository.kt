package com.luckyalanzhou.barcodegenerator

import androidx.room.withTransaction

/** Room 数据访问边界；UI 和 Activity 不直接负责数据库事务细节。 */
class BarcodeRepository(private val database: BarcodeDatabase) {
    private val dao get() = database.barcodeDao()

    suspend fun saveFavoriteFolders(folders: List<FavoriteFolderEntity>) {
        database.withTransaction {
            dao.clearFolders()
            dao.saveFolders(folders)
        }
    }

    suspend fun saveAllFavorites(
        items: List<CodeItemEntity>,
        groups: List<FavoriteGroupEntity>,
        links: List<FavoriteGroupItemEntity>,
        folders: List<FavoriteFolderEntity>,
    ) {
        database.withTransaction {
            dao.clearGroupItems()
            dao.clearGroups()
            dao.clearItems()
            dao.clearFolders()
            dao.saveItems(items)
            dao.saveGroups(groups)
            dao.saveGroupItems(links)
            dao.saveFolders(folders)
        }
    }

    suspend fun saveItems(items: List<CodeItemEntity>) {
        database.withTransaction {
            dao.clearItems()
            dao.saveItems(items)
        }
    }

    suspend fun saveFavoriteGroups(
        groups: List<FavoriteGroupEntity>,
        links: List<FavoriteGroupItemEntity>,
    ) {
        database.withTransaction {
            dao.clearGroupItems()
            dao.clearGroups()
            dao.saveGroups(groups)
            dao.saveGroupItems(links)
        }
    }

    suspend fun loadItems() = dao.loadItems()
    suspend fun loadGroups() = dao.loadGroups()
    suspend fun loadGroupItems() = dao.loadGroupItems()
    suspend fun loadFolders() = dao.loadFolders()

    suspend fun loadTransferEntities() = TransferEntities(
        items = dao.loadItems(),
        groups = dao.loadGroups(),
        links = dao.loadGroupItems(),
        folders = dao.loadFolders(),
    )

    suspend fun appendTransfer(transfer: TransferEntities) {
        database.withTransaction {
            dao.saveItems(transfer.items)
            dao.saveGroups(transfer.groups)
            dao.saveGroupItems(transfer.links)
            dao.saveFolders(transfer.folders)
        }
    }
}

