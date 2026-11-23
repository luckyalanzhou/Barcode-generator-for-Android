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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 负责历史记录相关操作，并从共享条码数据会话发布快照。 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dataSession: LibraryDataSession,
    private val persistence: BarcodePersistenceCoordinator,
) : ViewModel() {
    private val coordinator = HistoryCoordinator(dataSession.store) { batchId, createdAt ->
        persistence.deleteHistoryBatch(batchId, createdAt)
    }

    val dataState: StateFlow<BarcodeDataState> = dataSession.dataState
    private val mutableRefreshGeneration = MutableStateFlow(0L)
    val refreshGeneration: StateFlow<Long> = mutableRefreshGeneration.asStateFlow()
    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    internal data class HistorySearch(val term: String = "", val items: List<CodeItem>? = null, val failed: Boolean = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val search = combine(query, refreshGeneration) { value, generation -> value.trim() to generation }
        .flatMapLatest { (term, _) ->
            if (term.isEmpty()) flowOf(HistorySearch())
            else persistence.observeHistoryItems()
                .map { items -> HistorySearch(term, matchingHistoryItems(items, term)) }
                .flowOn(Dispatchers.Default)
                .onStart { emit(HistorySearch(term)) }
                .catch { emit(HistorySearch(term, failed = true)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), HistorySearch())

    fun updateQuery(value: String) {
        mutableQuery.value = value
    }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dataSession.publishDataState()
            mutableRefreshGeneration.update { it + 1 }
        }
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
        viewModelScope.launch(Dispatchers.IO) {
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

internal fun matchingHistoryItems(items: List<CodeItem>, term: String): List<CodeItem> {
    fun CodeItem.batchKey() = historyBatchId.takeIf(String::isNotBlank) ?: createdAt
    val matching = items.asSequence().filter { it.text.contains(term, ignoreCase = true) }
        .map { it.batchKey() }.toSet()
    return items.filter { it.batchKey() in matching }
}
