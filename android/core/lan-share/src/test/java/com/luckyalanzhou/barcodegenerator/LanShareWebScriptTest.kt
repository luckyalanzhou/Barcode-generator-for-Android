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
}
