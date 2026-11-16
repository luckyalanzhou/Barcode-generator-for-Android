package com.luckyalanzhou.barcodegenerator.data.network.web

import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebText.*

/** 浏览器传输页的页面结构模板。 */
internal object LanShareWebBody {
    fun render(locale: LanShareWebLocale): String {
        fun text(key: LanShareWebText): String = escapeLanShareHtml(key.resolve(locale))

        return """
<body>
    <section id="pairing-gate" class="pairing-gate" hidden>
        <form id="pairing-form" novalidate>
            <h1>${text(PAIRING_TITLE)}</h1>
            <p>${text(PAIRING_INSTRUCTIONS)}</p>
            <label for="pairing-code">${text(PAIRING_CODE)}</label>
            <input id="pairing-code" inputmode="numeric" autocomplete="one-time-code" maxlength="8" pattern="[0-9]{8}" required>
            <button type="submit">${text(PAIR_AND_CONNECT)}</button>
            <p id="pairing-error" role="status"></p>
        </form>
    </section>
    <header class="bar">
        <span>${text(TITLE)}</span>
        <span id="connection-status" class="connection-status">${text(CONNECTING)}</span>
    </header>
    <main><ul id="files"></ul></main>
    <div class="bottom">
        <form id="upload-form">
            <button type="button" class="file-picker" id="attachment-button" aria-label="${text(ADD_ATTACHMENT)}">+</button>
            <input id="camera-capture" type="file" accept="image/*" capture="environment" hidden>
            <input id="gallery" type="file" accept="image/*" hidden>
            <input id="file-picker" type="file" hidden>
            <input id="message" class="message-input" placeholder="${text(MESSAGE_PLACEHOLDER)}" autocomplete="off">
            <button type="submit" class="send-button" aria-label="${text(SEND_OR_UPLOAD)}">${text(SEND)}</button>
        </form>
    </div>
    <div id="attachment-sheet" class="attachment-sheet">
        <button data-picker="camera-capture">${text(TAKE_PHOTO)}</button>
        <button data-picker="gallery">${text(PHOTO_LIBRARY)}</button>
        <button data-picker="file-picker">${text(CHOOSE_FILE)}</button>
        <button class="sheet-close">${text(CANCEL)}</button>
    </div>
    <div id="image-viewer" class="image-viewer" role="dialog" aria-modal="true" aria-label="${text(IMAGE_PREVIEW)}" hidden>
        <div class="image-viewer-toolbar">
            <span id="image-viewer-title"></span>
            <button id="image-viewer-close" type="button" aria-label="${text(CLOSE_IMAGE_PREVIEW)}">×</button>
        </div>
        <div id="image-viewer-stage" class="image-viewer-stage"><img id="image-viewer-image" alt=""></div>
        <div id="image-viewer-hint" class="image-viewer-hint">${text(PREVIEW_CONTROLS)}</div>
    </div>
"""
    }
}
