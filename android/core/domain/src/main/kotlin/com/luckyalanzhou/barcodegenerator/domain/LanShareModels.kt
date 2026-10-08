package com.luckyalanzhou.barcodegenerator.domain

import java.io.File
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.flow.StateFlow

const val LAN_SHARE_MAX_SESSION_MESSAGES = 100

/** LAN Share 展示和会话模型，供 ViewModel 与 Compose 使用，不暴露网络 DTO 包。 */
data class LanShareFile(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val sender: String = "peer",
    val mimeType: String? = null,
)

/** App 主机与已连接浏览器共享的会话内文字消息，不作为永久聊天记录保存。 */
data class LanShareMessage(
    val id: String,
    val text: String,
    val sender: String,
    val createdAt: Long,
)

/** 局域网服务运行期间在进程内发布的连接、消息和文件事件。 */
sealed interface LanShareRealtimeEvent {
    data class ConnectionChanged(val connected: Boolean) : LanShareRealtimeEvent
    data class MessageAdded(val message: LanShareMessage) : LanShareRealtimeEvent
    data class FileAdded(val file: LanShareFile) : LanShareRealtimeEvent
}

/** 主机端最新局域网分享状态；StateFlow 只保留最新快照，避免事件积压无限增长。 */
data class LanShareRealtimeState(
    val browserConnected: Boolean = false,
    val files: List<LanShareFile> = emptyList(),
    val messages: List<LanShareMessage> = emptyList(),
) {
    fun applying(event: LanShareRealtimeEvent): LanShareRealtimeState = when (event) {
        is LanShareRealtimeEvent.ConnectionChanged -> copy(browserConnected = event.connected)
        is LanShareRealtimeEvent.MessageAdded -> copy(
            messages = (messages + event.message)
                .distinctBy(LanShareMessage::id)
                .sortedBy(LanShareMessage::createdAt)
                .takeLast(LAN_SHARE_MAX_SESSION_MESSAGES),
        )
        is LanShareRealtimeEvent.FileAdded -> copy(
            files = (files.filterNot { it.id == event.file.id } + event.file)
                .sortedBy(LanShareFile::modifiedAt),
        )
    }
}

data class LanShareSession(
    val baseUrl: String,
) {
    /** 用于二维码展示和复制的局域网直连地址。 */
    val shareUrl: String get() = baseUrl

    companion object {
        fun fromShareUrl(value: String): LanShareSession? = runCatching {
            val uri = URI(value.trim())
            val host = uri.host ?: return@runCatching null
            val isIpv4 = host.split('.').let { parts ->
                parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
            }
            if (!uri.scheme.equals("http", ignoreCase = true) || !isIpv4 ||
                uri.port !in 1..65535 || uri.userInfo != null || uri.fragment != null ||
                uri.rawPath !in listOf("", "/") || uri.rawQuery != null
            ) return@runCatching null
            LanShareSession("http://$host:${uri.port}")
        }.getOrNull()
    }
}

/** 预览缓存上限；原始照片由主机端解码并生成尺寸受控的预览图。 */
const val LAN_SHARE_PREVIEW_MAX_FILE_BYTES = 64L * 1024L * 1024L
const val LAN_SHARE_PREVIEW_CACHE_MAX_BYTES = 256L * 1024L * 1024L

/** 根据文件名扩展名判断浏览器端可请求预览的图片类型。 */
fun isLanShareImageName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in
        setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff")

fun isLanShareImage(name: String, mimeType: String? = null): Boolean =
    mimeType?.substringBefore(';')?.trim()?.startsWith("image/", ignoreCase = true) == true ||
        isLanShareImageName(name)

fun isLanShareTiffName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("tif", "tiff")

fun isLanShareTiff(name: String, mimeType: String? = null): Boolean =
    isLanShareTiffName(name) || mimeType?.substringBefore(';')?.trim()?.equals("image/tiff", ignoreCase = true) == true

/** 与 Android Uri 解耦的上传输入；App 层负责按需打开对应的字节流。 */
data class LanShareUploadSource(
    val name: String,
    val size: Long,
    val mimeType: String? = null,
    val openStream: () -> InputStream?,
)

/** App 层使用的局域网分享端口；HTTP/WebSocket 具体实现位于 `:core:lan-share`。 */
interface LanShareGateway {
    fun isOnLocalNetwork(): Boolean
    fun isRouterLanHost(host: String?): Boolean
    fun localFile(id: String): File?
    fun start(): LanShareSession
    fun stop(clearSharedFiles: Boolean = false)
    fun browserConnected(): Boolean
    fun localFiles(): List<LanShareFile>
    fun localMessages(): List<LanShareMessage>
    fun sendLocalMessage(text: String): LanShareMessage
    fun observeRealtimeState(): StateFlow<LanShareRealtimeState>
    fun list(session: LanShareSession): List<LanShareFile>
    fun upload(
        session: LanShareSession,
        transferId: String,
        source: LanShareUploadSource,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): String
    fun cancelUpload(transferId: String) {}
    fun downloadToFile(session: LanShareSession, id: String, destination: File)
    fun downloadPreview(
        session: LanShareSession,
        id: String,
        destination: File,
        maxBytes: Long = LAN_SHARE_PREVIEW_MAX_FILE_BYTES,
    )
}
