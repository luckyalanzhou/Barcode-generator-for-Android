package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

/** Keeps one query cursor set aligned with the shared regular and search stores. */
@ActivityRetainedScoped
class FavoritesQuerySession @Inject constructor(
    repository: BarcodeRepository,
    dataSession: FavoritesDataSession,
) {
    internal val coordinator = FavoritesQueryCoordinator(
        repository = repository,
        store = dataSession.store,
        searchStore = dataSession.searchStore,
    )
}
