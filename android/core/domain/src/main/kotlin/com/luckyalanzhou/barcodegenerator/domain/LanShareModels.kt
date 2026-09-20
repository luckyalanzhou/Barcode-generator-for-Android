package com.luckyalanzhou.barcodegenerator.domain

import java.io.File
import java.io.InputStream

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
    /** Address intended for QR/manual browser entry. LAN Share does not use token authentication. */
    val shareUrl: String get() = baseUrl
}

/** File types that can be rendered as LAN Share previews. */
fun isLanShareImageName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in
        setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif")

/** Platform-neutral upload input; the app layer supplies the stream from a Uri. */
data class LanShareUploadSource(
    val name: String,
    val size: Long,
    val openStream: () -> InputStream?,
)

/** LAN Share boundary used by the app layer; the HTTP implementation stays in Data. */
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
    fun downloadPreview(session: LanShareSession, id: String, destination: File)
}
