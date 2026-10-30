package com.luckyalanzhou.barcodegenerator.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 负责历史记录相关操作，并从共享条码数据会话发布快照。 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dataSession: LibraryDataSession,
    private val persistence: BarcodePersistenceCoordinator,
) : ViewModel() {
    private val coordinator = HistoryCoordinator(dataSession.store) { items ->
        persistence.persistItems(items)
    }

    val dataState: StateFlow<BarcodeDataState> = dataSession.dataState
    private val mutableRefreshGeneration = MutableStateFlow(0L)
    val refreshGeneration: StateFlow<Long> = mutableRefreshGeneration.asStateFlow()

    fun refreshHistory() {
        dataSession.publishDataState()
        mutableRefreshGeneration.update { it + 1 }
    }

    fun deleteHistoryBatch(batch: List<CodeItem>) {
        runLibraryMutation {
            coordinator.deleteBatch(batch)
        }
    }

    fun clearHistoryAndPersist() {
        runLibraryMutation {
            coordinator.clearHistory()
            persistence.clearHistory()
        }
    }

    /** 历史写入会影响共享条码快照，需与收藏导入/编辑串行并等待写入回执。 */
    private fun runLibraryMutation(mutation: () -> Unit) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                persistence.withLibraryMutation {
                    persistence.awaitPendingWrites()
                    mutation()
                    dataSession.publishDataState()
                    persistence.awaitPendingWrites()
                }
            }
        }
    }
}
