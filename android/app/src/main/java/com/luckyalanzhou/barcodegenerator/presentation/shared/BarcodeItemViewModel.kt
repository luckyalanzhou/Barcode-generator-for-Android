package com.luckyalanzhou.barcodegenerator.presentation.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesQuerySession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 管理收藏与历史功能共用的条码条目变更。 */
@HiltViewModel
class BarcodeItemViewModel @Inject constructor(
    private val dataSession: LibraryDataSession,
    querySession: FavoritesQuerySession,
    private val persistence: BarcodePersistenceCoordinator,
    private val appLogger: AppLogger,
) : ViewModel() {
    private val store = dataSession.store
    private val mutations = BarcodeItemMutationCoordinator(
        store = store,
        persistAllFavorites = {
            persistence.persistAllFavorites(
                store.itemsSnapshot(),
                store.groupsSnapshot(),
                store.foldersSnapshot(),
                store.loadedGroupLinkIdsSnapshot(),
            )
        },
        persistItems = { persistence.persistItems(store.itemsSnapshot()) },
        persistDeletion = { id, time -> persistence.deleteItem(id, time) },
    )
    private val favoritesQuerySession = querySession

    fun deleteBarcodeItem(itemId: Long) {
        withLibraryMutation {
            mutations.deleteItem(itemId)
            publishAfterMutation()
        }
    }

    fun updateBarcodeItem(itemId: Long, text: String, format: String) {
        withLibraryMutation {
            mutations.updateItem(itemId, text, format)
            publishAfterMutation()
        }
    }

    /** 无竞争时保持同步更新；导入等长事务持锁时，排队到新快照发布后再修改。 */
    private fun withLibraryMutation(block: () -> Unit) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // UI 已接收的编辑必须等导入刷新后提交，Activity 结束不能取消排队中的写入。
            withContext(NonCancellable) { persistence.withLibraryMutation(block) }
        }
    }

    private fun publishAfterMutation() {
        favoritesQuerySession.onLibraryMutation()
        viewModelScope.launch {
            try {
                favoritesQuerySession.refreshSearchAfterMutation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                appLogger.recordSafely(
                    "favorites",
                    "search refresh failed after barcode mutation",
                    diagnosticError = error,
                    primaryError = error,
                )
            }
        }
    }
}
