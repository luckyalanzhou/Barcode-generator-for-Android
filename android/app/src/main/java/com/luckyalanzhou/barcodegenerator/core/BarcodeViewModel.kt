package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppUiState(
    val page: String = "generate",
    val title: String = "条码生成器",
    val chromeVisible: Boolean = true,
    val selectedTab: Int = 0,
)

@HiltViewModel
class BarcodeViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /** 第一阶段兼容入口：旧业务仍可调用 render()，Compose 通过状态流接收结果。 */
    fun updateAppUi(page: String, title: String, chromeVisible: Boolean, selectedTab: Int) {
        _uiState.update {
            it.copy(
                page = page,
                title = title,
                chromeVisible = chromeVisible,
                selectedTab = selectedTab,
            )
        }
    }

    /** 页面唯一状态源；Activity 的兼容访问器直接转发到这里。 */
    var page: String
        get() = _uiState.value.page
        set(value) { _uiState.update { it.copy(page = value) } }
    var inputDraft: MutableList<String> = mutableListOf()
    var pendingGenerateFormat: String? = null
    var generateFormatName: String = "Code 128-B"
    var resultItems: List<CodeItem> = emptyList()
    var showingHistoryResult: Boolean = false
    var resultsReturnPage: String = "generate"
    var selectedFavoriteGroup: FavoriteGroup? = null
    var collapsedFavoriteFolders: MutableSet<String> = mutableSetOf()
    var favoriteTreeInitialized: Boolean = false
    var settingsReturnPage: String = "generate"
    var startupUpdateCheckStarted: Boolean = false
    var availableUpdateUrl: String? = null
    var availableUpdateExpectedSize: Long? = null
    var availableUpdateSha256: String? = null
    var updateDialogShowing: Boolean = false
    var updateDownloadRunning: Boolean = false
    var updateDownloadGeneration: Long = 0L
    var pendingInstallPath: String? = null
}
