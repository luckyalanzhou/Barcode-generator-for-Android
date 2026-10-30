package com.luckyalanzhou.barcodegenerator.data.platform

import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.CancellationException

/** 更新包仅从受信任的 GitHub Release HTTPS 主机下载，并显式校验每次重定向。 */
internal object HttpsUpdateRedirectPolicy {
    const val MAX_REDIRECTS = 5

    private val allowedHosts = setOf(
        "github.com",
        "release-assets.githubusercontent.com",
    )

    fun isRedirect(statusCode: Int): Boolean =
        statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308

    fun requireAllowedGitHubReleaseUrl(url: URL) {
        requireAllowedGitHubReleaseUri(toUri(url))
    }

    fun resolve(currentUrl: URL, location: String?, redirectsFollowed: Int): URL {
        val currentUri = toUri(currentUrl)
        requireAllowedGitHubReleaseUri(currentUri)
        require(redirectsFollowed in 0 until MAX_REDIRECTS) { "更新服务器重定向次数过多" }
        require(!location.isNullOrBlank()) { "更新服务器返回了无效重定向" }

        val target = try {
            currentUri.resolve(location.trim())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw IllegalArgumentException("更新服务器返回了无效重定向", error)
        }

        requireAllowedGitHubReleaseUri(target)

        return try {
            target.toURL()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw IllegalArgumentException("更新服务器返回了无效重定向", error)
        }
    }

    private fun requireAllowedGitHubReleaseUri(uri: URI) {
        val hostIsAllowed = uri.host
            ?.lowercase(Locale.ROOT)
            ?.let(allowedHosts::contains) == true
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                hostIsAllowed &&
                (uri.port == -1 || uri.port == 443) &&
                uri.rawUserInfo == null &&
                uri.rawFragment == null,
        ) { "更新下载地址必须指向受信任的 GitHub Release HTTPS 主机" }
    }

    private fun toUri(url: URL): URI = try {
        URI(url.toExternalForm())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw IllegalArgumentException("更新服务器返回了无效重定向", error)
    }
}
