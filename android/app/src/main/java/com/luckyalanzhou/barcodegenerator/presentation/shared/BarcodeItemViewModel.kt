package com.luckyalanzhou.barcodegenerator.presentation.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesDataSession
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesQuerySession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Owns barcode-item mutations shared by the Favorites and History features. */
@HiltViewModel
class BarcodeItemViewModel @Inject constructor(
    private val dataSession: FavoritesDataSession,
    querySession: FavoritesQuerySession,
    persistence: BarcodePersistenceCoordinator,
) : ViewModel() {
    private val store = dataSession.store
    private val mutations = BarcodeItemMutationCoordinator(
        store = store,
        persistAllFavorites = {
            persistence.persistAllFavorites(
                viewModelScope,
                store.itemsSnapshot(),
                store.groupsSnapshot(),
                store.foldersSnapshot(),
                store.loadedGroupLinkIdsSnapshot(),
            )
        },
        persistItems = { persistence.persistItems(viewModelScope, store.itemsSnapshot()) },
    )
    private val queryCoordinator = querySession.coordinator

    fun deleteBarcodeItem(itemId: Long) {
        mutations.deleteItem(itemId)
        publishAfterMutation()
    }

    fun updateBarcodeItem(itemId: Long, text: String, format: String) {
        mutations.updateItem(itemId, text, format)
        publishAfterMutation()
    }

    private fun publishAfterMutation() {
        queryCoordinator.onMutation()
        dataSession.publishDataState()
    }
}
