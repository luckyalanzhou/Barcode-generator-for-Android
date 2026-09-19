package com.luckyalanzhou.barcodegenerator.data.network.protocol

import com.luckyalanzhou.barcodegenerator.*

/** 单次会话的统一传输记录；App 与浏览器均以该记录生成消息气泡。 */
data class LanShareFile(val id: String, val name: String, val size: Long, val modifiedAt: Long, val sender: String = "peer")

data class LanShareSession(val baseUrl: String)

/** 局域网分享的容量上限集中管理，避免客户端与服务端出现不一致。 */
internal object LanShareLimits {
    const val MAX_FILE_BYTES = 5L * 1024L * 1024L * 1024L
    const val MAX_ROOM_BYTES = 100L * 1024L * 1024L * 1024L
}
