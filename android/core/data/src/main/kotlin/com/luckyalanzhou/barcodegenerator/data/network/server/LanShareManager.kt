package com.luckyalanzhou.barcodegenerator.data.network.server

import com.luckyalanzhou.barcodegenerator.data.network.client.LanShareClient
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.protocol.listFiles
import com.luckyalanzhou.barcodegenerator.data.network.protocol.sharedFile

import com.luckyalanzhou.barcodegenerator.domain.AppLogger


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.net.Inet4Address

/** 局域网分享会话管理器：负责网络地址、端口和服务生命周期。 */
class LanShareManager(
    private val context: Context,
    private val logger: AppLogger,
) : LanShareGateway {
    companion object {
        // 保留原有公开入口，避免其他模块直接读取容量上限时产生兼容性变化。
        const val MAX_FILE_BYTES = LanShareLimits.MAX_FILE_BYTES
        const val MAX_ROOM_BYTES = LanShareLimits.MAX_ROOM_BYTES

        fun areOnSameRouterSubnet(local: Inet4Address, remote: Inet4Address, prefixLength: Int): Boolean {
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
    private val client by lazy { LanShareClient(this::isRouterLanHost, logger) }

    /** 分享服务只使用 Wi-Fi 默认网关所在子网的 IPv4 地址。 */
    override fun isOnLocalNetwork(): Boolean = routerIpv4Addresses().isNotEmpty()

    /** 扫码地址必须和当前路由器网关的 IPv4 子网一致，不能硬编码某个地址段。 */
    override fun isRouterLanHost(host: String?): Boolean = runCatching {
        val remote = java.net.InetAddress.getByName(host) as? Inet4Address ?: return@runCatching false
        routerIpv4Addresses().any { local -> areOnSameRouterSubnet(local.address as Inet4Address, remote, local.prefixLength) }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun routerIpv4Addresses(): List<android.net.LinkAddress> = run {
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

    override fun start(): LanShareSession = start(clearSharedFiles = true)

    private fun start(clearSharedFiles: Boolean): LanShareSession {
        val addresses = routerIpv4Addresses()
        check(addresses.isNotEmpty()) { "Error 当前不处于局域网" }
        stop()
        if (clearSharedFiles) clearFiles()
        val address = addresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull()
            ?.hostAddress ?: error("未连接到局域网")
        val ports = (18080..28080).filter { it != lastPort }.shuffled() + listOfNotNull(lastPort)
        val running = ports.firstNotNullOfOrNull { port ->
            runCatching {
                LanShareServer(address, port, folder, logger).also {
                    it.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                }
            }.getOrNull()
        } ?: error("无法启动局域网分享服务")
        server = running
        lastPort = running.listeningPort
        val session = LanShareSession("http://$address:${running.listeningPort}")
        logger.record("lan", "server started address=${session.baseUrl}", null)
        return session
    }

    override fun restart(): LanShareSession = start(clearSharedFiles = false)

    override fun browserConnected() = server?.browserConnected() == true

    override fun stop(clearSharedFiles: Boolean) {
        server?.stop()
        server = null
        if (clearSharedFiles) clearFiles()
    }

    override fun localFiles() = listFiles(folder, "app")
    override fun localFile(id: String): File? = sharedFile(folder, id)
    private fun clearFiles() { folder.listFiles().orEmpty().forEach { it.delete() } }

    override fun list(session: LanShareSession) = client.list(session)
    override fun upload(session: LanShareSession, source: LanShareUploadSource): String = client.upload(session, source)
    override fun uploadText(session: LanShareSession, text: String): String = client.uploadText(session, text)
    override fun downloadToFile(session: LanShareSession, id: String, destination: File) = client.downloadToFile(session, id, destination)
    override fun downloadPreview(session: LanShareSession, id: String, destination: File) = client.downloadPreview(session, id, destination)
}
