package com.luckyalanzhou.barcodegenerator.domain

import java.io.File
import java.io.InputStream
import java.net.URI

/** LAN Share 展示和会话模型，供 ViewModel 与 Compose 使用，不暴露网络 DTO 包。 */
data class LanShareFile(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val sender: String = "peer",
)

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

fun isLanShareTiffName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("tif", "tiff")

/** Platform-neutral upload input; the app layer supplies the stream from a Uri. */
data class LanShareUploadSource(
    val name: String,
    val size: Long,
    val openStream: () -> InputStream?,
)

/** LAN Share boundary used by the app layer; the HTTP implementation lives in :core:lan-share. */
interface LanShareGateway {
    fun isOnLocalNetwork(): Boolean
    fun isRouterLanHost(host: String?): Boolean
    fun localFile(id: String): File?
    fun start(): LanShareSession
    fun restart(): LanShareSession
    fun stop(clearSharedFiles: Boolean = false)
    fun browserConnected(): Boolean
    fun localFiles(): List<LanShareFile>
    fun list(session: LanShareSession): List<LanShareFile>
    fun upload(session: LanShareSession, source: LanShareUploadSource): String
    fun uploadText(session: LanShareSession, text: String): String
    fun downloadToFile(session: LanShareSession, id: String, destination: File)
    fun downloadPreview(
        session: LanShareSession,
        id: String,
        destination: File,
        maxBytes: Long = LAN_SHARE_PREVIEW_MAX_FILE_BYTES,
    )
}
