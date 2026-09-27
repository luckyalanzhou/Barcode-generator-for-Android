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

/** Ephemeral text chat entry shared by the App host and connected browser clients. */
data class LanShareMessage(
    val id: String,
    val text: String,
    val sender: String,
    val createdAt: Long,
)

/** Live, in-process events emitted by the active LAN sharing server. */
sealed interface LanShareRealtimeEvent {
    data class ConnectionChanged(val connected: Boolean) : LanShareRealtimeEvent
    data class MessageAdded(val message: LanShareMessage) : LanShareRealtimeEvent
    data class FileAdded(val file: LanShareFile) : LanShareRealtimeEvent
}

/** Latest host-side LAN-share state; StateFlow conflation avoids an unbounded event backlog. */
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
    /** QR and copied address for direct access from the local network. */
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

/** Bound generated preview payloads; source photos are decoded and resized on the host. */
const val LAN_SHARE_PREVIEW_MAX_FILE_BYTES = 64L * 1024L * 1024L
const val LAN_SHARE_PREVIEW_CACHE_MAX_BYTES = 256L * 1024L * 1024L

/** File types that can be rendered as LAN Share previews. */
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

/** Platform-neutral upload input; the app layer supplies the stream from a Uri. */
data class LanShareUploadSource(
    val name: String,
    val size: Long,
    val mimeType: String? = null,
    val openStream: () -> InputStream?,
)

/** LAN Share boundary used by the app layer; the HTTP implementation lives in :core:lan-share. */
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
    fun upload(session: LanShareSession, source: LanShareUploadSource): String
    fun downloadToFile(session: LanShareSession, id: String, destination: File)
    fun downloadPreview(
        session: LanShareSession,
        id: String,
        destination: File,
        maxBytes: Long = LAN_SHARE_PREVIEW_MAX_FILE_BYTES,
    )
}
