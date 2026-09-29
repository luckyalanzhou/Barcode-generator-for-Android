package com.luckyalanzhou.barcodegenerator.data.network.web

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits

/** 浏览器传输页的交互脚本模板。 */
internal object LanShareWebScript {
    fun render() = """<script>
const MAX_UPLOAD_BYTES = ${LanShareLimits.MAX_FILE_BYTES};
const fileList = document.getElementById('files');
const uploadForm = document.getElementById('upload-form');
const messageInput = document.getElementById('message');
const connectionStatus = document.getElementById('connection-status');
const attachmentSheet = document.getElementById('attachment-sheet');
const attachmentButton = document.getElementById('attachment-button');
const imageViewer = document.getElementById('image-viewer');
const imageViewerStage = document.getElementById('image-viewer-stage');
const imageViewerImage = document.getElementById('image-viewer-image');
const imageViewerTitle = document.getElementById('image-viewer-title');
const imageViewerClose = document.getElementById('image-viewer-close');
let fileRecords = [];
let chatRecords = [];
const uploadRecords = new Map();
const isIOSDevice = /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const clientIdKey = 'lanShareClientId';

function readClientIdCookie() {
    const cookiePrefix = encodeURIComponent(clientIdKey) + '=';
    const cookie = document.cookie.split(';').map(part => part.trim()).find(part => part.startsWith(cookiePrefix));
    if (!cookie) return '';
    try {
        return decodeURIComponent(cookie.slice(cookiePrefix.length));
    } catch (_) {
        return '';
    }
}

let clientId = readClientIdCookie() || localStorage.getItem(clientIdKey);
if (!/^c[a-zA-Z0-9_-]{8,63}$/.test(clientId || '')) {
    clientId = 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
}
localStorage.setItem(clientIdKey, clientId);
// Cookies are shared across ports for the same host, unlike localStorage. Keep sender identity
// stable when the app restarts its LAN server on a different port and retains previous files.
document.cookie = encodeURIComponent(clientIdKey) + '=' + encodeURIComponent(clientId) + '; Path=/; Max-Age=31536000; SameSite=Lax';

const ownFileIdsKey = 'lanShareOwnFileIds:' + clientId;
const ownFileIds = new Set();
try {
    const storedOwnFileIds = JSON.parse(localStorage.getItem(ownFileIdsKey) || '[]');
    if (Array.isArray(storedOwnFileIds)) {
        storedOwnFileIds.filter(id => typeof id === 'string').slice(-256).forEach(id => ownFileIds.add(id));
    }
} catch (_) {}

function rememberOwnFile(id) {
    if (!id) return;
    ownFileIds.add(id);
    while (ownFileIds.size > 256) ownFileIds.delete(ownFileIds.values().next().value);
    try {
        localStorage.setItem(ownFileIdsKey, JSON.stringify(Array.from(ownFileIds)));
    } catch (_) {}
}

function isImageName(name) {
    return /\.(jpg|jpeg|png|gif|webp|bmp|heic|heif|avif|tif|tiff)$/i.test(name || '');
}

function isImageFile(file) {
    const mimeType = typeof file.mimeType === 'string'
        ? file.mimeType.split(';', 1)[0].trim().toLowerCase()
        : '';
    return mimeType ? mimeType.startsWith('image/') : isImageName(file.name);
}

function isOwnFile(file) {
    // Older browser uploads have no client ID, so the server can only label them "browser".
    return ownFileIds.has(file.id) || file.sender === 'browser:' + clientId || file.sender === 'browser';
}

function isBrowserSender(sender) {
    return sender === 'browser' || (typeof sender === 'string' && sender.startsWith('browser:'));
}

function formatSize(bytes) {
    const value = Number(bytes) || 0;
    if (value >= 1024 * 1024 * 1024) return (value / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
    if (value >= 1024 * 1024) return (value / (1024 * 1024)).toFixed(1) + ' MB';
    if (value >= 1024) return Math.floor(value / 1024) + ' KB';
    return value + ' B';
}

function setConnectionState(connected) {
    connectionStatus.textContent = connected ? '● 已连接到设备' : '○ 正在连接设备...';
    connectionStatus.classList.toggle('connected', connected);
}

function fileUrl(file) {
    return '/dl/' + encodeURIComponent(file.id) + '?v=' + encodeURIComponent(file.modifiedAt || '');
}

function previewUrl(file) {
    return '/api/preview/' + encodeURIComponent(file.id) + '?v=' + encodeURIComponent(file.modifiedAt || '');
}

function createFileItem(file) {
    const item = document.createElement('li');
    const url = fileUrl(file);
    item.dataset.fileId = file.id;
    item.className = isOwnFile(file) ? 'mine' : 'peer';

    if (isImageFile(file)) {
        const preview = document.createElement('img');
        preview.className = 'media-preview';
        preview.alt = '';
        preview.loading = 'lazy';
        preview.decoding = 'async';
        preview.tabIndex = 0;
        preview.setAttribute('role', 'button');
        preview.setAttribute('aria-label', '预览图片：' + (file.name || '图片'));
        preview.dataset.fullSrc = url;
        preview.addEventListener('error', () => {
            preview.remove();
            item.classList.remove('image-item');
        }, { once: true });
        preview.addEventListener('keydown', event => {
            if (event.key !== 'Enter' && event.key !== ' ') return;
            event.preventDefault();
            preview.click();
        });
        item.classList.add('image-item');
        item.appendChild(preview);
        preview.src = previewUrl(file);
    }

    const imageFile = isImageFile(file);
    const name = document.createElement(imageFile ? 'a' : 'span');
    name.className = 'file-name';
    name.textContent = file.name || '未命名';
    if (imageFile) {
        name.href = url;
        name.download = file.name || '附件';
    }
    item.appendChild(name);

    const size = document.createElement('small');
    size.textContent = formatSize(file.size);
    item.appendChild(size);

    if (!imageFile) {
        const download = document.createElement('a');
        download.href = url;
        download.download = file.name || '附件';
        download.className = 'download';
        download.setAttribute('aria-label', '下载 ' + (file.name || '附件'));
        download.textContent = '下载';
        item.appendChild(download);
    }
    return item;
}

let previewScale = 1;
let previewOffsetX = 0;
let previewOffsetY = 0;
let previewOrigin = null;
let previewDrag = null;

function applyPreviewTransform() {
    imageViewerImage.style.transform = 'translate(' + previewOffsetX + 'px, ' + previewOffsetY + 'px) scale(' + previewScale + ')';
    imageViewerImage.classList.toggle('zoomed', previewScale > 1);
    imageViewerImage.classList.remove('dragging');
}

function resetPreviewTransform() {
    previewScale = 1;
    previewOffsetX = 0;
    previewOffsetY = 0;
    previewDrag = null;
    applyPreviewTransform();
}

function clampPreviewOffsets(baseWidth, baseHeight) {
    const maxX = Math.max(0, (baseWidth * previewScale - imageViewerStage.clientWidth) / 2);
    const maxY = Math.max(0, (baseHeight * previewScale - imageViewerStage.clientHeight) / 2);
    previewOffsetX = Math.max(-maxX, Math.min(maxX, previewOffsetX));
    previewOffsetY = Math.max(-maxY, Math.min(maxY, previewOffsetY));
}

function closeImageViewer() {
    imageViewer.hidden = true;
    document.body.classList.remove('preview-open');
    if (previewOrigin && previewOrigin.isConnected) previewOrigin.focus();
    previewOrigin = null;
    previewDrag = null;
}

fileList.addEventListener('click', event => {
    const image = event.target.closest('img.media-preview');
    if (!image) return;
    event.preventDefault();
    previewOrigin = image;
    imageViewerImage.src = image.dataset.fullSrc || image.currentSrc || image.src;
    imageViewerImage.alt = image.alt || '图片预览';
    imageViewerTitle.textContent = image.alt || '图片预览';
    resetPreviewTransform();
    imageViewer.hidden = false;
    document.body.classList.add('preview-open');
    imageViewerClose.focus();
});

imageViewerClose.addEventListener('click', closeImageViewer);
imageViewer.addEventListener('click', event => {
    if (event.target === imageViewer || event.target === imageViewerStage) closeImageViewer();
});
imageViewer.addEventListener('wheel', event => {
    if (imageViewer.hidden) return;
    event.preventDefault();
    const nextScale = Math.max(1, Math.min(5, previewScale * (event.deltaY < 0 ? 1.15 : 1 / 1.15)));
    if (nextScale === previewScale) return;
    const rect = imageViewerImage.getBoundingClientRect();
    const factor = nextScale / previewScale;
    const baseWidth = rect.width / previewScale;
    const baseHeight = rect.height / previewScale;
    previewOffsetX += (event.clientX - (rect.left + rect.width / 2)) * (1 - factor);
    previewOffsetY += (event.clientY - (rect.top + rect.height / 2)) * (1 - factor);
    previewScale = nextScale;
    if (previewScale === 1) {
        previewOffsetX = 0;
        previewOffsetY = 0;
    } else {
        clampPreviewOffsets(baseWidth, baseHeight);
    }
    applyPreviewTransform();
}, { passive: false });

imageViewerImage.addEventListener('pointerdown', event => {
    if (previewScale <= 1 || (event.pointerType === 'mouse' && event.button !== 0)) return;
    previewDrag = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, offsetX: previewOffsetX, offsetY: previewOffsetY };
    imageViewerImage.setPointerCapture(event.pointerId);
    imageViewerImage.classList.add('dragging');
    event.preventDefault();
});
imageViewerImage.addEventListener('pointermove', event => {
    if (!previewDrag || previewDrag.pointerId !== event.pointerId) return;
    const rect = imageViewerImage.getBoundingClientRect();
    previewOffsetX = previewDrag.offsetX + event.clientX - previewDrag.x;
    previewOffsetY = previewDrag.offsetY + event.clientY - previewDrag.y;
    clampPreviewOffsets(rect.width / previewScale, rect.height / previewScale);
    imageViewerImage.style.transform = 'translate(' + previewOffsetX + 'px, ' + previewOffsetY + 'px) scale(' + previewScale + ')';
});
function finishPreviewDrag(event) {
    if (!previewDrag || previewDrag.pointerId !== event.pointerId) return;
    previewDrag = null;
    imageViewerImage.classList.remove('dragging');
}
imageViewerImage.addEventListener('pointerup', finishPreviewDrag);
imageViewerImage.addEventListener('pointercancel', finishPreviewDrag);

document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !imageViewer.hidden) {
        event.preventDefault();
        closeImageViewer();
    }
});

/** Keep WebSocket file and chat snapshots in one chronological conversation. */
function reconcileFiles(list) {
    fileRecords = (Array.isArray(list) ? list : []).filter(file => file && file.id);
    renderTimeline();
}

function upsertFile(file, transferId) {
    if (!file || !file.id) return;
    if (transferId && uploadRecords.has(transferId)) {
        uploadRecords.delete(transferId);
        rememberOwnFile(file.id);
    }
    fileRecords = fileRecords.filter(existing => existing.id !== file.id);
    fileRecords.push(file);
    renderTimeline();
}

function reconcileMessages(list) {
    chatRecords = (Array.isArray(list) ? list : []).filter(message =>
        message && message.id && typeof message.text === 'string'
    );
    renderTimeline();
}

function appendChatMessage(message) {
    if (!message || !message.id || typeof message.text !== 'string') return;
    chatRecords = chatRecords.filter(existing => existing.id !== message.id);
    chatRecords.push(message);
    if (chatRecords.length > 100) chatRecords.splice(0, chatRecords.length - 100);
    renderTimeline();
}

function isOwnMessage(message) {
    return message.sender === 'browser:' + clientId;
}

function createMessageItem(message) {
    const item = document.createElement('li');
    item.dataset.messageId = message.id;
    item.className = isOwnMessage(message) ? 'mine' : 'peer';
    item.classList.add('text-message');
    item.textContent = message.text;
    return item;
}

function createUploadItem(upload) {
    const item = document.createElement('li');
    item.dataset.uploadId = upload.id;
    item.className = 'mine upload-progress';
    item.setAttribute('role', 'progressbar');
    item.setAttribute('aria-valuemin', '0');
    item.setAttribute('aria-valuemax', '100');
    const fill = document.createElement('span');
    fill.className = 'upload-fill';
    fill.setAttribute('aria-hidden', 'true');
    const name = document.createElement('span');
    name.className = 'upload-name';
    const size = document.createElement('small');
    size.className = 'upload-size';
    const percent = document.createElement('span');
    percent.className = 'upload-percent';
    item.append(fill, name, size, percent);
    updateUploadItem(item, upload);
    return item;
}

function updateUploadItem(item, upload) {
    const total = Math.max(0, Number(upload.total) || 0);
    const loaded = Math.max(0, Math.min(total, Number(upload.loaded) || 0));
    const percent = total > 0 ? Math.floor(loaded * 100 / total) : 0;
    item.style.setProperty('--upload-progress', percent + '%');
    item.setAttribute('aria-valuenow', String(percent));
    item.setAttribute('aria-label', '正在上传 ' + upload.name + '，' + percent + '%');
    item.querySelector('.upload-name').textContent = upload.name || '附件';
    item.querySelector('.upload-size').textContent = formatSize(loaded) + ' / ' + formatSize(total);
    item.querySelector('.upload-percent').textContent = percent + '%';
}

function renderTimeline() {
    const normalized = fileRecords;
    const messages = chatRecords;
    const peerSenders = Array.from(new Set([
        ...normalized.filter(file => !isOwnFile(file) && isBrowserSender(file.sender)).map(file => file.sender),
        ...messages.filter(message => !isOwnMessage(message) && isBrowserSender(message.sender)).map(message => message.sender)
    ])).sort();
    const peerColorIndices = new Map(peerSenders.map((sender, index) => [sender, index % 8]));
    const usePeerColors = peerSenders.length > 1;
    const timeline = [
        ...normalized.map(file => ({ kind: 'file', item: file, timestamp: Number(file.modifiedAt) || 0, key: 'file:' + file.id })),
        ...messages.map(message => ({ kind: 'message', item: message, timestamp: Number(message.createdAt) || 0, key: 'message:' + message.id })),
        ...Array.from(uploadRecords.values()).map(upload => ({ kind: 'upload', item: upload, timestamp: upload.startedAt, key: 'upload:' + upload.id }))
    ].sort((left, right) => left.timestamp - right.timestamp || left.key.localeCompare(right.key));
    const current = new Map(Array.from(fileList.children).map(item => {
        const key = item.dataset.fileId ? 'file:' + item.dataset.fileId :
            item.dataset.messageId ? 'message:' + item.dataset.messageId : 'upload:' + item.dataset.uploadId;
        return [key, item];
    }));
    const activeKeys = new Set(timeline.map(entry => entry.key));

    current.forEach((item, id) => {
        if (!activeKeys.has(id)) item.remove();
    });

    timeline.forEach(entry => {
        if (entry.kind === 'upload') {
            const oldItem = current.get(entry.key);
            const item = oldItem || createUploadItem(entry.item);
            if (oldItem) updateUploadItem(item, entry.item);
            fileList.appendChild(item);
            return;
        }
        if (entry.kind === 'message') {
            const message = entry.item;
            const oldItem = current.get(entry.key);
            const item = oldItem || createMessageItem(message);
            if (oldItem) {
                item.className = isOwnMessage(message) ? 'mine' : 'peer';
                item.classList.add('text-message');
                item.textContent = message.text;
            }
            delete item.dataset.peerColor;
            if (!isOwnMessage(message) && usePeerColors && isBrowserSender(message.sender)) {
                item.dataset.peerColor = String(peerColorIndices.get(message.sender) || 0);
            }
            fileList.appendChild(item);
            return;
        }

        const file = entry.item;
        const oldItem = current.get(entry.key);
        const item = oldItem || createFileItem(file);
        if (oldItem) {
            item.className = isOwnFile(file) ? 'mine' : 'peer';
            if (isImageFile(file)) item.classList.add('image-item');
            const name = item.querySelector('.file-name');
            const download = item.querySelector('.download');
            const size = item.querySelector('small');
            const url = fileUrl(file);
            if (name) {
                name.textContent = file.name || '未命名';
                if (name.tagName === 'A') {
                    name.href = url;
                    name.download = file.name || '附件';
                }
            }
            if (download) {
                download.href = url;
                download.download = file.name || '附件';
                download.setAttribute('aria-label', '下载 ' + (file.name || '附件'));
            }
            if (size) size.textContent = formatSize(file.size);
            const preview = item.querySelector('img');
            const imageUrl = previewUrl(file);
            if (preview) preview.dataset.fullSrc = url;
            if (preview && preview.src !== new URL(imageUrl, location.href).href) preview.src = imageUrl;
        }
        setPeerBubbleColor(item, file, peerColorIndices, usePeerColors);
        fileList.appendChild(item);
    });
}

function setPeerBubbleColor(item, file, peerColorIndices, usePeerColors) {
    delete item.dataset.peerColor;
    if (!isOwnFile(file) && usePeerColors && isBrowserSender(file.sender)) {
        item.dataset.peerColor = String(peerColorIndices.get(file.sender) || 0);
    }
}

async function uploadFile(file) {
    if (!file) return;
    if (file.size > MAX_UPLOAD_BYTES) {
        alert('单个文件不能超过 10 GiB');
        return;
    }
    const transferId = 'u' + Date.now().toString(36) + Math.random().toString(36).slice(2, 12);
    const upload = { id: transferId, name: file.name || '附件', total: file.size, loaded: 0, startedAt: Date.now() };
    uploadRecords.set(transferId, upload);
    renderTimeline();
    try {
        const storedId = await new Promise((resolve, reject) => {
            const request = new XMLHttpRequest();
            const url = '/upload?name=' + encodeURIComponent(file.name || '附件') +
                '&client=' + encodeURIComponent(clientId) + '&transfer=' + encodeURIComponent(transferId);
            request.open('PUT', url, true);
            request.setRequestHeader('content-type', file.type || 'application/octet-stream');
            // Safari/部分移动浏览器使用 chunked PUT，无法由网页设置 Content-Length。
            request.setRequestHeader('x-file-size', String(file.size));
            request.upload.addEventListener('progress', event => {
                if (!uploadRecords.has(transferId)) return;
                const total = event.lengthComputable ? event.total : file.size;
                const progress = { ...upload, loaded: event.loaded, total };
                uploadRecords.set(transferId, progress);
                const item = fileList.querySelector('[data-upload-id="' + transferId + '"]');
                if (item) updateUploadItem(item, progress);
            });
            request.addEventListener('load', () => {
                if (request.status >= 200 && request.status < 300) resolve(request.responseText.trim());
                else reject(new Error(request.responseText || ('HTTP ' + request.status)));
            });
            request.addEventListener('error', () => reject(new Error('网络连接失败')));
            request.addEventListener('abort', () => reject(new Error('上传已取消')));
            request.send(file);
        });
        // The server returns the stored file ID. Keep that authoritative identity locally so
        // this browser's new upload stays on the right even if sender metadata is stale/missing.
        if (storedId) {
            rememberOwnFile(storedId);
            upsertFile({
                id: storedId,
                name: file.name || '附件',
                size: file.size,
                modifiedAt: Date.now(),
                sender: 'browser:' + clientId,
                mimeType: file.type || null
            }, transferId);
        } else {
            uploadRecords.delete(transferId);
            renderTimeline();
        }
        if (messageInput) messageInput.value = '';
    } catch (error) {
        uploadRecords.delete(transferId);
        renderTimeline();
        alert('发送失败：' + (error.message || '请刷新页面后重试'));
    }
}

uploadForm.onsubmit = event => {
    event.preventDefault();
    const text = messageInput.value;
    if (!text.trim()) {
        messageInput.focus();
        return;
    }
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        alert('设备连接尚未就绪，请稍后重试');
        return;
    }
    if (new TextEncoder().encode(text).length > 64 * 1024) {
        alert('单条文字消息不能超过 64 KB');
        return;
    }
    socket.send(JSON.stringify({ type: 'message', text }));
    messageInput.value = '';
};

attachmentButton.onclick = event => {
    event.stopPropagation();
    // iOS Safari already presents Photos, Camera, Files, and Cancel for a file input.
    // Open it directly to avoid stacking the web sheet over Safari's native chooser.
    if (isIOSDevice) {
        document.getElementById('file-picker').click();
        return;
    }
    attachmentSheet.classList.add('open');
    const buttonRect = attachmentButton.getBoundingClientRect();
    attachmentSheet.style.left = (buttonRect.left + 8) + 'px';
    attachmentSheet.style.top = (buttonRect.top - attachmentSheet.offsetHeight - 16) + 'px';
};

['camera-capture', 'gallery', 'file-picker'].forEach(id => {
    const picker = document.getElementById(id);
    if (!picker) return;
    picker.onchange = event => {
        const file = event.target.files && event.target.files[0];
        if (file) {
            attachmentSheet.classList.remove('open');
            uploadFile(file);
        }
        event.target.value = '';
    };
});

attachmentSheet.onclick = event => {
    if (event.target.closest('.sheet-close')) {
        attachmentSheet.classList.remove('open');
        return;
    }
    const pickerId = event.target.closest('[data-picker]')?.dataset.picker;
    if (pickerId) {
        const picker = document.getElementById(pickerId);
        if (picker) {
            // 先关闭菜单；用户取消系统选择器时也不会留下一个悬空菜单。
            attachmentSheet.classList.remove('open');
            picker.click();
        }
    }
};

document.addEventListener('click', event => {
    if (attachmentSheet.classList.contains('open') && !attachmentSheet.contains(event.target) && event.target !== attachmentButton) {
        attachmentSheet.classList.remove('open');
    }
});

let socket;
function connectSocket() {
    try {
        socket = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws?client=' + encodeURIComponent(clientId));
        socket.onopen = () => {
            setConnectionState(true);
            socket.send('sync');
        };
        socket.onmessage = event => {
            try {
                const payload = JSON.parse(event.data);
                if (payload.type === 'snapshot') {
                    reconcileFiles(payload.files);
                    reconcileMessages(payload.messages);
                } else if (payload.type === 'message') appendChatMessage(payload.message);
                else if (payload.type === 'files' && payload.file) upsertFile(payload.file, payload.transferId);
                else if (payload.type === 'error') alert(payload.message || '消息发送失败');
            } catch (_) {}
        };
        socket.onclose = () => {
            setConnectionState(false);
            setTimeout(connectSocket, 1000);
        };
        socket.onerror = () => setConnectionState(false);
    } catch (_) {
        setTimeout(connectSocket, 1000);
    }
}

connectSocket();
</script>"""
}
