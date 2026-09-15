package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.net.Inet4Address

/** 局域网分享会话管理器：负责网络地址、端口和服务生命周期。 */
class LanShareManager(private val context: Context) {
    companion object {
        // 保留原有公开入口，避免其他模块直接读取容量上限时产生兼容性变化。
        const val MAX_FILE_BYTES = LanShareLimits.MAX_FILE_BYTES
        const val MAX_ROOM_BYTES = LanShareLimits.MAX_ROOM_BYTES

        internal fun areOnSameRouterSubnet(local: Inet4Address, remote: Inet4Address, prefixLength: Int): Boolean {
            if (prefixLength !in 0..32) return false
            val localValue = local.address.fold(0L) { value, byte -> (value shl 8) or (byte.toInt() and 0xff).toLong() }
            val remoteValue = remote.address.fold(0L) { value, byte -> (value shl 8) or (byte.toInt() and 0xff).toLong() }
            val mask = if (prefixLength == 0) 0L else (0xffff_ffffL shl (32 - prefixLength)) and 0xffff_ffffL
            return (localValue and mask) == (remoteValue and mask)
        }
    }

    private val folder = File(context.filesDir, "lan-share").apply { mkdirs() }
    private var server: LanShareServer? = null
    private var lastPort: Int? = null
    private val client by lazy { LanShareClient(context, this::isRouterLanHost) }

    /** 分享服务只使用 Wi-Fi 默认网关所在子网的 IPv4 地址。 */
    fun isOnLocalNetwork(): Boolean = routerIpv4Addresses().isNotEmpty()

    /** 扫码地址必须和当前路由器网关的 IPv4 子网一致，不能硬编码某个地址段。 */
    fun isRouterLanHost(host: String?): Boolean = runCatching {
        val remote = java.net.InetAddress.getByName(host) as? Inet4Address ?: return@runCatching false
        routerIpv4Addresses().any { local -> areOnSameRouterSubnet(local.address as Inet4Address, remote, local.prefixLength) }
    }.getOrDefault(false)

    private fun routerIpv4Addresses(): List<android.net.LinkAddress> = run {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // 仅枚举系统标记为 Wi-Fi 的网络，并且必须同时找到该 Wi-Fi 的 IPv4 默认网关。
        // activeNetwork 仅用于给真实 Wi-Fi 排序；VPN/容器网络不会通过下面的 Wi-Fi 过滤。
        val activeNetwork = connectivity.activeNetwork
        connectivity.allNetworks.asSequence()
            .sortedByDescending { it == activeNetwork }
            .filter { network ->
                connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            .flatMap { network ->
                val properties = connectivity.getLinkProperties(network) ?: return@flatMap emptySequence()
                val gateway = properties.routes.firstOrNull { route ->
                    route.isDefaultRoute && route.gateway is Inet4Address
                }?.gateway as? Inet4Address ?: return@flatMap emptySequence()
                properties.linkAddresses.asSequence().filter { address ->
                    val local = address.address as? Inet4Address ?: return@filter false
                    !local.isLoopbackAddress && !local.isAnyLocalAddress && !local.isMulticastAddress &&
                        areOnSameRouterSubnet(local, gateway, address.prefixLength)
                }
            }
            .toList()
    }

    fun start(): LanShareSession = start(clearSharedFiles = true)

    private fun start(clearSharedFiles: Boolean): LanShareSession {
        check(isOnLocalNetwork()) { "Error 当前不处于局域网" }
        stop()
        if (clearSharedFiles) clearFiles()
        val address = routerIpv4Addresses()
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull()
            ?.hostAddress ?: error("未连接到局域网")
        val ports = (18080..28080).filter { it != lastPort }.shuffled() + listOfNotNull(lastPort)
        val running = ports.firstNotNullOfOrNull { port ->
            runCatching { LanShareServer(port, folder).also { it.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false) } }.getOrNull()
        } ?: error("无法启动局域网分享服务")
        server = running
        lastPort = running.listeningPort
        val session = LanShareSession("http://$address:${running.listeningPort}")
        DebugLog.record("lan", "server started address=${session.baseUrl}")
        return session
    }

    fun restart(): LanShareSession = start(clearSharedFiles = false)

    fun browserConnected() = server?.browserConnected() == true

    fun stop(clearSharedFiles: Boolean = false) {
        server?.stop()
        server = null
        if (clearSharedFiles) clearFiles()
    }

    fun localFiles() = listFiles(folder, "app")
    fun localFile(id: String): File? = sharedFile(folder, id)
    private fun clearFiles() { folder.listFiles().orEmpty().forEach { it.delete() } }

    fun list(session: LanShareSession) = client.list(session)
    fun upload(session: LanShareSession, uri: android.net.Uri): String = client.upload(session, uri)
    fun uploadText(session: LanShareSession, text: String): String = client.uploadText(session, text)
    fun download(session: LanShareSession, id: String, destination: android.net.Uri) = client.download(session, id, destination)
    fun downloadPreview(session: LanShareSession, id: String, destination: File) = client.downloadPreview(session, id, destination)
}
