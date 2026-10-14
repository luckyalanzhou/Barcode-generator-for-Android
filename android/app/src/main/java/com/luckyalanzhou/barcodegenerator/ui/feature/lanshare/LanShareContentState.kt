package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession

/** Feature-owned upload progress data; transfer execution remains in presentation. */
internal data class LanShareUploadingContent(
    val id: String,
    val name: String,
    val size: Long,
    val uploadedBytes: Long,
    val startedAt: Long,
) {
    val progressPercent: Int
        get() = if (size <= 0L) 0 else ((uploadedBytes.coerceIn(0L, size) * 100L) / size).toInt()
}

/** Render-only LAN share state. Android Uri/File transfer handles are deliberately excluded. */
internal data class LanShareContentState(
    val session: LanShareSession? = null,
    val isHost: Boolean = false,
    val qrVisible: Boolean = false,
    val browserConnected: Boolean = false,
    val files: List<LanShareFile> = emptyList(),
    val uploadingFiles: List<LanShareUploadingContent> = emptyList(),
    val messages: List<LanShareMessage> = emptyList(),
    val ownFileIds: Set<String> = emptySet(),
    val previewFileIds: Set<String> = emptySet(),
    val pendingUploadName: String? = null,
)
