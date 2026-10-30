package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebScript
import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebTemplates
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareWebScriptTest {
    @Test
    fun browserClientIdentitySurvivesServerPortChanges() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("function readClientIdCookie()"))
        assertTrue(script.contains("readClientIdCookie() || readBrowserStorage('localStorage', clientIdKey)"))
        assertTrue(script.contains("Max-Age=31536000; SameSite=Lax"))
    }

    @Test
    fun browserStorageFailuresDoNotAbortPageInitialization() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("function readBrowserStorage(storage, key)"))
        assertTrue(script.contains("function writeBrowserStorage(storage, key, value)"))
        assertTrue(script.contains("readBrowserStorage('sessionStorage', accessTokenKey)"))
        assertTrue(script.contains("readBrowserStorage('localStorage', clientIdKey)"))
        assertTrue(script.contains("writeBrowserStorage('sessionStorage', accessTokenKey, token)"))
        assertTrue(script.contains("try { return window[storage].getItem(key); } catch (_) { return null; }"))
        assertTrue(script.contains("try { window[storage].setItem(key, value); return true; } catch (_) { return false; }"))
        assertTrue(!script.contains("localStorage.getItem("))
        assertTrue(!script.contains("sessionStorage.getItem("))
        assertTrue(!script.contains("localStorage.setItem("))
        assertTrue(!script.contains("sessionStorage.setItem("))
    }

    @Test
    fun browserRequiresPairingBeforeShowingTransferUiAndNeverSendsPairCodeInUrl() {
        val script = LanShareWebScript.render()
        val page = LanShareWebTemplates.page()

        assertTrue(page.contains("id=\"pairing-gate\""))
        assertTrue(page.contains("id=\"pairing-code\""))
        assertTrue(script.contains("function showPairing()"))
        assertTrue(script.contains("fetch('/api/pair', {"))
        assertTrue(script.contains("'X-Lan-Pairing-Code': code"))
        assertTrue(!script.contains("/api/pair?code="))
        assertTrue(script.contains("validateAccessToken(accessToken).catch(showPairing)"))
    }

    @Test
    fun browserConnectionIndicatorUsesWebSocketLifecycleWithoutPolling() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("const CONNECTION_DISCONNECT_GRACE_MS = 3000;"))
        assertTrue(script.contains("function markConnectionRestored()"))
        assertTrue(script.contains("function scheduleConnectionLost()"))
        assertTrue(script.contains("socket.onopen = () => {\n            markConnectionRestored();"))
        assertTrue(script.contains("socket.onclose = () => {\n            scheduleConnectionLost();"))
        assertTrue(!script.contains("socket.onerror = () => setConnectionState(false)"))
        assertTrue(script.contains("if (generation !== connectionStateGeneration) return;"))
        assertTrue(!script.contains("function heartbeat()"))
        assertTrue(!script.contains("/api/presence"))
        assertTrue(!script.contains("setInterval("))
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
        assertTrue(script.contains("request.send(file)"))
        assertTrue(script.contains("request.setRequestHeader('x-file-size', String(file.size))"))
        assertTrue(script.contains("request.upload.addEventListener('progress'"))
        assertTrue(script.contains("'&transfer=' + encodeURIComponent(transferId)"))
        assertTrue(script.contains("rememberOwnFile(storedId)"))
        assertTrue(script.contains("function updateUploadItem(item, upload)"))
    }

    @Test
    fun browserRejectsOnlyFilesAboveTheSharedTenGibibyteLimitBeforeUploading() {
        val script = LanShareWebScript.render()

        assertEquals(10L * 1024L * 1024L * 1024L, LanShareLimits.MAX_FILE_BYTES)
        assertTrue(script.contains("const MAX_UPLOAD_BYTES = ${LanShareLimits.MAX_FILE_BYTES};"))
        assertTrue(script.contains("if (file.size > MAX_UPLOAD_BYTES)"))
        assertTrue(script.contains("alert('单个文件不能超过 10 GiB')"))
    }

    @Test
    fun browserImagesOpenWheelZoomPreviewWithoutChangingNameDownload() {
        val page = LanShareWebTemplates.page()

        assertTrue(page.contains("id=\"image-viewer\""))
        assertTrue(!page.contains("image-viewer-reset"))
        assertTrue(page.contains("aria-modal=\"true\""))
        assertTrue(page.contains("event.target.closest('img.media-preview')"))
        assertTrue(page.contains("border-radius:12px;background:var(--preview-bg)"))
        assertTrue(page.contains("preview.setAttribute('role', 'button')"))
        assertTrue(page.contains("event.deltaY < 0 ? 1.15 : 1 / 1.15"))
        assertTrue(page.contains("Math.min(5, previewScale"))
        assertTrue(page.contains("function clampPreviewOffsets(baseWidth, baseHeight)"))
        assertTrue(page.contains("name.download = file.name || '附件'"))
    }

    @Test
    fun browserImageViewerKeepsKeyboardFocusInsideTheModal() {
        val page = LanShareWebTemplates.page()

        assertTrue(page.contains("id=\"image-viewer\" class=\"image-viewer\" role=\"dialog\" aria-modal=\"true\""))
        assertTrue(page.contains("imageViewer.addEventListener('keydown', event => {"))
        assertTrue(page.contains("if (event.key !== 'Tab' || imageViewer.hidden) return;"))
        assertTrue(page.contains("event.shiftKey"))
        assertTrue(page.contains("imageViewer.contains(document.activeElement)"))
        assertTrue(page.contains("event.preventDefault();"))
        assertTrue(page.contains("imageViewerClose.focus();"))
    }

    @Test
    fun browserRecognizesCommonImageTypesAndHidesUnsupportedBrokenPreview() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("bmp|heic|heif|avif|tif|tiff"))
        assertTrue(script.contains("function isImageFile(file)"))
        assertTrue(script.contains("mimeType ? mimeType.startsWith('image/') : isImageName(file.name)"))
        assertTrue(script.contains("preview.addEventListener('error'"))
        assertTrue(script.contains("item.classList.remove('image-item')"))
    }

    @Test
    fun browserUsesCompatiblePreviewsForFullScreenAndOriginalForDownload() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("return tokenUrl('/api/preview/' + encodeURIComponent(file.id)"))
        assertTrue(script.contains("function tokenUrl(path)"))
        assertTrue(script.contains("writeBrowserStorage('sessionStorage', accessTokenKey, token)"))
        assertTrue(script.contains("Authorization: 'Bearer ' + token"))
        assertTrue(script.contains("'&token=' + encodeURIComponent(accessToken)"))
        assertTrue(script.contains("preview.dataset.fullSrc = previewUrl(file)"))
        assertTrue(script.contains("imageViewerImage.src = image.dataset.fullSrc || image.currentSrc || image.src"))
        assertTrue(script.contains("preview.src = previewUrl(file)"))
        assertTrue(script.contains("name.href = url"))
        assertTrue(script.contains("return tokenUrl('/dl/' + encodeURIComponent(file.id)"))
        assertTrue(!script.contains("return '/api/download/"))
    }

    @Test
    fun browserTextUsesWebSocketWhileAttachmentsRemainHttpUploads() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("socket.send(JSON.stringify({ type: 'message', text }))"))
        assertTrue(script.contains("'/ws?client=' + encodeURIComponent(clientId) + '&token=' + encodeURIComponent(accessToken)"))
        assertTrue(script.contains("reconcileMessages(payload.messages)"))
        assertTrue(script.contains("appendChatMessage(payload.message)"))
        assertTrue(script.contains("request.send(file)"))
        assertTrue(!script.contains("new File([text], '消息.txt'"))
    }

    @Test
    fun browserValidatesSessionWithoutFetchingTheFileManifest() {
        val script = LanShareWebScript.render()

        assertTrue(script.contains("fetch('/api/session', { headers: { Authorization: 'Bearer ' + token }, cache: 'no-store' }"))
        assertTrue(!script.contains("fetch('/api/files'"))
        assertTrue(script.contains("function upsertFile(file, transferId)"))
        assertTrue(script.contains("payload.type === 'files' && payload.file) upsertFile(payload.file, payload.transferId)"))
        assertTrue(script.contains("socket.send('sync')"))
        assertTrue(!script.contains("async function refreshFiles()"))
    }

    @Test
    fun browserUploadBubblesFillFromLeftAsTheRequestProgresses() {
        val page = LanShareWebTemplates.page()

        assertTrue(page.contains("--upload-progress"))
        assertTrue(page.contains("transition:width .12s linear"))
        assertTrue(page.contains("aria-valuenow"))
    }

    @Test
    fun browserCanCancelAnActiveUploadWithoutShowingFailureAlert() {
        val script = LanShareWebScript.render()
        val page = LanShareWebTemplates.page()

        assertTrue(script.contains("const activeUploads = new Map();"))
        assertTrue(script.contains("function cancelUpload(transferId)"))
        assertTrue(script.contains("request.abort();"))
        assertTrue(script.contains("if (!cancelledUploads.delete(transferId))"))
        assertTrue(script.contains("cancel.className = 'upload-cancel';"))
        assertTrue(page.contains(".upload-cancel{position:relative"))
        assertTrue(page.contains("grid-template-areas:\"icon name percent\" \"icon size cancel\""))
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
