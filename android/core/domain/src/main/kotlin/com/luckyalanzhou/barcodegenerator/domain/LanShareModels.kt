package com.luckyalanzhou.barcodegenerator.domain

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
    val token: String = "",
) {
    /** Address intended for QR/manual browser entry; the token is never logged in baseUrl. */
    val shareUrl: String
        get() = if (token.isBlank()) baseUrl else "$baseUrl/?token=$token"
}
