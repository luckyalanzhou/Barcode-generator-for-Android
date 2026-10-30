package com.luckyalanzhou.barcodegenerator.data.network.server

import com.luckyalanzhou.barcodegenerator.data.network.recordSafely
import com.luckyalanzhou.barcodegenerator.data.network.client.LanShareClient
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeEvent
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeState
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import com.luckyalanzhou.barcodegenerator.domain.LanShareDownloadCancellation
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_SOCKET_READ_TIMEOUT_MS
import com.luckyalanzhou.barcodegenerator.data.network.protocol.listFiles
import com.luckyalanzhou.barcodegenerator.data.network.protocol.sharedFile

import com.luckyalanzhou.barcodegenerator.domain.AppLogger


import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 局域网分享会话管理器：负责网络地址、端口和服务生命周期。 */
private class LanShareManagerOverrides(
    val isRouterLanHost: ((String?) -> Boolean)? = null,
    val localIpv4Address: (() -> Inet4Address?)? = null,
    val deleteSharedFile: ((File) -> Boolean)? = null,
)

class LanShareManager private constructor(
    private val context: Context,
    private val logger: AppLogger,
    private val testOverrides: LanShareManagerOverrides,
) : LanShareGateway {
    constructor(context: Context, logger: AppLogger) : this(context, logger, LanShareManagerOverrides())

    /** 测试专用构造器用于通过 App 侧公开网关验证真实上传与取消调用链。 */
    internal constructor(
        context: Context,
        logger: AppLogger,
        isRouterLanHost: (String?) -> Boolean,
    ) : this(context, logger, LanShareManagerOverrides(isRouterLanHost = isRouterLanHost))

    /** 测试专用构造器用于通过公开网关验证新房间清理失败时的关闭策略。 */
    internal constructor(
        context: Context,
        logger: AppLogger,
        isRouterLanHost: (String?) -> Boolean,
        localIpv4Address: () -> Inet4Address?,
        deleteSharedFile: (File) -> Boolean,
    ) : this(
        context,
        logger,
        LanShareManagerOverrides(isRouterLanHost, localIpv4Address, deleteSharedFile),
    )

    companion object {
        // 保留原有公开入口，避免其他模块直接读取容量上限时产生兼容性变化。
        const val MAX_FILE_BYTES = LanShareLimits.MAX_FILE_BYTES
        const val MAX_ROOM_BYTES = LanShareLimits.MAX_ROOM_BYTES
        const val MAX_ROOM_FILES = LanShareLimits.MAX_ROOM_FILES
        /** 服务端预览缓存所在的 cacheDir 子目录名，供启动清理复用。 */
        const val WEB_PREVIEW_CACHE_DIRECTORY_NAME = "lan-share-web-preview"

        fun areOnSameRouterSubnet(local: Inet4Address, remote: Inet4Address, prefixLength: Int): Boolean {
            if (prefixLength !in 0..32) return false
            val localValue = local.address.fold(0L) { value, byte -> (value shl 8) or (byte.toInt() and 0xff).toLong() }
            val remoteValue = remote.address.fold(0L) { value, byte -> (value shl 8) or (byte.toInt() and 0xff).toLong() }
            val mask = if (prefixLength == 0) 0L else (0xffff_ffffL shl (32 - prefixLength)) and 0xffff_ffffL
            return (localValue and mask) == (remoteValue and mask)
        }
    }

    private val folder = File(context.filesDir, "lan-share").apply { mkdirs() }
    private val webPreviewFolder = File(context.cacheDir, WEB_PREVIEW_CACHE_DIRECTORY_NAME)
    private var server: LanShareServer? = null
    private var lastPort: Int? = null
    private val serverGeneration = AtomicLong(0L)
    private val realtimeStateLock = Any()
    private val realtimeState = MutableStateFlow(LanShareRealtimeState())
    private val client by lazy { LanShareClient(this::isRouterLanHost, logger) }

    /** 分享服务只使用 Wi-Fi 默认网关所在子网的 IPv4 地址。 */
    override fun isOnLocalNetwork(): Boolean = routerIpv4Addresses().isNotEmpty()

    /** 扫码地址必须和当前路由器网关的 IPv4 子网一致，不能硬编码某个地址段。 */
    override fun isRouterLanHost(host: String?): Boolean = testOverrides.isRouterLanHost?.invoke(host) ?: runCatching {
        val remote = java.net.InetAddress.getByName(host) as? Inet4Address ?: return@runCatching false
        routerIpv4Addresses().any { local -> areOnSameRouterSubnet(local.address as Inet4Address, remote, local.prefixLength) }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun routerIpv4Addresses(): List<LinkAddress> = run {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // 只接受 Wi-Fi 传输，并且必须找到该 Wi-Fi 网络的 IPv4 默认网关。
        // activeNetwork 可能暂时指向 VPN 或其他网络，因此优先检查它，再检查系统当前的 Wi-Fi 网络集合；
        // 仍然不会接受移动数据、以太网、VPN 或没有 Wi-Fi 网关的地址。
        val candidates = buildList {
            connectivity.activeNetwork?.let(::add)
            connectivity.allNetworks.forEach { if (!contains(it)) add(it) }
        }
        candidates.asSequence().mapNotNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI).not() ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) return@mapNotNull null
            val properties = connectivity.getLinkProperties(network) ?: return@mapNotNull null
            val gateway = properties.routes.firstOrNull { route ->
                route.isDefaultRoute && route.gateway is Inet4Address
            }?.gateway as? Inet4Address ?: return@mapNotNull null
            val addresses = properties.linkAddresses.filter { address ->
                val local = address.address as? Inet4Address ?: return@filter false
                !local.isLoopbackAddress && !local.isAnyLocalAddress && !local.isMulticastAddress &&
                    areOnSameRouterSubnet(local, gateway, address.prefixLength)
            }
            addresses.takeIf { it.isNotEmpty() }
        }.firstOrNull().orEmpty()
    }

    /** 开始新的分享会话：停止旧服务、按需清理会话文件，并在当前 Wi-Fi 子网随机端口启动服务。 */
    override fun start(): LanShareSession = start(clearSharedFiles = true)

    private fun start(clearSharedFiles: Boolean): LanShareSession {
        val address = testOverrides.localIpv4Address?.invoke()?.hostAddress
            ?: routerIpv4Addresses().firstOrNull()?.address?.hostAddress
        check(address != null) { "Error 当前不处于局域网" }
        stop()
        val generation = serverGeneration.incrementAndGet()
        if (clearSharedFiles && !clearFiles()) {
            val failure = IllegalStateException("无法清理上次局域网分享文件，请重试")
            logger.recordSafely("lan", "previous session files could not be cleared", primaryError = failure)
            throw failure
        }
        val port = (18080..28080).filter { it != lastPort }.random()
        val running = LanShareServer(
            address,
            port,
            folder,
            logger,
            webPreviewFolder,
            emitRealtimeEvent = { event ->
                synchronized(realtimeStateLock) {
                    if (serverGeneration.get() == generation) {
                        realtimeState.update { it.applying(event) }
                    }
                }
            },
        ).also {
            try {
                it.start(LAN_SHARE_SOCKET_READ_TIMEOUT_MS, false)
            } catch (error: Exception) {
                runCatching { it.stop() }
                throw IllegalStateException("无法在随机端口 $port 启动局域网分享服务", error)
            }
        }
        server = running
        lastPort = running.listeningPort
        val session = LanShareSession(
            baseUrl = "http://$address:${running.listeningPort}",
            accessToken = running.sessionToken,
            pairingCode = running.pairingCode,
        )
        logger.recordSafely("lan", "server started address=${session.baseUrl}")
        return session
    }

    override fun browserConnected() = server?.browserConnected() == true
    override fun localMessages() = server?.messagesSnapshot().orEmpty()
    override fun sendLocalMessage(text: String): LanShareMessage =
        checkNotNull(server) { "只有 App 创建的分享房间可以发送文字消息" }.sendLocalMessage(text)

    override fun observeRealtimeState(): StateFlow<LanShareRealtimeState> = realtimeState.asStateFlow()

    /** 关闭 HTTP/WebSocket 服务并清空实时状态；是否删除会话文件由调用方明确指定。 */
    override fun stop(clearSharedFiles: Boolean) {
        synchronized(realtimeStateLock) {
            serverGeneration.incrementAndGet()
            realtimeState.value = LanShareRealtimeState()
        }
        server?.stop()
        server = null
        if (clearSharedFiles && !clearFiles()) {
            logger.recordSafely("lan", "share files could not be cleared on close")
        }
    }

    override fun localFiles() = server?.filesSnapshot("app") ?: listFiles(folder, "app")
    override fun localFile(id: String): File? = sharedFile(folder, id)
    private fun clearFiles(): Boolean {
        val sharedFilesCleared = try {
            if (!folder.isDirectory) {
                false
            } else {
                val files = folder.listFiles()
                files != null && files.all { file ->
                    val deleted = testOverrides.deleteSharedFile?.invoke(file) ?: file.delete()
                    (deleted || !file.exists())
                } && folder.listFiles()?.isEmpty() == true
            }
        } catch (_: Exception) {
            false
        }
        val previewCacheCleared = try {
            !webPreviewFolder.exists() ||
                (webPreviewFolder.deleteRecursively() && !webPreviewFolder.exists())
        } catch (_: Exception) {
            false
        }
        return sharedFilesCleared && previewCacheCleared
    }

    override fun list(session: LanShareSession) = client.list(session)
    /** 将 App 选中的文件通过 HTTP 上传到浏览器创建的分享房间，并持续回报字节进度。 */
    override fun upload(
        session: LanShareSession,
        transferId: String,
        source: LanShareUploadSource,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit,
    ): String = client.upload(session, transferId, source, onProgress)
    override fun cancelUpload(transferId: String) = client.cancelUpload(transferId)
    override fun downloadToFile(session: LanShareSession, id: String, destination: File) =
        client.downloadToFile(session, id, destination)
    override fun downloadToFile(
        session: LanShareSession,
        id: String,
        destination: File,
        cancellation: LanShareDownloadCancellation,
    ) = client.downloadToFile(session, id, destination, cancellation)
    override fun downloadPreview(session: LanShareSession, id: String, destination: File, maxBytes: Long) =
        client.downloadPreview(session, id, destination, maxBytes)
}
