package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppUiState(
    val route: AppRoute = AppRoute.Generate,
    val selectedTab: Int = 0,
) {
    val page: String get() = route.pageName
    val title: String get() = route.title
    val chromeVisible: Boolean get() = route.chromeVisible
}

/**
 * 条码、收藏和文件夹的统一只读快照。
 *
 * Activity 仍保留兼容写入桥；Compose 页面只读取这个快照，避免多个 ViewModel
 * 分别保存一份容易过期的镜像。
 */
data class BarcodeDataState(
    val items: List<CodeItem> = emptyList(),
    val groups: List<FavoriteGroup> = emptyList(),
    val folders: List<String> = emptyList(),
)

@HiltViewModel
class BarcodeViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /** 条码与收藏的实际内存所有者；Activity 仅通过兼容访问器使用这些集合。 */
    internal val items = mutableListOf<CodeItem>()
    internal val favoriteGroups = mutableListOf<FavoriteGroup>()
    internal val favoriteFolders = mutableListOf<String>()

    private val _dataState = MutableStateFlow(BarcodeDataState())
    val dataState: StateFlow<BarcodeDataState> = _dataState.asStateFlow()

    /** 发布只读快照，页面不会直接观察可变集合。 */
    fun publishDataState() {
        _dataState.value = BarcodeDataState(
            items = items.map { it.copy() },
            groups = favoriteGroups.map { it.copy(itemIds = it.itemIds.toMutableList()) },
            folders = favoriteFolders.toList(),
        )
    }

    /** 第一阶段兼容入口：旧业务仍可调用 render()，Compose 通过状态流接收结果。 */
    fun updateAppUi(page: String) {
        _uiState.update { it.copy(route = AppRoute.fromPage(page)) }
    }

    fun updateSelectedTab(index: Int) {
        if (index !in 0..3) return
        _uiState.update { it.copy(selectedTab = index) }
    }

    /** 页面唯一状态源；Activity 的兼容访问器直接转发到这里。 */
    var page: String
        get() = _uiState.value.page
        set(value) { _uiState.update { it.copy(route = AppRoute.fromPage(value)) } }
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
