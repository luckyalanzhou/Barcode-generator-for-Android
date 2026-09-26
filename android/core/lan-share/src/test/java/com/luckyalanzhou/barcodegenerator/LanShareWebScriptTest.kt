package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebScript
import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareWebScriptTest {
    @Test
    fun browserClientIdentitySurvivesServerPortChanges() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("function readClientIdCookie()"))
        assertTrue(script.contains("readClientIdCookie() || localStorage.getItem(clientIdKey)"))
        assertTrue(script.contains("Max-Age=31536000; SameSite=Lax"))
    }

    @Test
    fun browserConnectionIndicatorUsesSerializedPresenceWithFailureGrace() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("const HEARTBEAT_TIMEOUT_MS = 5000;"))
        assertTrue(script.contains("const CONNECTION_FAILURE_GRACE_MS = 10000;"))
        assertTrue(script.contains("if (heartbeatInFlight) return;"))
        assertTrue(script.contains("signal: controller.signal"))
        assertTrue(script.contains("Date.now() - lastConnectionSuccessAt >= CONNECTION_FAILURE_GRACE_MS"))

        val refresh = script.substringAfter("async function refreshFiles()").substringBefore("async function uploadFile(file)")
        assertTrue(!refresh.contains("setConnectionState(false)"))

        val socketHandlers = script.substringAfter("let socket;").substringBefore("heartbeat();")
        assertTrue(!socketHandlers.contains("setConnectionState(false)"))
        assertTrue(!socketHandlers.contains("setConnectionState(true)"))
    }

    @Test
    fun browserPageTreatsLegacyBrowserUploadsAsOwnMessages() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("file.sender === 'browser:' + clientId || file.sender === 'browser'"))
        assertEquals(
            2,
            Regex("item\\.className = isOwnFile\\(file\\) \\? 'mine' : 'peer';")
                .findAll(script)
                .count()
        )
    }

    @Test
    fun browserPageAssignsPaletteColorsOnlyToMultipleRemoteBrowserSenders() {
        val script = LanShareWebScript.render()
        val page = LanShareWebTemplates.page()

        assertTrue(script.contains("function isBrowserSender(sender)"))
        assertTrue(script.contains("const usePeerColors = peerSenders.length > 1;"))
        assertTrue(script.contains("item.dataset.peerColor = String(peerColorIndices.get(file.sender) || 0)"))
        assertTrue(page.contains("li.peer[data-peer-color=\"0\"]"))
        assertTrue(page.contains("li.peer[data-peer-color=\"7\"]"))
        assertTrue(page.contains("@media(prefers-color-scheme:dark){li.peer[data-peer-color=\"0\"]"))
    }

    @Test
    fun browserUploadRemembersItsServerFileIdAndSendsTheOriginalFile() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("const ownFileIdsKey = 'lanShareOwnFileIds:' + clientId"))
        assertTrue(script.contains("function rememberOwnFile(id)"))
        assertTrue(script.contains("ownFileIds.has(file.id)"))
        assertTrue(script.contains("body: file"))
        assertTrue(script.contains("'x-file-size': String(file.size)"))
        assertTrue(script.contains("rememberOwnFile((await response.text()).trim())"))
    }

    @Test
    fun browserImagesOpenWheelZoomPreviewWithoutChangingNameDownload() {
        val page = LanShareWebTemplates.page()

        assertTrue(page.contains("id=\"image-viewer\""))
        assertTrue(!page.contains("image-viewer-reset"))
        assertTrue(page.contains("aria-modal=\"true\""))
        assertTrue(page.contains("event.target.closest('img.media-preview')"))
        assertTrue(page.contains("preview.setAttribute('role', 'button')"))
        assertTrue(page.contains("event.deltaY < 0 ? 1.15 : 1 / 1.15"))
        assertTrue(page.contains("Math.min(5, previewScale"))
        assertTrue(page.contains("function clampPreviewOffsets(baseWidth, baseHeight)"))
        assertTrue(page.contains("name.download = file.name || '附件'"))
    }

    @Test
    fun browserRecognizesCommonImageTypesAndHidesUnsupportedBrokenPreview() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("bmp|heic|heif|avif|tif|tiff"))
        assertTrue(script.contains("preview.addEventListener('error'"))
        assertTrue(script.contains("item.classList.remove('image-item')"))
    }

    @Test
    fun browserUsesBoundedServerPreviewsAndKeepsOriginalForDownloadAndFullScreen() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("return '/api/preview/' + encodeURIComponent(file.id)"))
        assertTrue(!script.contains("authorizedUrl"))
        assertTrue(!script.contains("accessToken"))
        assertTrue(!script.contains("X-Lan-Token"))
        assertTrue(!script.contains("token="))
        assertTrue(script.contains("preview.dataset.fullSrc = url"))
        assertTrue(script.contains("imageViewerImage.src = image.dataset.fullSrc || image.currentSrc || image.src"))
        assertTrue(script.contains("preview.src = previewUrl(file)"))
        assertTrue(script.contains("name.href = url"))
    }

    @Test
    fun browserTransferListScrollsBetweenFixedPageChromeAndUsesCompactImagePreviews() {
        val page = LanShareWebTemplates.page()

        assertTrue(page.contains("height:100dvh;overflow:hidden"))
        assertTrue(page.contains("overflow-y:auto;overscroll-behavior-y:contain"))
        assertTrue(page.contains(".bar{position:relative;z-index:4;flex:0 0 var(--web-header-height)}"))
        assertTrue(page.contains(".bottom{position:relative;inset:auto;flex:0 0 auto}"))
        assertTrue(page.contains(".media-preview{max-height:220px}"))
        assertTrue(page.contains(".media-preview{max-height:320px}"))
    }
}
