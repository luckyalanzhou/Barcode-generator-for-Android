package com.luckyalanzhou.barcodegenerator.data.platform

import java.net.URL
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
    fun onlyStandardRedirectStatusesAreFollowedAndRedirectCountIsBounded() {
        assertTrue(HttpsUpdateRedirectPolicy.isRedirect(301))
        assertTrue(HttpsUpdateRedirectPolicy.isRedirect(308))
        assertFalse(HttpsUpdateRedirectPolicy.isRedirect(300))
        assertEquals(5, HttpsUpdateRedirectPolicy.MAX_REDIRECTS)
    }
}
