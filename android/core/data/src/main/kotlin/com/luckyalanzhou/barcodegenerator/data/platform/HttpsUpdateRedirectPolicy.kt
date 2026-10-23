package com.luckyalanzhou.barcodegenerator.data.platform

import java.net.URI
import java.net.URL
import java.util.concurrent.CancellationException

/** 更新包下载只接受安全的 HTTPS 重定向，避免自动跟随导致协议降级。 */
internal object HttpsUpdateRedirectPolicy {
    const val MAX_REDIRECTS = 5

    fun isRedirect(statusCode: Int): Boolean =
        statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308

    fun resolve(currentUrl: URL, location: String?, redirectsFollowed: Int): URL {
        require(currentUrl.protocol.equals("https", ignoreCase = true)) { "更新下载地址必须使用 HTTPS" }
        require(redirectsFollowed in 0 until MAX_REDIRECTS) { "更新服务器重定向次数过多" }
        require(!location.isNullOrBlank()) { "更新服务器返回了无效重定向" }

        val target = try {
            URI(currentUrl.toExternalForm()).resolve(location.trim())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw IllegalArgumentException("更新服务器返回了无效重定向", error)
        }

        require(
            target.scheme.equals("https", ignoreCase = true) &&
                !target.host.isNullOrBlank() &&
                target.rawUserInfo == null &&
                target.rawFragment == null,
        ) { "更新下载重定向必须指向有效的 HTTPS 地址" }

        return try {
            target.toURL()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw IllegalArgumentException("更新服务器返回了无效重定向", error)
        }
    }
}
