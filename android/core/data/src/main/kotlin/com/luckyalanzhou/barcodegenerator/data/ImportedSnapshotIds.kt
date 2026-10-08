package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot

/** Called inside the import transaction, before any old rows are removed. */
internal fun allocateImportedSnapshotIds(snapshot: BarcodeSnapshot, maxItemId: Long, maxGroupId: Long): BarcodeSnapshot {
    require(maxItemId >= 0 && maxItemId <= Long.MAX_VALUE - snapshot.items.size)
    require(maxGroupId >= 0 && maxGroupId <= Long.MAX_VALUE - snapshot.groups.size)
    require(snapshot.items.map { it.id }.distinct().size == snapshot.items.size)
    require(snapshot.groups.map { it.id }.distinct().size == snapshot.groups.size)
    val itemIds = snapshot.items.mapIndexed { index, item -> item.id to (maxItemId + index + 1L) }.toMap()
    val groupIds = snapshot.groups.mapIndexed { index, group -> group.id to (maxGroupId + index + 1L) }.toMap()
    return snapshot.copy(
        items = snapshot.items.map { it.copy(id = itemIds.getValue(it.id)) },
        groups = snapshot.groups.map { group -> group.copy(
            id = groupIds.getValue(group.id), itemIds = group.itemIds.map { itemIds.getValue(it) }.toMutableList(),
        ) },
        links = snapshot.links.map { it.copy(groupId = groupIds.getValue(it.groupId), itemId = itemIds.getValue(it.itemId)) },
        replaceGroupLinkIds = snapshot.replaceGroupLinkIds.map { groupIds.getValue(it) }.toSet(),
    )
}
