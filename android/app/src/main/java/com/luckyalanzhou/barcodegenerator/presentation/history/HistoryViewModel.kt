package com.luckyalanzhou.barcodegenerator.presentation.history

import androidx.lifecycle.ViewModel
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Owns history-only actions and publishes snapshots from the shared barcode data session. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dataSession: LibraryDataSession,
    persistence: BarcodePersistenceCoordinator,
) : ViewModel() {
    private val coordinator = HistoryCoordinator(dataSession.store) { items ->
        persistence.persistItems(items)
    }

    val dataState: StateFlow<BarcodeDataState> = dataSession.dataState

    fun deleteHistoryBatch(batch: List<CodeItem>) {
        coordinator.deleteBatch(batch)
        dataSession.publishDataState()
    }

    fun clearHistoryAndPersist() {
        coordinator.clearHistory()
        dataSession.publishDataState()
    }
}
