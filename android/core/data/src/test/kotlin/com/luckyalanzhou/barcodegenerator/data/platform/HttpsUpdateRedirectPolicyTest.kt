package com.luckyalanzhou.barcodegenerator.data.platform

import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsUpdateRedirectPolicyTest {
    private val currentUrl = URL("https://github.com/releases/download/v1.0.0/app.apk")

    @Test
    fun resolvesRelativeAndGitHubAssetRedirectsOverHttps() {
        assertEquals(
            URL("https://github.com/releases/download/v1.0.0/app-next.apk"),
            HttpsUpdateRedirectPolicy.resolve(currentUrl, "app-next.apk", redirectsFollowed = 0),
        )
        assertEquals(
            URL("https://release-assets.githubusercontent.com/signed/app.apk"),
            HttpsUpdateRedirectPolicy.resolve(
                currentUrl,
                "https://release-assets.githubusercontent.com/signed/app.apk",
                redirectsFollowed = 0,
            ),
        )
    }

    @Test
    fun acceptsOnlyGitHubReleaseHostsAndDefaultHttpsPorts() {
        HttpsUpdateRedirectPolicy.requireAllowedGitHubReleaseUrl(currentUrl)
        HttpsUpdateRedirectPolicy.requireAllowedGitHubReleaseUrl(
            URL("https://github.com:443/releases/download/v1.0.0/app.apk"),
        )
        HttpsUpdateRedirectPolicy.requireAllowedGitHubReleaseUrl(
            URL("https://release-assets.githubusercontent.com/signed/app.apk"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.requireAllowedGitHubReleaseUrl(URL("https://example.com/app.apk"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.requireAllowedGitHubReleaseUrl(URL("https://github.com:8443/app.apk"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.resolve(currentUrl, "https://github.com.attacker.test/app.apk", redirectsFollowed = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.resolve(
                URL("https://example.com/releases/app.apk"),
                "https://github.com/releases/app.apk",
                redirectsFollowed = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.resolve(
                currentUrl,
                "https://release-assets.githubusercontent.com.attacker.test/app.apk",
                redirectsFollowed = 0,
            )
        }
    }

    @Test
    fun rejectsDowngradeAndInvalidRedirectTargets() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.resolve(currentUrl, "http://example.com/app.apk", redirectsFollowed = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.resolve(currentUrl, null, redirectsFollowed = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.resolve(currentUrl, "https://user@example.com/app.apk", redirectsFollowed = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.resolve(currentUrl, "https://example.com/app.apk#fragment", redirectsFollowed = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsUpdateRedirectPolicy.resolve(
                currentUrl,
                "https://example.com/app.apk",
                redirectsFollowed = HttpsUpdateRedirectPolicy.MAX_REDIRECTS,
            )
        }
    }

    @Test
    fun fatalErrorWhileFormattingCurrentUrlIsNotWrappedAsInvalidRedirect() {
        val fatal = AssertionError("simulated fatal URL formatting failure")
        val brokenUrl = URL(
            "https",
            "github.com",
            -1,
            "/releases/download/v1.0.0/app.apk",
            object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection = error("connection must not be opened")
                override fun toExternalForm(url: URL): String = throw fatal
            },
        )

        assertThrows(AssertionError::class.java) {
            HttpsUpdateRedirectPolicy.resolve(brokenUrl, "app-next.apk", redirectsFollowed = 0)
        }
    }

    @Test
    fun onlyStandardRedirectStatusesAreFollowedAndRedirectCountIsBounded() {
        assertTrue(HttpsUpdateRedirectPolicy.isRedirect(301))
        assertTrue(HttpsUpdateRedirectPolicy.isRedirect(308))
        assertFalse(HttpsUpdateRedirectPolicy.isRedirect(300))
        assertEquals(5, HttpsUpdateRedirectPolicy.MAX_REDIRECTS)
    }
}
