package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 防止延迟到达的房间刷新结果写入新房间，或发布到已关闭的页面。 */
internal class LanShareRefreshGuard(private val state: MutableStateFlow<LanShareUiState>) {
    private val generation = AtomicLong()

    fun currentGeneration(): Long = generation.get()

    fun invalidate(): Long = generation.incrementAndGet()

    fun isCurrent(session: LanShareSession, ticket: Long): Boolean =
        generation.get() == ticket && state.value.session == session

    fun update(session: LanShareSession, ticket: Long, transform: (LanShareUiState) -> LanShareUiState) {
        state.update { current ->
            if (generation.get() == ticket && current.session == session) transform(current) else current
        }
    }
}
