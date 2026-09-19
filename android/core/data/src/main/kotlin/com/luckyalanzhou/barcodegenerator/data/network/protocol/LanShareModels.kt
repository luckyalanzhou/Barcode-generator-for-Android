package com.luckyalanzhou.barcodegenerator.data.network.protocol

/** 局域网分享的容量上限集中管理，避免客户端与服务端出现不一致。 */
internal object LanShareLimits {
    const val MAX_FILE_BYTES = 5L * 1024L * 1024L * 1024L
    const val MAX_ROOM_BYTES = 100L * 1024L * 1024L * 1024L
}
