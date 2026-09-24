package com.luckyalanzhou.barcodegenerator.presentation.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.luckyalanzhou.barcodegenerator.presentation.AppUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal data class AppNavigationRequest(
    val route: NavigationRoute,
    val topLevelDestination: Boolean,
)

/** Owns the app-wide route state used by the Compose shell and Activity bridges. */
@HiltViewModel
class AppNavigationViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val navigationRequestChannel = Channel<AppNavigationRequest>(Channel.BUFFERED)
    internal val navigationRequests: Flow<AppNavigationRequest> = navigationRequestChannel.receiveAsFlow()
    private val routeFacade = AppRouteStateFacade(_uiState)

    fun navigateTo(
        route: NavigationRoute,
        fromTabSwipe: Boolean = false,
        topLevelDestination: Boolean = route.mainTabIndex != null && route != NavigationRoute.Settings,
    ) {
        routeFacade.navigateTo(route, fromTabSwipe)
        navigationRequestChannel.trySend(AppNavigationRequest(route, topLevelDestination))
    }

    /** Keeps Activity-restored route state in sync with the shell's route source. */
    fun syncNavigationStateFromUi(route: NavigationRoute, fromTabSwipe: Boolean = false) {
        routeFacade.navigateTo(route, fromTabSwipe)
    }

    fun updateSettingsReturnPage(route: NavigationRoute) {
        routeFacade.updateSettingsReturnPage(route)
    }
}
