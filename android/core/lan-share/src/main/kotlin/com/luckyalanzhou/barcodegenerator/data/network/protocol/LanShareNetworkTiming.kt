package com.luckyalanzhou.barcodegenerator.data.network.protocol

/** 大文件上传期间允许发送端或存储短暂停顿，同时避免无响应会话无限占用资源。 */
internal const val LAN_SHARE_SOCKET_READ_TIMEOUT_MS = 120_000

/** 允许约 8.4 Mbps 的持续速率，低于项目观测到的正常局域网上传速度。 */
internal const val LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND = 1024L * 1024L
/** 为慢速客户端连接并开始发送预留启动时间。 */
internal const val LAN_SHARE_UPLOAD_STARTUP_GRACE_MS = 30_000L
/** 增加 120 秒总宽限，以容纳传输期间的短暂停顿。 */
internal const val LAN_SHARE_UPLOAD_PAUSE_GRACE_MS = 120_000L

/** 即使 Socket 读取超时较长，WebSocket Ping/Pong 也能维持空闲浏览器会话。 */
internal const val LAN_SHARE_WEBSOCKET_HEARTBEAT_INTERVAL_MS = 10_000L
internal const val LAN_SHARE_WEBSOCKET_HEARTBEAT_TIMEOUT_MS = 35_000L
