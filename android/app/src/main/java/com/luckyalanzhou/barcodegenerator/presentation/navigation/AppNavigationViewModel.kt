package com.luckyalanzhou.barcodegenerator.presentation.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.luckyalanzhou.barcodegenerator.presentation.AppUiState

/** 管理 Compose 主界面与 Activity 桥接层共用的全局页面状态。 */
@HiltViewModel
class AppNavigationViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val navigationStateCoordinator = NavigationStateCoordinator(_uiState)

    fun navigateTo(route: NavigationRoute, fromTabSwipe: Boolean = false) {
        navigationStateCoordinator.navigateTo(route, fromTabSwipe)
    }

    /** 将 Activity 恢复的页面状态同步到主界面的页面状态源。 */
    fun syncNavigationStateFromUi(route: NavigationRoute, fromTabSwipe: Boolean = false) {
        navigationStateCoordinator.navigateTo(route, fromTabSwipe)
    }

    fun updateSettingsReturnPage(route: NavigationRoute) {
        navigationStateCoordinator.updateSettingsReturnPage(route)
    }
}
