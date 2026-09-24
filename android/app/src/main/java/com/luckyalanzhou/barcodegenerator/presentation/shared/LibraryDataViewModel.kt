package com.luckyalanzhou.barcodegenerator.presentation.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Owns application-wide startup loading and recovery from failed library writes. */
@HiltViewModel
class LibraryDataViewModel @Inject constructor(
    private val coordinator: LibraryDataCoordinator,
    persistence: BarcodePersistenceCoordinator,
    private val logger: AppLogger,
) : ViewModel() {
    private var startupFallbackNoticeConsumed = false

    private val _persistenceFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val persistenceFailures: SharedFlow<Unit> = _persistenceFailures.asSharedFlow()

    init {
        viewModelScope.launch {
            persistence.writeFailures.collect { error ->
                logger.record("persistence", "write failed", error)
                _persistenceFailures.emit(Unit)
                // Restore the durable shared snapshot after any feature's optimistic write fails.
                runCatching { coordinator.loadPersistedData() }
                    .onFailure { reloadError ->
                        logger.record("persistence", "reload after write failure failed", reloadError)
                    }
            }
        }
    }

    suspend fun loadPersistedData() = coordinator.loadPersistedData()

    /** Coalesces settings and library load failures into one startup notice. */
    fun consumeStartupFallbackNotice(): Boolean {
        if (startupFallbackNoticeConsumed) return false
        startupFallbackNoticeConsumed = true
        return true
    }
}
