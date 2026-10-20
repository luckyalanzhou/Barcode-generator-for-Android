package com.luckyalanzhou.barcodegenerator.presentation.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesQuerySession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Owns barcode-item mutations shared by the Favorites and History features. */
@HiltViewModel
class BarcodeItemViewModel @Inject constructor(
    private val dataSession: LibraryDataSession,
    querySession: FavoritesQuerySession,
    persistence: BarcodePersistenceCoordinator,
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
        mutations.deleteItem(itemId)
        publishAfterMutation()
    }

    fun updateBarcodeItem(itemId: Long, text: String, format: String) {
        mutations.updateItem(itemId, text, format)
        publishAfterMutation()
    }

    private fun publishAfterMutation() {
        favoritesQuerySession.onLibraryMutation()
        viewModelScope.launch {
            try {
                favoritesQuerySession.refreshSearchAfterMutation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                appLogger.record("favorites", "search refresh failed after barcode mutation", error)
            }
        }
    }
}
