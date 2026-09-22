package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.luckyalanzhou.barcodegenerator.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/** History route/state adapter. All view rendering remains in [HistoryComposePage]. */
@Composable
internal fun HistoryScreen(
    dataState: BarcodeDataState,
    dark: Boolean,
    onClear: () -> Unit,
    onOpen: (List<CodeItem>) -> Unit,
    onEdit: (List<CodeItem>) -> Unit,
    onDelete: (List<CodeItem>) -> Unit,
) {
    val entries = remember(dataState.items) {
        dataState.items
            .asSequence()
            .filter { it.inHistory }
            .map { it.copy() }
            .groupBy { it.createdAt }
            .toList()
            .sortedByDescending { it.first }
    }

    HistoryComposePage(
        entries = entries,
        dark = dark,
        onClear = onClear,
        onOpen = onOpen,
        onEdit = onEdit,
        onDelete = onDelete,
        timeText = ::formatHistoryTime,
    )
}
