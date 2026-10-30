package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession

/** 由功能 UI 持有的上传进度数据；传输过程仍由 Presentation 层执行。 */
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

/** 仅用于渲染的 LAN 分享状态；不包含 Android Uri/File 传输句柄。 */
internal data class LanShareContentState(
    val session: LanShareSession? = null,
    val isHost: Boolean = false,
    val isStartingHost: Boolean = false,
    val qrVisible: Boolean = false,
    val browserConnected: Boolean = false,
    val files: List<LanShareFile> = emptyList(),
    val uploadingFiles: List<LanShareUploadingContent> = emptyList(),
    val isSendingText: Boolean = false,
    val downloadingFileIds: Set<String> = emptySet(),
    val messages: List<LanShareMessage> = emptyList(),
    val ownFileIds: Set<String> = emptySet(),
    val previewFileIds: Set<String> = emptySet(),
    val pendingUploadName: String? = null,
)
