package com.luckyalanzhou.barcodegenerator.presentation.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 管理应用启动时的收藏库加载，以及写入失败后的恢复。 */
@HiltViewModel
class LibraryDataViewModel @Inject constructor(
    private val coordinator: LibraryDataCoordinator,
    persistence: BarcodePersistenceCoordinator,
    private val logger: AppLogger,
) : ViewModel() {
    private val _persistenceFailures = Channel<Unit>(Channel.BUFFERED)
    val persistenceFailures: Flow<Unit> = _persistenceFailures.receiveAsFlow()

    init {
        viewModelScope.launch {
            persistence.writeFailures.collect { error ->
                logger.recordSafely("persistence", "write failed", error, error)
                _persistenceFailures.send(Unit)
                // 任一功能的乐观写入失败后，都从持久层恢复共享快照。
                runLibraryDataRecovery(
                    load = {
                        // 恢复读取与正在提交的跨页面变更串行，避免旧快照覆盖新内存状态。
                        persistence.withLibraryMutation { coordinator.loadPersistedData() }
                    },
                    onFailure = { reloadError ->
                        logger.recordSafely(
                            "persistence",
                            "reload after write failure failed",
                            reloadError,
                            reloadError,
                        )
                    },
                )
            }
        }
    }

    suspend fun loadPersistedData() = coordinator.loadPersistedData()
}

/** 只把实际恢复异常记录为失败；ViewModel 销毁等生命周期取消必须继续向上传播。 */
internal suspend fun runLibraryDataRecovery(
    load: suspend () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    try {
        load()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(error)
    }
}
