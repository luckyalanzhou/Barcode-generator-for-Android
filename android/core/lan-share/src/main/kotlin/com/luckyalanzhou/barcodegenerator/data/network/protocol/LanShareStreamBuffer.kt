package com.luckyalanzhou.barcodegenerator.data.network.protocol

/** Per-transfer buffer large enough to avoid tiny writes without excessive memory use. */
internal const val LAN_SHARE_STREAM_BUFFER_SIZE = 128 * 1024
