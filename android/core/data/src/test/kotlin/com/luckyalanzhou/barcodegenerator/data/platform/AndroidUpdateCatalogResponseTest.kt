package com.luckyalanzhou.barcodegenerator.data.platform

import com.luckyalanzhou.barcodegenerator.domain.UpdateFailure
import com.luckyalanzhou.barcodegenerator.domain.UpdateLookupResult
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidUpdateCatalogResponseTest {
    @Test
    fun betaUpdateWithoutAnySha256IsRejectedDuringCheck() {
        val result = parseUpdateCatalogResponse(
            releases = releases(
                tag = "android-test-v9.9.9",
                body = "versionCode: 9",
                assetName = "BarcodeGeneratorBeta9.9.9.apk",
            ),
            updateTagPrefix = "android-test-v",
            apkFilePrefix = "BarcodeGeneratorBeta",
            currentVersionName = "9.9.9",
            currentVersionCode = 8,
        )

        assertEquals(
            UpdateFailure.MissingHash,
            (result as UpdateLookupResult.Failed).failure,
        )
    }

    @Test
    fun betaUpdateUsesSha256FromReleaseNotes() {
        val expectedSha = "a".repeat(64)
        val result = parseUpdateCatalogResponse(
            releases = releases(
                tag = "android-test-v9.9.9",
                body = "versionCode: 9\nsha256: $expectedSha",
                assetName = "BarcodeGeneratorBeta9.9.9.apk",
            ),
            updateTagPrefix = "android-test-v",
            apkFilePrefix = "BarcodeGeneratorBeta",
            currentVersionName = "9.9.9",
            currentVersionCode = 8,
        )

        assertTrue(result is UpdateLookupResult.Available)
        assertEquals(expectedSha, (result as UpdateLookupResult.Available).expectedSha256)
        assertEquals("9.9.9#9", result.ignoreKey)
    }

    @Test
    fun officialUpdateFallsBackToGitHubAssetDigest() {
        val expectedSha = "b".repeat(64)
        val result = parseUpdateCatalogResponse(
            releases = releases(
                tag = "android-v1.0.0",
                body = "",
                assetName = "BarcodeGeneratorOfficial1.0.0.apk",
                assetDigest = "sha256:$expectedSha",
            ),
            updateTagPrefix = "android-v",
            apkFilePrefix = "BarcodeGeneratorOfficial",
            currentVersionName = "0.9.9",
            currentVersionCode = 12,
        )

        assertTrue(result is UpdateLookupResult.Available)
        assertEquals(expectedSha, (result as UpdateLookupResult.Available).expectedSha256)
        assertEquals("1.0.0", result.ignoreKey)
    }

    private fun releases(
        tag: String,
        body: String,
        assetName: String,
        assetDigest: String = "",
    ) = JSONArray().put(
        org.json.JSONObject()
            .put("tag_name", tag)
            .put("body", body)
            .put(
                "assets",
                JSONArray().put(
                    org.json.JSONObject()
                        .put("name", assetName)
                        .put("browser_download_url", "https://example.test/$assetName")
                        .put("size", 123L)
                        .put("digest", assetDigest),
                ),
            ),
    )
}
