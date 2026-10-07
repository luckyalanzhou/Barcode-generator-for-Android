package com.luckyalanzhou.barcodegenerator.ui.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/** History route/state adapter. All view rendering remains in [HistoryComposePage]. */
@Composable
internal fun HistoryScreen(
    items: List<CodeItem>,
    refreshGeneration: Long,
    dark: Boolean,
    onOpen: (List<CodeItem>) -> Unit,
    onEdit: (List<CodeItem>) -> Unit,
    onDelete: (List<CodeItem>) -> Unit,
) {
    val refreshTime = remember(refreshGeneration) { System.currentTimeMillis() }
    val entries = remember(items, refreshGeneration) {
        items
            .asSequence()
            .filter { it.inHistory }
            .map { it.copy() }
            .groupBy { it.createdAt }
            .toList()
            .sortedByDescending { it.first }
    }

    HistoryComposePage(
        entries = entries,
        refreshGeneration = refreshGeneration,
        dark = dark,
        onOpen = onOpen,
        onEdit = onEdit,
        onDelete = onDelete,
        timeText = { time -> formatHistoryTime(time, refreshTime) },
    )
}
