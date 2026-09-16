package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LanShareUiState(
    val session: LanShareSession? = null,
    val isHost: Boolean = false,
    val qrVisible: Boolean = false,
    val browserConnected: Boolean = false,
    val files: List<LanShareFile> = emptyList(),
    val ownFileIds: Set<String> = emptySet(),
    val previewFiles: Map<String, java.io.File> = emptyMap(),
)

/** 局域网分享展示状态；网络服务生命周期仍由 Activity 桥接层管理。 */
@HiltViewModel
class LanShareViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(LanShareUiState())
    val uiState: StateFlow<LanShareUiState> = _uiState.asStateFlow()

    fun sync(
        session: LanShareSession?,
        isHost: Boolean,
        qrVisible: Boolean,
        browserConnected: Boolean,
        files: List<LanShareFile>,
        ownFileIds: Set<String>,
        previewFiles: Map<String, java.io.File>,
    ) {
        _uiState.value = LanShareUiState(session, isHost, qrVisible, browserConnected, files, ownFileIds, previewFiles)
    }
}
