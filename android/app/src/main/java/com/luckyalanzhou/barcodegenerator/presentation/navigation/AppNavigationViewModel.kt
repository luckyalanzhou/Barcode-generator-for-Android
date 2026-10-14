package com.luckyalanzhou.barcodegenerator.presentation.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.luckyalanzhou.barcodegenerator.presentation.AppUiState

/** Owns the app-wide route state used by the Compose shell and Activity bridges. */
@HiltViewModel
class AppNavigationViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val routeFacade = AppRouteStateFacade(_uiState)

    fun navigateTo(route: NavigationRoute, fromTabSwipe: Boolean = false) {
        routeFacade.navigateTo(route, fromTabSwipe)
    }

    /** Keeps Activity-restored route state in sync with the shell's route source. */
    fun syncNavigationStateFromUi(route: NavigationRoute, fromTabSwipe: Boolean = false) {
        routeFacade.navigateTo(route, fromTabSwipe)
    }

    fun updateSettingsReturnPage(route: NavigationRoute) {
        routeFacade.updateSettingsReturnPage(route)
    }
}
