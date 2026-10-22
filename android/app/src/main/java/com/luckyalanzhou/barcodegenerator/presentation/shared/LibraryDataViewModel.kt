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

/** Owns application-wide startup loading and recovery from failed library writes. */
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
                logger.record("persistence", "write failed", error)
                _persistenceFailures.send(Unit)
                // Restore the durable shared snapshot after any feature's optimistic write fails.
                runLibraryDataRecovery(
                    load = { coordinator.loadPersistedData() },
                    onFailure = { reloadError ->
                        logger.record("persistence", "reload after write failure failed", reloadError)
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
