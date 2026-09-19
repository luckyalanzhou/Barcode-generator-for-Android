package com.luckyalanzhou.barcodegenerator.domain

/** LAN Share 展示和会话模型，供 ViewModel 与 Compose 使用，不暴露网络 DTO 包。 */
data class LanShareFile(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val sender: String = "peer",
)

data class LanShareSession(val baseUrl: String)
