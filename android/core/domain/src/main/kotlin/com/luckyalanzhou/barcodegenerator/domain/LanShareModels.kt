package com.luckyalanzhou.barcodegenerator.domain

import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.Base64

/** LAN Share 展示和会话模型，供 ViewModel 与 Compose 使用，不暴露网络 DTO 包。 */
data class LanShareFile(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val sender: String = "peer",
)

enum class LanShareSecurityMode {
    /** HTTP address intended for a general-purpose browser on the local network. */
    BROWSER_COMPATIBLE,

    /** HTTPS address whose ephemeral certificate is trusted only by the receiving app. */
    ENCRYPTED_APP,
}

data class LanShareSession(
    val baseUrl: String,
    val accessToken: String,
    /** Host-only short code for manual browser entry; absent on a session joined by QR. */
    val manualCode: String? = null,
    val securityMode: LanShareSecurityMode = LanShareSecurityMode.BROWSER_COMPATIBLE,
    /** SHA-256 certificate fingerprint; only present for an app-to-app encrypted session. */
    val certificateFingerprint: String? = null,
) {
    init {
        require(ACCESS_TOKEN.matches(accessToken)) { "无效的局域网分享访问码" }
        val expectedScheme = if (securityMode == LanShareSecurityMode.ENCRYPTED_APP) "https" else "http"
        require(runCatching { URI(baseUrl).scheme.equals(expectedScheme, ignoreCase = true) }.getOrDefault(false)) {
            "局域网分享地址与安全模式不匹配"
        }
        require(
            if (securityMode == LanShareSecurityMode.ENCRYPTED_APP) {
                certificateFingerprint?.matches(CERTIFICATE_FINGERPRINT) == true && manualCode == null
            } else {
                certificateFingerprint == null
            }
        ) { "无效的局域网分享证书信息" }
        require(manualCode == null || (MANUAL_CODE.matches(manualCode) &&
            manualCode.any(Char::isDigit) && manualCode.any(Char::isLetter))) {
            "无效的四位手动访问码"
        }
    }

    /** QR payload; never log or persist this value. Secure payloads are only understood by this app. */
    val shareUrl: String get() = when (securityMode) {
        LanShareSecurityMode.BROWSER_COMPATIBLE -> "$baseUrl/?token=$accessToken"
        LanShareSecurityMode.ENCRYPTED_APP -> {
            val encodedAddress = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(baseUrl.toByteArray(Charsets.UTF_8))
            "barcodegenerator://lan-share?url=$encodedAddress&token=$accessToken&pin=$certificateFingerprint"
        }
    }

    companion object {
        private val ACCESS_TOKEN = Regex("[A-Za-z0-9_-]{22}")
        private val MANUAL_CODE = Regex("[A-Z0-9]{4}")
        private val CERTIFICATE_FINGERPRINT = Regex("[A-Fa-f0-9]{64}")

        fun fromShareUrl(value: String): LanShareSession? = runCatching {
            val uri = URI(value.trim())
            if (uri.userInfo != null || uri.fragment != null) return@runCatching null
            if (uri.scheme.equals("barcodegenerator", ignoreCase = true)) {
                return@runCatching parseEncryptedAppPayload(uri)
            }
            val host = uri.host ?: return@runCatching null
            val isIpv4 = host.split('.').let { parts ->
                parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
            }
            if (!uri.scheme.equals("http", ignoreCase = true) || !isIpv4 ||
                uri.port !in 1..65535 ||
                uri.rawPath !in listOf("", "/")
            ) return@runCatching null
            val parameters = parseQuery(uri.rawQuery) ?: return@runCatching null
            if (parameters.keys != setOf("token")) return@runCatching null
            val token = parameters["token"]?.takeIf(ACCESS_TOKEN::matches)
                ?: return@runCatching null
            LanShareSession("http://$host:${uri.port}", token)
        }.getOrNull()

        private fun parseEncryptedAppPayload(uri: URI): LanShareSession? {
            if (!uri.host.equals("lan-share", ignoreCase = true) || uri.rawPath !in listOf("", "/") ||
                uri.port != -1
            ) return null
            val parameters = parseQuery(uri.rawQuery) ?: return null
            if (parameters.keys != setOf("url", "token", "pin")) return null
            val address = parameters["url"]?.let { encoded ->
                runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull()
            } ?: return null
            val secureUri = URI(address)
            val host = secureUri.host ?: return null
            val isIpv4 = host.split('.').let { parts ->
                parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
            }
            if (!secureUri.scheme.equals("https", ignoreCase = true) || !isIpv4 ||
                secureUri.port !in 1..65535 || secureUri.rawPath !in listOf("", "/") ||
                secureUri.rawQuery != null || secureUri.rawFragment != null || secureUri.userInfo != null
            ) return null
            val token = parameters["token"]?.takeIf(ACCESS_TOKEN::matches) ?: return null
            val pin = parameters["pin"]?.takeIf(CERTIFICATE_FINGERPRINT::matches) ?: return null
            return LanShareSession(
                baseUrl = "https://$host:${secureUri.port}",
                accessToken = token,
                securityMode = LanShareSecurityMode.ENCRYPTED_APP,
                certificateFingerprint = pin.lowercase(),
            )
        }

        private fun parseQuery(query: String?): Map<String, String>? {
            if (query.isNullOrBlank()) return null
            val entries = query.split('&').map { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return null
                val key = java.net.URLDecoder.decode(part.substring(0, separator), Charsets.UTF_8.name())
                val value = java.net.URLDecoder.decode(part.substring(separator + 1), Charsets.UTF_8.name())
                key to value
            }
            if (entries.map { it.first }.toSet().size != entries.size) return null
            return entries.toMap()
        }
    }
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
