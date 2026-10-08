package com.luckyalanzhou.barcodegenerator.data.network.protocol

/** Allow brief sender or storage stalls during large uploads without keeping silent sessions forever. */
internal const val LAN_SHARE_SOCKET_READ_TIMEOUT_MS = 120_000

/** WebSocket Ping/Pong keeps idle browser sessions alive despite the longer socket read timeout. */
internal const val LAN_SHARE_WEBSOCKET_HEARTBEAT_INTERVAL_MS = 10_000L
internal const val LAN_SHARE_WEBSOCKET_HEARTBEAT_TIMEOUT_MS = 35_000L
